package org.protege.editor.owl.rdf.repository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.openrdf.query.BooleanQuery;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.Query;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.rdfxml.RDFXMLParserFactory;
import org.openrdf.repository.util.RDFInserter;
import org.openrdf.sail.memory.MemoryStore;
import org.protege.editor.owl.rdf.SparqlReasoner;
import org.protege.editor.owl.rdf.SparqlReasonerException;
import org.protege.editor.owl.rdf.SparqlResultSet;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.util.NamespaceUtil;

public class BasicSparqlReasoner implements SparqlReasoner {
	private OWLOntologyManager manager;
	private Repository repository;
	
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

}
