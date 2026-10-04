package org.protege.editor.owl.rdf;

public class SavedSparqlQuery {
	private final String name;
	private final String query;

	public SavedSparqlQuery(String name, String query) {
		this.name = name;
		this.query = query;
	}

	public String getName() {
		return name;
	}

	public String getQuery() {
		return query;
	}

	@Override
	public String toString() {
		return name;
	}
}
