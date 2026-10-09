package org.protege.editor.owl.rdf.repository;

import java.util.HashSet;
import java.util.Set;

import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

class StatementAxiomConverter {
	private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
	private static final String RDFS_CLASS = "http://www.w3.org/2000/01/rdf-schema#Class";
	private static final String RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
	private static final String RDFS_DOMAIN = "http://www.w3.org/2000/01/rdf-schema#domain";
	private static final String RDFS_RANGE = "http://www.w3.org/2000/01/rdf-schema#range";
	private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
	private static final String RDFS_COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment";
	private static final String RDFS_SEE_ALSO = "http://www.w3.org/2000/01/rdf-schema#seeAlso";
	private static final String RDFS_IS_DEFINED_BY = "http://www.w3.org/2000/01/rdf-schema#isDefinedBy";
	private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";
	private static final String OWL_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#ObjectProperty";
	private static final String OWL_DATATYPE_PROPERTY = "http://www.w3.org/2002/07/owl#DatatypeProperty";
	private static final String OWL_ANNOTATION_PROPERTY = "http://www.w3.org/2002/07/owl#AnnotationProperty";
	private static final String OWL_NAMED_INDIVIDUAL = "http://www.w3.org/2002/07/owl#NamedIndividual";
	private static final String OWL_EQUIVALENT_CLASS = "http://www.w3.org/2002/07/owl#equivalentClass";
	private static final String OWL_DISJOINT_WITH = "http://www.w3.org/2002/07/owl#disjointWith";
	private static final String OWL_THING = "http://www.w3.org/2002/07/owl#Thing";
	private static final String OWL_NOTHING = "http://www.w3.org/2002/07/owl#Nothing";
	private static final String OWL_VERSION_INFO = "http://www.w3.org/2002/07/owl#versionInfo";
	private static final String OWL_DEPRECATED = "http://www.w3.org/2002/07/owl#deprecated";
	
	private final OWLOntologyManager manager;
	private final OWLDataFactory dataFactory;
	
	StatementAxiomConverter(OWLOntologyManager manager) {
		this.manager = manager;
		dataFactory = manager.getOWLDataFactory();
	}
	
	StatementConversion convert(Set<Statement> statements) {
		Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
		Set<Statement> fallbackStatements = new HashSet<Statement>();
		for (Statement statement : statements) {
			OWLAxiom axiom = convert(statement);
			if (axiom == null) {
				fallbackStatements.add(statement);
			}
			else {
				axioms.add(axiom);
			}
		}
		return new StatementConversion(axioms, fallbackStatements);
	}
	
	private OWLAxiom convert(Statement statement) {
		if (!(statement.getSubject() instanceof URI)) {
			return null;
		}
		String subject = statement.getSubject().stringValue();
		String predicate = statement.getPredicate().stringValue();
		Value object = statement.getObject();
		if (RDF_TYPE.equals(predicate) && object instanceof URI) {
			return typeAxiom(subject, object.stringValue());
		}
		if (object instanceof URI) {
			String objectIRI = object.stringValue();
			if (RDFS_SUBCLASS_OF.equals(predicate)) {
				return dataFactory.getOWLSubClassOfAxiom(dataFactory.getOWLClass(iri(subject)), dataFactory.getOWLClass(iri(objectIRI)));
			}
			if (OWL_EQUIVALENT_CLASS.equals(predicate)) {
				return dataFactory.getOWLEquivalentClassesAxiom(dataFactory.getOWLClass(iri(subject)), dataFactory.getOWLClass(iri(objectIRI)));
			}
			if (OWL_DISJOINT_WITH.equals(predicate)) {
				return dataFactory.getOWLDisjointClassesAxiom(dataFactory.getOWLClass(iri(subject)), dataFactory.getOWLClass(iri(objectIRI)));
			}
			if (RDFS_DOMAIN.equals(predicate)) {
				return isProperty(subject) ? domainAxiom(subject, objectIRI) : annotationAssertion(subject, predicate, objectIRI);
			}
			if (RDFS_RANGE.equals(predicate)) {
				return isProperty(subject) ? rangeAxiom(subject, objectIRI) : annotationAssertion(subject, predicate, objectIRI);
			}
			if (isAnnotationProperty(predicate)) {
				return annotationAssertion(subject, predicate, objectIRI);
			}
			if (isObjectProperty(predicate)) {
				return dataFactory.getOWLObjectPropertyAssertionAxiom(dataFactory.getOWLObjectProperty(iri(predicate)), dataFactory.getOWLNamedIndividual(iri(subject)), dataFactory.getOWLNamedIndividual(iri(objectIRI)));
			}
			return annotationAssertion(subject, predicate, objectIRI);
		}
		if (object instanceof Literal) {
			Literal literal = (Literal) object;
			if (isAnnotationProperty(predicate)) {
				return dataFactory.getOWLAnnotationAssertionAxiom(dataFactory.getOWLAnnotationProperty(iri(predicate)), iri(subject), literal(literal));
			}
			if (isDataProperty(predicate)) {
				return dataFactory.getOWLDataPropertyAssertionAxiom(dataFactory.getOWLDataProperty(iri(predicate)), dataFactory.getOWLNamedIndividual(iri(subject)), literal(literal));
			}
			return dataFactory.getOWLAnnotationAssertionAxiom(dataFactory.getOWLAnnotationProperty(iri(predicate)), iri(subject), literal(literal));
		}
		return null;
	}
	
	private OWLAxiom typeAxiom(String subject, String object) {
		if (OWL_CLASS.equals(object) || RDFS_CLASS.equals(object)) {
			return dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLClass(iri(subject)));
		}
		if (OWL_OBJECT_PROPERTY.equals(object)) {
			return dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLObjectProperty(iri(subject)));
		}
		if (OWL_DATATYPE_PROPERTY.equals(object)) {
			return dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLDataProperty(iri(subject)));
		}
		if (OWL_ANNOTATION_PROPERTY.equals(object)) {
			return dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLAnnotationProperty(iri(subject)));
		}
		if (OWL_NAMED_INDIVIDUAL.equals(object)) {
			return dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLNamedIndividual(iri(subject)));
		}
		if (isClassIRI(object)) {
			return dataFactory.getOWLClassAssertionAxiom(dataFactory.getOWLClass(iri(object)), dataFactory.getOWLNamedIndividual(iri(subject)));
		}
		return null;
	}
	
	private OWLAxiom domainAxiom(String propertyIRI, String classIRI) {
		if (isAnnotationProperty(propertyIRI)) {
			return dataFactory.getOWLAnnotationPropertyDomainAxiom(dataFactory.getOWLAnnotationProperty(iri(propertyIRI)), iri(classIRI));
		}
		if (isDataProperty(propertyIRI)) {
			return dataFactory.getOWLDataPropertyDomainAxiom(dataFactory.getOWLDataProperty(iri(propertyIRI)), dataFactory.getOWLClass(iri(classIRI)));
		}
		return dataFactory.getOWLObjectPropertyDomainAxiom(dataFactory.getOWLObjectProperty(iri(propertyIRI)), dataFactory.getOWLClass(iri(classIRI)));
	}
	
	private OWLAxiom rangeAxiom(String propertyIRI, String rangeIRI) {
		if (isAnnotationProperty(propertyIRI)) {
			return dataFactory.getOWLAnnotationPropertyRangeAxiom(dataFactory.getOWLAnnotationProperty(iri(propertyIRI)), iri(rangeIRI));
		}
		if (isDataProperty(propertyIRI)) {
			return dataFactory.getOWLDataPropertyRangeAxiom(dataFactory.getOWLDataProperty(iri(propertyIRI)), dataFactory.getOWLDatatype(iri(rangeIRI)));
		}
		return dataFactory.getOWLObjectPropertyRangeAxiom(dataFactory.getOWLObjectProperty(iri(propertyIRI)), dataFactory.getOWLClass(iri(rangeIRI)));
	}
	
	private boolean isAnnotationProperty(String iri) {
		return RDFS_LABEL.equals(iri) || RDFS_COMMENT.equals(iri) || RDFS_SEE_ALSO.equals(iri) || RDFS_IS_DEFINED_BY.equals(iri)
				|| OWL_VERSION_INFO.equals(iri) || OWL_DEPRECATED.equals(iri) || isDeclared(dataFactory.getOWLAnnotationProperty(iri(iri)));
	}
	
	private boolean isDataProperty(String iri) {
		return isDeclared(dataFactory.getOWLDataProperty(iri(iri)));
	}
	
	private boolean isObjectProperty(String iri) {
		return isDeclared(dataFactory.getOWLObjectProperty(iri(iri)));
	}
	
	private boolean isProperty(String iri) {
		return isAnnotationProperty(iri) || isDataProperty(iri) || isObjectProperty(iri);
	}
	
	private boolean isClassIRI(String iri) {
		return OWL_THING.equals(iri) || OWL_NOTHING.equals(iri) || isDeclared(dataFactory.getOWLClass(iri(iri)));
	}
	
	private boolean isDeclared(OWLEntity entity) {
		for (OWLOntology ontology : manager.getOntologies()) {
			if (ontology.isDeclared(entity, org.semanticweb.owlapi.model.parameters.Imports.INCLUDED)) {
				return true;
			}
		}
		return false;
	}
	
	private OWLLiteral literal(Literal literal) {
		String language = literal.getLanguage();
		if (language != null && language.length() > 0) {
			return dataFactory.getOWLLiteral(literal.getLabel(), language);
		}
		URI datatype = literal.getDatatype();
		if (datatype != null) {
			return dataFactory.getOWLLiteral(literal.getLabel(), dataFactory.getOWLDatatype(iri(datatype.stringValue())));
		}
		return dataFactory.getOWLLiteral(literal.getLabel());
	}
	
	private IRI iri(String iri) {
		return IRI.create(iri);
	}
	
	private OWLAxiom annotationAssertion(String subject, String predicate, String object) {
		return dataFactory.getOWLAnnotationAssertionAxiom(dataFactory.getOWLAnnotationProperty(iri(predicate)), iri(subject), iri(object));
	}
	
	static class StatementConversion {
		private final Set<OWLAxiom> axioms;
		private final Set<Statement> fallbackStatements;
		
		StatementConversion(Set<OWLAxiom> axioms, Set<Statement> fallbackStatements) {
			this.axioms = axioms;
			this.fallbackStatements = fallbackStatements;
		}
		
		Set<OWLAxiom> getAxioms() {
			return axioms;
		}
		
		Set<Statement> getFallbackStatements() {
			return fallbackStatements;
		}
	}
}
