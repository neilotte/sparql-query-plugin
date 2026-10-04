package org.protege.editor.owl.rdf.repository;

import java.util.ArrayList;
import java.util.List;

import org.openrdf.model.Statement;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.protege.editor.owl.rdf.SparqlResultSet;

public class GraphQueryHandler implements RDFHandler {
	private SparqlResultSet queryResult;
	
	public SparqlResultSet getQueryResult() {
		return queryResult;
	}

	@Override
	public void startRDF() throws RDFHandlerException {
		List<String> bindingNames = new ArrayList<String>();
		bindingNames.add("Subject");
		bindingNames.add("Predicate");
		bindingNames.add("Object");
		queryResult = new SparqlResultSet(bindingNames);
	}

	@Override
	public void handleComment(String arg0) throws RDFHandlerException {
	
	}

	@Override
	public void handleNamespace(String arg0, String arg1) throws RDFHandlerException {	
	}

	@Override
	public void handleStatement(Statement stmt) throws RDFHandlerException {
		List<Object> row = new ArrayList<Object>();
		row.add(Util.convertValue(stmt.getSubject()));
		row.add(Util.convertValue(stmt.getPredicate()));
		row.add(Util.convertValue(stmt.getObject()));
		queryResult.addRow(row);
	}

	@Override
	public void endRDF() throws RDFHandlerException {

	}

}
