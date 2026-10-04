package org.protege.editor.owl.rdf.repository;

import java.util.ArrayList;
import java.util.List;

import org.openrdf.model.Value;
import org.openrdf.query.Binding;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryResultHandlerException;
import org.openrdf.query.TupleQueryResultHandler;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.protege.editor.owl.rdf.SparqlResultSet;

public class TupleQueryHandler implements TupleQueryResultHandler {
	private SparqlResultSet queryResult;
	
	public SparqlResultSet getQueryResult() {
		return queryResult;
	}

	@Override
	public void startQueryResult(List<String> bindingNames) throws TupleQueryResultHandlerException {
		queryResult = new SparqlResultSet(bindingNames);
	}

	@Override
	public void handleSolution(BindingSet bindingSet) throws TupleQueryResultHandlerException {
		List<Object> row = new ArrayList<Object>();
		for (int i = 0; i < queryResult.getColumnCount(); i++) {
			String columnName = queryResult.getColumnName(i);
			Binding binding = bindingSet.getBinding(columnName);
			Value v = binding != null ? binding.getValue() : (Value) null;
			row.add(Util.convertValue(v));
		}
		queryResult.addRow(row);
	}
	
	@Override
	public void endQueryResult() throws TupleQueryResultHandlerException {

	}

	@Override
	public void handleBoolean(boolean arg0) throws QueryResultHandlerException {
		
	}

	@Override
	public void handleLinks(List<String> arg0) throws QueryResultHandlerException {
		
	}

}
