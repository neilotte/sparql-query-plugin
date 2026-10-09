package org.protege.editor.owl.rdf;

import org.semanticweb.owlapi.model.OWLOntology;

public interface SparqlReasoner {
	
	void precalculate() throws SparqlReasonerException;
	
	String getSampleQuery();
	
	default void setTargetOntology(OWLOntology ontology) {
	}
	
	default SparqlUpdatePreview previewUpdate(String query) throws SparqlReasonerException {
		return SparqlUpdatePreview.notUpdate();
	}
	
	default SparqlResultSet undoLastUpdate() throws SparqlReasonerException {
		throw new SparqlReasonerException("This SPARQL reasoner does not support undoing updates.");
	}
	
	default SparqlUpdateCleanupPlan getLastUpdateCleanupPlan() {
		return new SparqlUpdateCleanupPlan();
	}
	
	SparqlResultSet executeQuery(String query) throws SparqlReasonerException;
	
	void dispose();
}
