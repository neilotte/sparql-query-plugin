package org.protege.editor.owl.rdf.repository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.openrdf.model.Statement;
import org.openrdf.query.BooleanQuery;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.Query;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.query.Update;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.rdfxml.RDFXMLParserFactory;
import org.openrdf.rio.rdfxml.RDFXMLWriterFactory;
import org.openrdf.repository.util.RDFInserter;
import org.openrdf.sail.memory.MemoryStore;
import org.protege.editor.owl.rdf.SparqlReasoner;
import org.protege.editor.owl.rdf.SparqlReasonerException;
import org.protege.editor.owl.rdf.SparqlResultSet;
import org.protege.editor.owl.rdf.SparqlUpdatePreview;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RioTurtleDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.util.NamespaceUtil;

public class BasicSparqlReasoner implements SparqlReasoner {
	private OWLOntologyManager manager;
	private Repository repository;
	private OWLOntology targetOntology;
	private final Deque<List<OWLOntologyChange>> undoStack = new LinkedList<List<OWLOntologyChange>>();
	
	public BasicSparqlReasoner(OWLOntologyManager manager) {
		this.manager = manager;
	}
	
	@Override
	public String getSampleQuery() {
		StringBuffer sb = new StringBuffer();
		NamespaceUtil nsUtil = new NamespaceUtil();
		for (Entry<String, String> entry : nsUtil.getNamespace2PrefixMap().entrySet()) {
			String ns = entry.getKey();
			String prefix = entry.getValue();
			sb.append("PREFIX ");
			sb.append(prefix);
			sb.append(": <");
			sb.append(ns);
			sb.append(">\n");
		}
		sb.append("SELECT ?subject ?object\n\tWHERE { ?subject rdfs:subClassOf ?object }");
		return sb.toString();
	}

	@Override
	public void setTargetOntology(OWLOntology ontology) {
		targetOntology = ontology;
	}
	
	@Override
	public SparqlUpdatePreview previewUpdate(String queryString) throws SparqlReasonerException {
		precalculate();
		try {
			RepositoryConnection sourceConnection = null;
			Repository previewRepository = null;
			RepositoryConnection previewConnection = null;
			try {
				sourceConnection = repository.getConnection();
				Set<Statement> before = getStatements(sourceConnection);
				previewRepository = createRepository(before);
				previewConnection = previewRepository.getConnection();
				Update update = previewConnection.prepareUpdate(QueryLanguage.SPARQL, queryString);
				update.execute();
				return createUpdatePreview(before, getStatements(previewConnection));
			}
			finally {
				if (previewConnection != null) {
					previewConnection.close();
				}
				if (previewRepository != null) {
					previewRepository.shutDown();
				}
				if (sourceConnection != null) {
					sourceConnection.close();
				}
			}
		}
		catch (Exception e) {
			throw new SparqlReasonerException(e);
		}
	}
	
	@Override
	public SparqlResultSet undoLastUpdate() throws SparqlReasonerException {
		if (undoStack.isEmpty()) {
			return createMessageResult("Undo", "There is no SPARQL update to undo.");
		}
		try {
			List<OWLOntologyChange> inverseChanges = undoStack.pop();
			manager.applyChanges(inverseChanges);
			refreshRepository();
			return createMessageResult("Undo", "Reverted " + inverseChanges.size() + " ontology changes from the last SPARQL update.");
		}
		catch (Exception e) {
			throw new SparqlReasonerException(e);
		}
	}
	
	@Override
	public void precalculate() throws SparqlReasonerException {
		if (repository == null) {
			try {
				repository = new SailRepository(new MemoryStore());
				repository.initialize();
				loadOntologies(repository);
			}
			catch (Exception e) {
				throw new SparqlReasonerException(e);
			}
		}
	}
	
	@Override
	public SparqlResultSet executeQuery(String queryString) throws SparqlReasonerException {
		precalculate();
		try {
			RepositoryConnection connection = null;
			try {
				connection = repository.getConnection();
				return executeQueryOrUpdate(connection, queryString);
			}
			finally {
				if (connection != null) {
					connection.close();
				}
			}
		}
		catch (Exception e) {
			throw new SparqlReasonerException(e);
		}
	}
	
	private SparqlResultSet executeQueryOrUpdate(RepositoryConnection connection, String queryString) throws Exception {
		try {
			Query query = connection.prepareQuery(QueryLanguage.SPARQL, queryString);
			if (query instanceof TupleQuery) {
				return handleTupleQuery((TupleQuery) query);
			}
			else if (query instanceof GraphQuery) {
				return handleGraphQuery((GraphQuery) query);
			}
			else if (query instanceof BooleanQuery) {
				return handleBooleanQuery((BooleanQuery) query);
			}
			else {
				throw new IllegalStateException("Can't handle queries of type " + query.getClass());
			}
		}
		catch (Exception queryException) {
			try {
				Update update = connection.prepareUpdate(QueryLanguage.SPARQL, queryString);
				return handleUpdate(connection, update);
			}
			catch (Exception updateException) {
				updateException.addSuppressed(queryException);
				throw updateException;
			}
		}
	}
	
	private SparqlResultSet handleUpdate(RepositoryConnection connection, Update update) throws Exception {
		Set<Statement> before = getStatements(connection);
		update.execute();
		Set<Statement> after = getStatements(connection);
		AppliedUpdate updateSummary = applyUpdateDiff(before, after);
		return createUpdateResult(updateSummary);
	}
	
	private AppliedUpdate applyUpdateDiff(Set<Statement> before, Set<Statement> after) throws Exception {
		Set<Statement> addedStatements = new HashSet<Statement>(after);
		addedStatements.removeAll(before);
		Set<Statement> removedStatements = new HashSet<Statement>(before);
		removedStatements.removeAll(after);
		AxiomDiff axiomDiff = createAxiomDiff(addedStatements, removedStatements);
		AppliedUpdate appliedUpdate = applyAxiomDiff(axiomDiff, addedStatements.size(), removedStatements.size());
		return appliedUpdate;
	}
	
	private SparqlUpdatePreview createUpdatePreview(Set<Statement> before, Set<Statement> after) throws Exception {
		Set<Statement> addedStatements = new HashSet<Statement>(after);
		addedStatements.removeAll(before);
		Set<Statement> removedStatements = new HashSet<Statement>(before);
		removedStatements.removeAll(after);
		AxiomDiff axiomDiff = createAxiomDiff(addedStatements, removedStatements);
		return new SparqlUpdatePreview(true, addedStatements.size(), removedStatements.size(), axiomDiff.getAddedAxioms().size(), axiomDiff.getRemovedAxioms().size(), axiomDiff.getFallbackTripleCount());
	}
	
	private AxiomDiff createAxiomDiff(Set<Statement> addedStatements, Set<Statement> removedStatements) throws Exception {
		StatementAxiomConverter converter = new StatementAxiomConverter(manager);
		StatementAxiomConverter.StatementConversion addedConversion = converter.convert(addedStatements);
		StatementAxiomConverter.StatementConversion removedConversion = converter.convert(removedStatements);
		Set<OWLAxiom> addedAxioms = new HashSet<OWLAxiom>(addedConversion.getAxioms());
		Set<OWLAxiom> removedAxioms = new HashSet<OWLAxiom>(removedConversion.getAxioms());
		addedAxioms.addAll(parseAxioms(addedConversion.getFallbackStatements()));
		removedAxioms.addAll(parseAxioms(removedConversion.getFallbackStatements()));
		return new AxiomDiff(addedAxioms, removedAxioms, addedConversion.getFallbackStatements().size() + removedConversion.getFallbackStatements().size());
	}
	
	private AppliedUpdate applyAxiomDiff(AxiomDiff axiomDiff, int addedTriples, int removedTriples) {
		OWLOntology addTarget = getTargetOntology();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		List<OWLOntologyChange> inverseChanges = new ArrayList<OWLOntologyChange>();
		Set<OWLOntology> changedOntologies = new HashSet<OWLOntology>();
		for (OWLAxiom axiom : axiomDiff.getRemovedAxioms()) {
			for (OWLOntology ontology : manager.getOntologies(axiom)) {
				changes.add(new RemoveAxiom(ontology, axiom));
				inverseChanges.add(0, new AddAxiom(ontology, axiom));
				changedOntologies.add(ontology);
			}
		}
		for (OWLAxiom axiom : axiomDiff.getAddedAxioms()) {
			if (!addTarget.containsAxiom(axiom)) {
				changes.add(new AddAxiom(addTarget, axiom));
				inverseChanges.add(0, new RemoveAxiom(addTarget, axiom));
				changedOntologies.add(addTarget);
			}
		}
		if (!changes.isEmpty()) {
			manager.applyChanges(changes);
			undoStack.push(inverseChanges);
			for (OWLOntology ontology : changedOntologies) {
				preferPlainTurtleFormat(ontology);
			}
		}
		return new AppliedUpdate(addedTriples, removedTriples, axiomDiff.getAddedAxioms().size(), axiomDiff.getRemovedAxioms().size(), axiomDiff.getFallbackTripleCount(), changes.size());
	}
	
	private Set<Statement> getStatements(RepositoryConnection connection) throws RepositoryException {
		RepositoryResult<Statement> result = null;
		try {
			result = connection.getStatements(null, null, null, false);
			return new HashSet<Statement>(result.asList());
		}
		finally {
			if (result != null) {
				result.close();
			}
		}
	}
	
	private Repository createRepository(Set<Statement> statements) throws RepositoryException {
		Repository newRepository = new SailRepository(new MemoryStore());
		newRepository.initialize();
		RepositoryConnection connection = null;
		try {
			connection = newRepository.getConnection();
			connection.add(statements);
		}
		finally {
			if (connection != null) {
				connection.close();
			}
		}
		return newRepository;
	}
	
	private Set<OWLAxiom> parseAxioms(Set<Statement> statements) throws Exception {
		Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
		if (statements.isEmpty()) {
			return axioms;
		}
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		RDFXMLWriterFactory writerFactory = new RDFXMLWriterFactory();
		org.openrdf.rio.RDFWriter writer = writerFactory.getWriter(outputStream);
		writer.startRDF();
		for (Statement statement : statements) {
			writer.handleStatement(statement);
		}
		writer.endRDF();
		OWLOntologyManager tempManager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = tempManager.loadOntologyFromOntologyDocument(new ByteArrayInputStream(outputStream.toByteArray()));
		for (OWLOntology loadedOntology : tempManager.getOntologies()) {
			axioms.addAll(loadedOntology.getAxioms());
		}
		tempManager.removeOntology(ontology);
		return axioms;
	}
	
	private int addAxioms(Set<OWLAxiom> axioms) {
		OWLOntology ontology = getTargetOntology();
		int count = 0;
		for (OWLAxiom axiom : axioms) {
			if (!ontology.containsAxiom(axiom)) {
				manager.applyChange(new AddAxiom(ontology, axiom));
				count++;
			}
		}
		return count;
	}
	
	private int removeAxioms(Set<OWLAxiom> axioms) {
		int count = 0;
		for (OWLAxiom axiom : axioms) {
			for (OWLOntology ontology : manager.getOntologies(axiom)) {
				manager.applyChange(new RemoveAxiom(ontology, axiom));
				count++;
			}
		}
		return count;
	}
	
	private OWLOntology getTargetOntology() {
		if (targetOntology != null && manager.contains(targetOntology)) {
			return targetOntology;
		}
		return manager.getOntologies().iterator().next();
	}
	
	private void preferPlainTurtleFormat(OWLOntology ontology) {
		OWLDocumentFormat format = manager.getOntologyFormat(ontology);
		if (format instanceof TurtleDocumentFormat) {
			RioTurtleDocumentFormat rioFormat = new RioTurtleDocumentFormat();
			if (format.isPrefixOWLOntologyFormat()) {
				PrefixDocumentFormat prefixFormat = format.asPrefixOWLOntologyFormat();
				rioFormat.copyPrefixesFrom(prefixFormat);
			}
			manager.setOntologyFormat(ontology, rioFormat);
		}
	}
	
	private SparqlResultSet createUpdateResult(AppliedUpdate update) {
		List<String> columnNames = new ArrayList<String>();
		columnNames.add("Operation");
		columnNames.add("Added triples");
		columnNames.add("Removed triples");
		columnNames.add("Added axioms");
		columnNames.add("Removed axioms");
		columnNames.add("Fallback triples");
		columnNames.add("Applied changes");
		SparqlResultSet result = new SparqlResultSet(columnNames);
		List<Object> row = new ArrayList<Object>();
		row.add("SPARQL Update");
		row.add(update.getAddedTriples());
		row.add(update.getRemovedTriples());
		row.add(update.getAddedAxioms());
		row.add(update.getRemovedAxioms());
		row.add(update.getFallbackTriples());
		row.add(update.getAppliedChanges());
		result.addRow(row);
		return result;
	}
	
	private SparqlResultSet createMessageResult(String operation, String message) {
		SparqlResultSet result = new SparqlResultSet(Collections.singletonList("Message"));
		List<Object> row = new ArrayList<Object>();
		row.add(operation + ": " + message);
		result.addRow(row);
		return result;
	}
	
	private void loadOntologies(Repository repository) throws Exception {
		RepositoryConnection connection = null;
		try {
			connection = repository.getConnection();
			connection.setAutoCommit(false);
			for (OWLOntology ontology : manager.getOntologies()) {
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				manager.saveOntology(ontology, new RDFXMLDocumentFormat(), outputStream);
				ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
				loadRdfXml(connection, inputStream, getBaseIRI(ontology));
			}
			connection.commit();
		}
		catch (Exception e) {
			if (connection != null) {
				try {
					connection.rollback();
				}
				catch (RepositoryException re) {
					e.addSuppressed(re);
				}
			}
			throw e;
		}
		finally {
			if (connection != null) {
				connection.close();
			}
		}
	}
	
	private void loadRdfXml(RepositoryConnection connection, ByteArrayInputStream inputStream, String baseIRI) throws Exception {
		RDFParser parser = new RDFXMLParserFactory().getParser();
		parser.setValueFactory(connection.getValueFactory());
		parser.setRDFHandler(new RDFInserter(connection));
		parser.parse(inputStream, baseIRI);
	}
	
	private String getBaseIRI(OWLOntology ontology) {
		IRI documentIRI = manager.getOntologyDocumentIRI(ontology);
		if (documentIRI != null) {
			return documentIRI.toString();
		}
		if (ontology.getOntologyID().getOntologyIRI().isPresent()) {
			return ontology.getOntologyID().getOntologyIRI().get().toString();
		}
		return "";
	}
	
	private void refreshRepository() throws Exception {
		if (repository != null) {
			repository.shutDown();
			repository = null;
		}
		precalculate();
	}
	
	private SparqlResultSet handleTupleQuery(TupleQuery tupleQuery) throws QueryEvaluationException, TupleQueryResultHandlerException {
		TupleQueryHandler handler = new TupleQueryHandler();
		tupleQuery.evaluate(handler);
		return handler.getQueryResult();
	}
	
	private SparqlResultSet handleGraphQuery(GraphQuery graph) throws QueryEvaluationException, RDFHandlerException {
		GraphQueryHandler handler = new GraphQueryHandler();
		graph.evaluate(handler);
		return handler.getQueryResult();
	}
	
	private SparqlResultSet handleBooleanQuery(BooleanQuery booleanQuery) throws QueryEvaluationException {
		List<String> columnNames = new ArrayList<String>();
		columnNames.add("Result");
		SparqlResultSet result = new SparqlResultSet(columnNames);
		List<Object> row = new ArrayList<Object>();
		row.add(booleanQuery.evaluate() ? "True" : "False");
		result.addRow(row);
		return result;
	}

	@Override
	public void dispose() {
		if (repository != null) {
			try {
				repository.shutDown();
			}
			catch (RepositoryException e) {
				// Ignore shutdown errors; the query view is already closing.
			}
			repository = null;
		}
	}
	
	private static class AxiomDiff {
		private final Set<OWLAxiom> addedAxioms;
		private final Set<OWLAxiom> removedAxioms;
		private final int fallbackTripleCount;
		
		AxiomDiff(Set<OWLAxiom> addedAxioms, Set<OWLAxiom> removedAxioms, int fallbackTripleCount) {
			this.addedAxioms = addedAxioms;
			this.removedAxioms = removedAxioms;
			this.fallbackTripleCount = fallbackTripleCount;
		}
		
		Set<OWLAxiom> getAddedAxioms() {
			return addedAxioms;
		}
		
		Set<OWLAxiom> getRemovedAxioms() {
			return removedAxioms;
		}
		
		int getFallbackTripleCount() {
			return fallbackTripleCount;
		}
	}
	
	private static class AppliedUpdate {
		private final int addedTriples;
		private final int removedTriples;
		private final int addedAxioms;
		private final int removedAxioms;
		private final int fallbackTriples;
		private final int appliedChanges;
		
		AppliedUpdate(int addedTriples, int removedTriples, int addedAxioms, int removedAxioms, int fallbackTriples, int appliedChanges) {
			this.addedTriples = addedTriples;
			this.removedTriples = removedTriples;
			this.addedAxioms = addedAxioms;
			this.removedAxioms = removedAxioms;
			this.fallbackTriples = fallbackTriples;
			this.appliedChanges = appliedChanges;
		}
		
		int getAddedTriples() {
			return addedTriples;
		}
		
		int getRemovedTriples() {
			return removedTriples;
		}
		
		int getAddedAxioms() {
			return addedAxioms;
		}
		
		int getRemovedAxioms() {
			return removedAxioms;
		}
		
		int getFallbackTriples() {
			return fallbackTriples;
		}
		
		int getAppliedChanges() {
			return appliedChanges;
		}
	}

}
