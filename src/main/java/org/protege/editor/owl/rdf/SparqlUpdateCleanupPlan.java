package org.protege.editor.owl.rdf;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SparqlUpdateCleanupPlan {
	private final Set<String> objectPropertyIRIs = new HashSet<String>();
	private final Set<String> annotationPropertyIRIs = new HashSet<String>();
	private final Set<String> namedIndividualIRIs = new HashSet<String>();
	
	public void addObjectPropertyIRI(String iri) {
		objectPropertyIRIs.add(iri);
	}
	
	public void addAnnotationPropertyIRI(String iri) {
		annotationPropertyIRIs.add(iri);
	}
	
	public void addNamedIndividualIRI(String iri) {
		namedIndividualIRIs.add(iri);
	}
	
	public boolean isEmpty() {
		return objectPropertyIRIs.isEmpty() && annotationPropertyIRIs.isEmpty() && namedIndividualIRIs.isEmpty();
	}
	
	public Set<String> getObjectPropertyIRIs() {
		return Collections.unmodifiableSet(objectPropertyIRIs);
	}
	
	public Set<String> getAnnotationPropertyIRIs() {
		return Collections.unmodifiableSet(annotationPropertyIRIs);
	}
	
	public Set<String> getNamedIndividualIRIs() {
		return Collections.unmodifiableSet(namedIndividualIRIs);
	}
}
