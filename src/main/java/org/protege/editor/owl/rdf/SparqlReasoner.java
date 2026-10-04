package org.protege.editor.owl.rdf;

import org.semanticweb.owlapi.model.OWLOntology;

public interface SparqlReasoner {
	
	void precalculate() throws SparqlReasonerException;
	
	String getSampleQuery();
	
	default void setTargetOntology(OWLOntology ontology) {
	}
	
	SparqlResultSet executeQuery(String query) throws SparqlReasonerException;
	
	void dispose();
}
