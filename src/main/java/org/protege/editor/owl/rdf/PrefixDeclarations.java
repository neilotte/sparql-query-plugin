package org.protege.editor.owl.rdf;

import org.protege.editor.owl.model.OWLModelManager;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PrefixDeclarations {
	private PrefixDeclarations() {
	}

	public static String create(OWLModelManager modelManager) {
		Map<String, String> prefixes = new LinkedHashMap<String, String>();
		addCommonPrefixes(prefixes);
		addOntologyPrefixes(prefixes, modelManager);
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, String> entry : prefixes.entrySet()) {
			builder.append("PREFIX ");
			builder.append(entry.getKey());
			builder.append(" <");
			builder.append(entry.getValue());
			builder.append(">\n");
		}
		return builder.toString();
	}

	private static void addCommonPrefixes(Map<String, String> prefixes) {
		prefixes.put("rdf:", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		prefixes.put("rdfs:", "http://www.w3.org/2000/01/rdf-schema#");
		prefixes.put("owl:", "http://www.w3.org/2002/07/owl#");
		prefixes.put("xsd:", "http://www.w3.org/2001/XMLSchema#");
	}

	private static void addOntologyPrefixes(Map<String, String> prefixes, OWLModelManager modelManager) {
		OWLOntologyManager ontologyManager = modelManager.getOWLOntologyManager();
		Set<OWLOntology> ontologies = modelManager.getOntologies();
		for (OWLOntology ontology : ontologies) {
			OWLDocumentFormat format = ontologyManager.getOntologyFormat(ontology);
			if (format instanceof PrefixDocumentFormat) {
				PrefixDocumentFormat prefixFormat = (PrefixDocumentFormat) format;
				for (Map.Entry<String, String> entry : prefixFormat.getPrefixName2PrefixMap().entrySet()) {
					addPrefix(prefixes, entry.getKey(), entry.getValue());
				}
			}
			addOntologyIriPrefix(prefixes, ontology);
		}
	}

	private static void addOntologyIriPrefix(Map<String, String> prefixes, OWLOntology ontology) {
		if (ontology.getOntologyID().getOntologyIRI().isPresent()) {
			IRI ontologyIri = ontology.getOntologyID().getOntologyIRI().get();
			String namespace = ontologyIri.getNamespace();
			if (namespace == null || namespace.length() == 0) {
				namespace = ontologyIri.toString();
			}
			addPrefix(prefixes, ":", namespace);
			addPrefix(prefixes, uniquePrefixName(prefixes, ontologyIri.getShortForm()), namespace);
		}
	}

	private static void addPrefix(Map<String, String> prefixes, String prefixName, String namespace) {
		if (namespace == null || namespace.trim().length() == 0) {
			return;
		}
		String normalized = normalizePrefixName(prefixName);
		if (normalized == null || prefixes.containsKey(normalized)) {
			return;
		}
		prefixes.put(normalized, namespace);
	}

	private static String normalizePrefixName(String prefixName) {
		if (prefixName == null) {
			return null;
		}
		String normalized = prefixName.trim();
		if (normalized.length() == 0) {
			return ":";
		}
		if (!normalized.endsWith(":")) {
			normalized = normalized + ":";
		}
		String candidate = normalized.substring(0, normalized.length() - 1);
		if (candidate.length() == 0) {
			return ":";
		}
		if (!candidate.matches("[A-Za-z_][A-Za-z0-9._-]*")) {
			return null;
		}
		return normalized;
	}

	private static String uniquePrefixName(Map<String, String> prefixes, String baseName) {
		String cleanName = baseName == null ? "ontology" : baseName.replaceAll("[^A-Za-z0-9._-]", "");
		if (cleanName.length() == 0 || !Character.isLetter(cleanName.charAt(0))) {
			cleanName = "ontology" + cleanName;
		}
		String candidate = cleanName + ":";
		int suffix = 1;
		while (prefixes.containsKey(candidate)) {
			candidate = cleanName + suffix + ":";
			suffix++;
		}
		return candidate;
	}
}
