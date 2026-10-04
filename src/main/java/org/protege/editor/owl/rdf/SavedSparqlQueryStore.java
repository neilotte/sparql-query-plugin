package org.protege.editor.owl.rdf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class SavedSparqlQueryStore {
	private static final String COUNT_KEY = "count";
	private static final String NAME_KEY = "name.";
	private static final String QUERY_KEY = "query.";

	private final Preferences preferences;

	public SavedSparqlQueryStore(Preferences preferences) {
		this.preferences = preferences;
	}

	public List<SavedSparqlQuery> loadQueries() {
		int count = preferences.getInt(COUNT_KEY, 0);
		List<SavedSparqlQuery> queries = new ArrayList<SavedSparqlQuery>();
		for (int i = 0; i < count; i++) {
			String name = preferences.get(NAME_KEY + i, null);
			String query = decode(preferences.get(QUERY_KEY + i, null));
			if (name != null && query != null) {
				queries.add(new SavedSparqlQuery(name, query));
			}
		}
		return queries;
	}

	public void saveQueries(List<SavedSparqlQuery> queries) {
		try {
			preferences.clear();
		}
		catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
		preferences.putInt(COUNT_KEY, queries.size());
		for (int i = 0; i < queries.size(); i++) {
			SavedSparqlQuery query = queries.get(i);
			preferences.put(NAME_KEY + i, query.getName());
			preferences.put(QUERY_KEY + i, encode(query.getQuery()));
		}
	}

	private String encode(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private String decode(String value) {
		if (value == null) {
			return null;
		}
		return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
	}
}
