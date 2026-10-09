package org.protege.editor.owl.rdf;

import org.protege.editor.core.ui.error.ErrorLogPanel;
import org.protege.editor.owl.model.io.IOListener;
import org.protege.editor.owl.model.io.IOListenerEvent;
import org.protege.editor.owl.rdf.repository.BasicSparqlReasonerFactory;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.prefs.Preferences;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;

public class SparqlQueryView extends AbstractOWLViewComponent {
	private static final String RUN_ACTION_KEY = "run-sparql-query";

	private SparqlReasoner reasoner;
	private SavedSparqlQueryStore queryStore;
	private SavedSparqlQueryStore recentQueryStore;
	private final TurtleSaveCleaner turtleSaveCleaner = new TurtleSaveCleaner();
	private final JTextPane queryPane = new JTextPane();
	private final SwingResultModel resultModel = new SwingResultModel();
	private final SparqlResultExporter resultExporter = new SparqlResultExporter();
	private final DefaultComboBoxModel<SavedSparqlQuery> savedQueryModel = new DefaultComboBoxModel<SavedSparqlQuery>();
	private final JComboBox<SavedSparqlQuery> savedQueryCombo = new JComboBox<SavedSparqlQuery>(savedQueryModel);
	private final DefaultComboBoxModel<SavedSparqlQuery> recentQueryModel = new DefaultComboBoxModel<SavedSparqlQuery>();
	private final JComboBox<SavedSparqlQuery> recentQueryCombo = new JComboBox<SavedSparqlQuery>(recentQueryModel);
	private final DefaultComboBoxModel<QueryTemplate> templateModel = new DefaultComboBoxModel<QueryTemplate>();
	private final JComboBox<QueryTemplate> templateCombo = new JComboBox<QueryTemplate>(templateModel);
	private final DefaultComboBoxModel<OWLOntology> targetOntologyModel = new DefaultComboBoxModel<OWLOntology>();
	private final JComboBox<OWLOntology> targetOntologyCombo = new JComboBox<OWLOntology>(targetOntologyModel);
	private final JLabel statusLabel = new JLabel("Ready");
	private final JButton runButton = new JButton("Run");
	private final JButton cancelButton = new JButton("Cancel");
	private final JButton exportButton = new JButton("Export...");
	private final JButton undoButton = new JButton("Undo Update");
	private final List<SavedSparqlQuery> savedQueries = new ArrayList<SavedSparqlQuery>();
	private final List<SavedSparqlQuery> recentQueries = new ArrayList<SavedSparqlQuery>();

	private SwingWorker<SparqlResultSet, Void> queryWorker;
	private IOListener ioListener;
	private Timer elapsedTimer;
	private long queryStartedAt;
	private SparqlResultSet currentResult = new SparqlResultSet(new ArrayList<String>());
	private boolean loadingSavedQuery;

	@Override
	protected void initialiseOWLView() {
		queryStore = new SavedSparqlQueryStore(Preferences.userNodeForPackage(SparqlQueryView.class).node("savedQueries"));
		recentQueryStore = new SavedSparqlQueryStore(Preferences.userNodeForPackage(SparqlQueryView.class).node("recentQueries"));
		initializeReasoner();
		setLayout(new BorderLayout());
		add(createToolbar(), BorderLayout.NORTH);
		add(createCenterComponent(), BorderLayout.CENTER);
		add(createStatusComponent(), BorderLayout.SOUTH);
		loadSavedQueries();
		loadRecentQueries();
		loadTargetOntologies();
		loadTemplates();
		setInitialQuery();
		registerSaveCleaner();
		registerRunShortcut();
		updateButtonState();
	}

	private void initializeReasoner() {
		try {
			List<SparqlInferenceFactory> plugins = Collections.singletonList((SparqlInferenceFactory) new BasicSparqlReasonerFactory());
			reasoner = plugins.iterator().next().createReasoner(getOWLModelManager().getOWLOntologyManager());
		}
		catch (RuntimeException e) {
			ErrorLogPanel.showErrorDialog(e);
		}
	}

	private JComponent createToolbar() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		JPanel executionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		JPanel toolsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		savedQueryCombo.setPrototypeDisplayValue(new SavedSparqlQuery("Saved queries", ""));
		savedQueryCombo.setPreferredSize(new Dimension(150, savedQueryCombo.getPreferredSize().height));
		savedQueryCombo.addActionListener(e -> loadSelectedSavedQuery());
		recentQueryCombo.setPrototypeDisplayValue(new SavedSparqlQuery("Recent queries", ""));
		recentQueryCombo.setPreferredSize(new Dimension(150, recentQueryCombo.getPreferredSize().height));
		recentQueryCombo.addActionListener(e -> loadSelectedRecentQuery());
		templateCombo.setPreferredSize(new Dimension(150, templateCombo.getPreferredSize().height));
		templateCombo.addActionListener(e -> insertSelectedTemplate());
		targetOntologyCombo.setPreferredSize(new Dimension(170, targetOntologyCombo.getPreferredSize().height));
		targetOntologyCombo.setRenderer(new OntologyRenderer());
		runButton.addActionListener(e -> runQuery());
		cancelButton.addActionListener(e -> cancelQuery());
		undoButton.addActionListener(e -> undoLastUpdate());
		cancelButton.setEnabled(false);
		exportButton.addActionListener(e -> exportResults());
		executionPanel.add(runButton);
		executionPanel.add(cancelButton);
		executionPanel.add(undoButton);
		executionPanel.add(exportButton);
		executionPanel.add(new JLabel("Update target:"));
		executionPanel.add(targetOntologyCombo);
		toolsPanel.add(new JLabel("Query:"));
		toolsPanel.add(savedQueryCombo);
		toolsPanel.add(new JButton(newQueryAction()));
		toolsPanel.add(new JButton(saveQueryAction()));
		toolsPanel.add(new JButton(deleteQueryAction()));
		toolsPanel.add(new JButton(insertPrefixesAction()));
		toolsPanel.add(new JButton(addLimitAction()));
		toolsPanel.add(new JLabel("Template:"));
		toolsPanel.add(templateCombo);
		toolsPanel.add(new JLabel("Recent:"));
		toolsPanel.add(recentQueryCombo);
		panel.add(executionPanel, BorderLayout.NORTH);
		panel.add(toolsPanel, BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createCenterComponent() {
		queryPane.setText(createSampleQuery());
		JScrollPane queryScrollPane = new JScrollPane(queryPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		JTable results = new JTable(resultModel);
		results.setAutoCreateRowSorter(true);
		JScrollPane resultsScrollPane = new JScrollPane(results);
		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, queryScrollPane, resultsScrollPane);
		splitPane.setResizeWeight(0.45);
		return splitPane;
	}

	private JComponent createStatusComponent() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		panel.add(statusLabel, BorderLayout.WEST);
		return panel;
	}

	private Action newQueryAction() {
		return new AbstractAction("New") {
			@Override
			public void actionPerformed(ActionEvent e) {
				queryPane.setText(createSampleQuery());
				savedQueryCombo.setSelectedIndex(-1);
				statusLabel.setText("New query");
			}
		};
	}

	private Action saveQueryAction() {
		return new AbstractAction("Save") {
			@Override
			public void actionPerformed(ActionEvent e) {
				saveCurrentQuery();
			}
		};
	}

	private Action deleteQueryAction() {
		return new AbstractAction("Delete") {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteSelectedQuery();
			}
		};
	}

	private Action insertPrefixesAction() {
		return new AbstractAction("Insert Prefixes") {
			@Override
			public void actionPerformed(ActionEvent e) {
				insertPrefixes();
			}
		};
	}
	
	private Action addLimitAction() {
		return new AbstractAction("LIMIT 100") {
			@Override
			public void actionPerformed(ActionEvent e) {
				addLimit();
			}
		};
	}

	private void loadSavedQueries() {
		savedQueries.clear();
		savedQueries.addAll(queryStore.loadQueries());
		refreshSavedQueryModel();
	}
	
	private void loadRecentQueries() {
		recentQueries.clear();
		recentQueries.addAll(recentQueryStore.loadQueries());
		refreshRecentQueryModel();
	}
	
	private void loadTargetOntologies() {
		targetOntologyModel.removeAllElements();
		for (OWLOntology ontology : getOWLModelManager().getOWLOntologyManager().getOntologies()) {
			targetOntologyModel.addElement(ontology);
		}
		OWLOntology activeOntology = getOWLModelManager().getActiveOntology();
		if (activeOntology != null) {
			targetOntologyCombo.setSelectedItem(activeOntology);
		}
	}
	
	private void loadTemplates() {
		templateModel.removeAllElements();
		templateModel.addElement(new QueryTemplate("Choose template...", null));
		templateModel.addElement(new QueryTemplate("Classes with labels", "SELECT ?class ?label\nWHERE {\n  ?class a owl:Class .\n  OPTIONAL { ?class rdfs:label ?label }\n}\nORDER BY ?class\nLIMIT 100"));
		templateModel.addElement(new QueryTemplate("Subclass tree", "SELECT ?child ?parent\nWHERE {\n  ?child rdfs:subClassOf ?parent\n}\nORDER BY ?parent ?child\nLIMIT 100"));
		templateModel.addElement(new QueryTemplate("Object properties", "SELECT ?property ?domain ?range\nWHERE {\n  ?property a owl:ObjectProperty .\n  OPTIONAL { ?property rdfs:domain ?domain }\n  OPTIONAL { ?property rdfs:range ?range }\n}\nORDER BY ?property\nLIMIT 100"));
		templateModel.addElement(new QueryTemplate("Annotation assertions", "SELECT ?subject ?property ?value\nWHERE {\n  ?subject ?property ?value .\n  ?property a owl:AnnotationProperty\n}\nLIMIT 100"));
		templateModel.addElement(new QueryTemplate("Individuals by class", "SELECT ?individual ?class\nWHERE {\n  ?individual a ?class .\n  ?class a owl:Class\n}\nORDER BY ?class ?individual\nLIMIT 100"));
		templateModel.addElement(new QueryTemplate("CONSTRUCT labels", "CONSTRUCT {\n  ?entity rdfs:label ?label\n}\nWHERE {\n  ?entity rdfs:label ?label\n}\nLIMIT 100"));
		templateModel.addElement(new QueryTemplate("INSERT subclass", "INSERT DATA {\n  <http://example.com/NewClass> a owl:Class ;\n    rdfs:subClassOf <http://example.com/ParentClass> ;\n    rdfs:label \"New class\" .\n}"));
		templateModel.addElement(new QueryTemplate("DELETE triple", "DELETE DATA {\n  <http://example.com/NewClass> rdfs:label \"New class\" .\n}"));
		templateCombo.setSelectedIndex(0);
	}

	private void refreshSavedQueryModel() {
		loadingSavedQuery = true;
		try {
			savedQueryModel.removeAllElements();
			for (SavedSparqlQuery query : savedQueries) {
				savedQueryModel.addElement(query);
			}
			savedQueryCombo.setSelectedIndex(-1);
		}
		finally {
			loadingSavedQuery = false;
		}
	}
	
	private void refreshRecentQueryModel() {
		recentQueryModel.removeAllElements();
		for (SavedSparqlQuery query : recentQueries) {
			recentQueryModel.addElement(query);
		}
		recentQueryCombo.setSelectedIndex(-1);
	}

	private void setInitialQuery() {
		if (savedQueries.isEmpty()) {
			queryPane.setText(createSampleQuery());
		}
		else {
			savedQueryCombo.setSelectedIndex(0);
		}
	}

	private void loadSelectedSavedQuery() {
		if (loadingSavedQuery) {
			return;
		}
		SavedSparqlQuery query = (SavedSparqlQuery) savedQueryCombo.getSelectedItem();
		if (query != null) {
			queryPane.setText(query.getQuery());
			statusLabel.setText("Loaded \"" + query.getName() + "\"");
		}
	}
	
	private void loadSelectedRecentQuery() {
		SavedSparqlQuery query = (SavedSparqlQuery) recentQueryCombo.getSelectedItem();
		if (query != null) {
			queryPane.setText(query.getQuery());
			statusLabel.setText("Loaded recent query");
		}
	}
	
	private void insertSelectedTemplate() {
		QueryTemplate template = (QueryTemplate) templateCombo.getSelectedItem();
		if (template != null && template.getQuery() != null) {
			queryPane.setText(PrefixDeclarations.create(getOWLModelManager()) + "\n" + template.getQuery());
			queryPane.setCaretPosition(0);
			statusLabel.setText("Inserted \"" + template.getName() + "\" template");
			templateCombo.setSelectedIndex(0);
		}
	}

	private void saveCurrentQuery() {
		SavedSparqlQuery selected = (SavedSparqlQuery) savedQueryCombo.getSelectedItem();
		String defaultName = selected == null ? "" : selected.getName();
		String name = JOptionPane.showInputDialog(getOWLWorkspace(), "Query name", defaultName);
		if (name == null) {
			return;
		}
		name = name.trim();
		if (name.length() == 0) {
			JOptionPane.showMessageDialog(getOWLWorkspace(), "Please provide a query name.");
			return;
		}
		int existingIndex = findSavedQuery(name);
		SavedSparqlQuery savedQuery = new SavedSparqlQuery(name, queryPane.getText());
		if (existingIndex >= 0) {
			savedQueries.set(existingIndex, savedQuery);
		}
		else {
			savedQueries.add(savedQuery);
		}
		queryStore.saveQueries(savedQueries);
		refreshSavedQueryModel();
		savedQueryCombo.setSelectedIndex(findSavedQuery(name));
		statusLabel.setText("Saved \"" + name + "\"");
	}

	private void deleteSelectedQuery() {
		SavedSparqlQuery selected = (SavedSparqlQuery) savedQueryCombo.getSelectedItem();
		if (selected == null) {
			return;
		}
		int answer = JOptionPane.showConfirmDialog(getOWLWorkspace(), "Delete \"" + selected.getName() + "\"?", "Delete saved query", JOptionPane.OK_CANCEL_OPTION);
		if (answer != JOptionPane.OK_OPTION) {
			return;
		}
		int index = findSavedQuery(selected.getName());
		if (index >= 0) {
			savedQueries.remove(index);
			queryStore.saveQueries(savedQueries);
			refreshSavedQueryModel();
			statusLabel.setText("Deleted \"" + selected.getName() + "\"");
		}
	}

	private int findSavedQuery(String name) {
		for (int i = 0; i < savedQueries.size(); i++) {
			if (savedQueries.get(i).getName().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	private void insertPrefixes() {
		String prefixes = PrefixDeclarations.create(getOWLModelManager());
		String query = queryPane.getText();
		String queryWithoutPrefixes = query.replaceAll("(?im)^\\s*PREFIX\\s+[^\\n\\r]+[\\n\\r]*", "");
		queryPane.setText(prefixes + "\n" + queryWithoutPrefixes.trim());
		queryPane.setCaretPosition(0);
		statusLabel.setText("Prefixes refreshed from loaded ontologies");
	}
	
	private void addLimit() {
		String query = queryPane.getText();
		if (!query.matches("(?is).*\\bSELECT\\b.*") || query.matches("(?is).*\\bLIMIT\\s+\\d+\\b.*")) {
			statusLabel.setText("LIMIT helper applies to SELECT queries without an existing LIMIT");
			return;
		}
		queryPane.setText(query.trim() + "\nLIMIT 100");
		statusLabel.setText("Added LIMIT 100");
	}

	private void runQuery() {
		if (reasoner == null || queryWorker != null) {
			return;
		}
		final String query = queryPane.getText();
		final OWLOntology targetOntology = getSelectedTargetOntology();
		reasoner.setTargetOntology(targetOntology);
		if (looksLikeUpdate(query) && !confirmUpdate(query)) {
			return;
		}
		queryStartedAt = System.currentTimeMillis();
		statusLabel.setText("Running query...");
		updateRunningState(true);
		startElapsedTimer();
		queryWorker = new SwingWorker<SparqlResultSet, Void>() {
			@Override
			protected SparqlResultSet doInBackground() throws SparqlReasonerException {
				reasoner.setTargetOntology(targetOntology);
				return reasoner.executeQuery(query);
			}

			@Override
			protected void done() {
				stopElapsedTimer();
				try {
					if (!isCancelled()) {
						currentResult = get();
						resultModel.setResults(currentResult);
						turtleSaveCleaner.addCleanupPlan(reasoner.getLastUpdateCleanupPlan());
						recordRecentQuery(query);
						long elapsed = System.currentTimeMillis() - queryStartedAt;
						statusLabel.setText("Done: " + currentResult.getRowCount() + " rows in " + elapsed + " ms");
					}
				}
				catch (Exception ex) {
					Throwable cause = ex.getCause() == null ? ex : ex.getCause();
					ErrorLogPanel.showErrorDialog(cause);
					JOptionPane.showMessageDialog(getOWLWorkspace(), cause.getMessage() + "\nSee the logs for more information.");
					statusLabel.setText("Query failed");
				}
				finally {
					queryWorker = null;
					updateRunningState(false);
				}
			}
		};
		queryWorker.execute();
	}
	
	private boolean confirmUpdate(String query) {
		try {
			statusLabel.setText("Previewing SPARQL update...");
			SparqlUpdatePreview preview = reasoner.previewUpdate(query);
			if (!preview.isUpdate()) {
				return true;
			}
			String message = "This SPARQL Update will add " + preview.getAddedTriples() + " triples and remove " + preview.getRemovedTriples() + " triples.\n"
					+ "OWL changes: +" + preview.getAddedAxioms() + " axioms, -" + preview.getRemovedAxioms() + " axioms.\n"
					+ "Fallback triples: " + preview.getFallbackTriples() + ". These require OWLAPI RDF parsing.\n\n"
					+ "Apply these changes to the selected ontology?";
			return JOptionPane.showConfirmDialog(getOWLWorkspace(), message, "Apply SPARQL Update", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
		}
		catch (SparqlReasonerException ex) {
			ErrorLogPanel.showErrorDialog(ex);
			JOptionPane.showMessageDialog(getOWLWorkspace(), ex.getMessage() + "\nSee the logs for more information.");
			statusLabel.setText("Update preview failed");
			return false;
		}
	}
	
	private boolean looksLikeUpdate(String query) {
		return query.matches("(?is).*\\b(INSERT|DELETE|LOAD|CLEAR|CREATE|DROP|COPY|MOVE|ADD)\\b.*");
	}
	
	private OWLOntology getSelectedTargetOntology() {
		OWLOntology selectedOntology = (OWLOntology) targetOntologyCombo.getSelectedItem();
		return selectedOntology == null ? getOWLModelManager().getActiveOntology() : selectedOntology;
	}
	
	private void recordRecentQuery(String query) {
		String trimmedQuery = query.trim();
		if (trimmedQuery.length() == 0) {
			return;
		}
		for (int i = recentQueries.size() - 1; i >= 0; i--) {
			if (recentQueries.get(i).getQuery().trim().equals(trimmedQuery)) {
				recentQueries.remove(i);
			}
		}
		String name = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		recentQueries.add(0, new SavedSparqlQuery(name, trimmedQuery));
		while (recentQueries.size() > 15) {
			recentQueries.remove(recentQueries.size() - 1);
		}
		recentQueryStore.saveQueries(recentQueries);
		refreshRecentQueryModel();
	}
	
	private void undoLastUpdate() {
		if (reasoner == null || queryWorker != null) {
			return;
		}
		updateRunningState(true);
		statusLabel.setText("Undoing last SPARQL update...");
		queryWorker = new SwingWorker<SparqlResultSet, Void>() {
			@Override
			protected SparqlResultSet doInBackground() throws SparqlReasonerException {
				return reasoner.undoLastUpdate();
			}
			
			@Override
			protected void done() {
				try {
					if (!isCancelled()) {
						currentResult = get();
						resultModel.setResults(currentResult);
						statusLabel.setText("Undo complete");
					}
				}
				catch (Exception ex) {
					Throwable cause = ex.getCause() == null ? ex : ex.getCause();
					ErrorLogPanel.showErrorDialog(cause);
					JOptionPane.showMessageDialog(getOWLWorkspace(), cause.getMessage() + "\nSee the logs for more information.");
					statusLabel.setText("Undo failed");
				}
				finally {
					queryWorker = null;
					updateRunningState(false);
				}
			}
		};
		queryWorker.execute();
	}

	private void cancelQuery() {
		if (queryWorker != null) {
			queryWorker.cancel(true);
			statusLabel.setText("Cancelling query...");
		}
	}

	private void exportResults() {
		if (currentResult.getColumnCount() == 0) {
			JOptionPane.showMessageDialog(getOWLWorkspace(), "Run a query before exporting results.");
			return;
		}
		JFileChooser chooser = new JFileChooser();
		for (SparqlResultExporter.Format format : SparqlResultExporter.Format.values()) {
			chooser.addChoosableFileFilter(new FileNameExtensionFilter(format.toString(), format.getExtension()));
		}
		chooser.setFileFilter(chooser.getChoosableFileFilters()[1]);
		if (chooser.showSaveDialog(getOWLWorkspace()) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		SparqlResultExporter.Format format = selectedFormat(chooser);
		File file = chooser.getSelectedFile();
		try {
			resultExporter.export(currentResult, format, file);
			statusLabel.setText("Exported results as " + format.getLabel());
		}
		catch (IOException ex) {
			ErrorLogPanel.showErrorDialog(ex);
			JOptionPane.showMessageDialog(getOWLWorkspace(), ex.getMessage() + "\nSee the logs for more information.");
		}
	}

	private SparqlResultExporter.Format selectedFormat(JFileChooser chooser) {
		String description = chooser.getFileFilter().getDescription();
		for (SparqlResultExporter.Format format : SparqlResultExporter.Format.values()) {
			if (description.startsWith(format.getLabel())) {
				return format;
			}
		}
		return SparqlResultExporter.Format.CSV;
	}

	private void updateRunningState(boolean running) {
		runButton.setEnabled(!running);
		cancelButton.setEnabled(running);
		updateButtonState();
	}

	private void updateButtonState() {
		exportButton.setEnabled(currentResult.getColumnCount() > 0 && queryWorker == null);
		undoButton.setEnabled(reasoner != null && queryWorker == null);
	}

	private void startElapsedTimer() {
		elapsedTimer = new Timer(250, e -> {
			long elapsed = System.currentTimeMillis() - queryStartedAt;
			statusLabel.setText("Running query... " + elapsed + " ms");
		});
		elapsedTimer.start();
	}

	private void stopElapsedTimer() {
		if (elapsedTimer != null) {
			elapsedTimer.stop();
			elapsedTimer = null;
		}
	}

	private String createSampleQuery() {
		return PrefixDeclarations.create(getOWLModelManager()) + "\nSELECT ?subject ?object\nWHERE {\n  ?subject rdfs:subClassOf ?object\n}";
	}

	private void registerRunShortcut() {
		Action runAction = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				runQuery();
			}
		};
		queryPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), RUN_ACTION_KEY);
		queryPane.getActionMap().put(RUN_ACTION_KEY, runAction);
	}
	
	private void registerSaveCleaner() {
		ioListener = new IOListener() {
			@Override
			public void beforeSave(IOListenerEvent event) {
			}
			
			@Override
			public void afterSave(IOListenerEvent event) {
				try {
					turtleSaveCleaner.clean(event.getPhysicalURI());
				}
				catch (Exception e) {
					ErrorLogPanel.showErrorDialog(e);
				}
			}
			
			@Override
			public void beforeLoad(IOListenerEvent event) {
			}
			
			@Override
			public void afterLoad(IOListenerEvent event) {
			}
		};
		getOWLModelManager().addIOListener(ioListener);
	}

	@Override
	protected void disposeOWLView() {
		stopElapsedTimer();
		if (queryWorker != null) {
			queryWorker.cancel(true);
			queryWorker = null;
		}
		if (reasoner != null) {
			reasoner.dispose();
			reasoner = null;
		}
		if (ioListener != null) {
			getOWLModelManager().removeIOListener(ioListener);
			ioListener = null;
		}
	}
	
	private static class QueryTemplate {
		private final String name;
		private final String query;
		
		QueryTemplate(String name, String query) {
			this.name = name;
			this.query = query;
		}
		
		String getName() {
			return name;
		}
		
		String getQuery() {
			return query;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	
	private static class OntologyRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;
		
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof OWLOntology) {
				OWLOntology ontology = (OWLOntology) value;
				if (ontology.getOntologyID().getOntologyIRI().isPresent()) {
					IRI iri = ontology.getOntologyID().getOntologyIRI().get();
					setText(iri.getShortForm());
				}
				else {
					setText("Anonymous ontology");
				}
			}
			return this;
		}
	}
}
