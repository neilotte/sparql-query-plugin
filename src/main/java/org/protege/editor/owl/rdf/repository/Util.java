package org.protege.editor.owl.rdf.repository;

import org.openrdf.model.BNode;
import org.openrdf.model.Literal;
import org.openrdf.model.Value;
import org.semanticweb.owlapi.model.IRI;

public class Util {
	private Util() {
		
	}

	public static Object convertValue(Value v) {
		Object converted = "";
		if (v == null) {
			return converted;
		}
		if (v instanceof BNode) {
			converted = "_:" + ((BNode) v).getID();
		}
		else if (v instanceof org.openrdf.model.URI) {
			converted = IRI.create(((org.openrdf.model.URI) v).stringValue());
		}
		else if (v instanceof Literal) {
			converted = ((Literal) v).getLabel();
		}
		else {
			converted = v.stringValue();
		}
		return converted;
	}
	
	
}
