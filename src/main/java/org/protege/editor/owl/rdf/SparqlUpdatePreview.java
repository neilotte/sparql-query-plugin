package org.protege.editor.owl.rdf;

public class SparqlUpdatePreview {
	private final boolean update;
	private final int addedTriples;
	private final int removedTriples;
	private final int addedAxioms;
	private final int removedAxioms;
	private final int fallbackTriples;
	
	public SparqlUpdatePreview(boolean update, int addedTriples, int removedTriples, int addedAxioms, int removedAxioms, int fallbackTriples) {
		this.update = update;
		this.addedTriples = addedTriples;
		this.removedTriples = removedTriples;
		this.addedAxioms = addedAxioms;
		this.removedAxioms = removedAxioms;
		this.fallbackTriples = fallbackTriples;
	}
	
	public static SparqlUpdatePreview notUpdate() {
		return new SparqlUpdatePreview(false, 0, 0, 0, 0, 0);
	}
	
	public boolean isUpdate() {
		return update;
	}
	
	public boolean hasChanges() {
		return addedTriples > 0 || removedTriples > 0 || addedAxioms > 0 || removedAxioms > 0;
	}
	
	public int getAddedTriples() {
		return addedTriples;
	}
	
	public int getRemovedTriples() {
		return removedTriples;
	}
	
	public int getAddedAxioms() {
		return addedAxioms;
	}
	
	public int getRemovedAxioms() {
		return removedAxioms;
	}
	
	public int getFallbackTriples() {
		return fallbackTriples;
	}
}
