package org.protege.editor.owl.rdf;

import org.protege.editor.core.ui.error.ErrorLogPanel;
import org.protege.editor.owl.rdf.repository.BasicSparqlReasonerFactory;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
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
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

public class SparqlQueryView extends AbstractOWLViewComponent {
	private static final String RUN_ACTION_KEY = "run-sparql-query";

	private SparqlReasoner reasoner;
	private SavedSparqlQueryStore queryStore;
	private final JTextPane queryPane = new JTextPane();
	private final SwingResultModel resultModel = new SwingResultModel();
	private final SparqlResultExporter resultExporter = new SparqlResultExporter();
	private final DefaultComboBoxModel<SavedSparqlQuery> savedQueryModel = new DefaultComboBoxModel<SavedSparqlQuery>();
	private final JComboBox<SavedSparqlQuery> savedQueryCombo = new JComboBox<SavedSparqlQuery>(savedQueryModel);
	private final JLabel statusLabel = new JLabel("Ready");
	private final JButton runButton = new JButton("Run");
	private final JButton cancelButton = new JButton("Cancel");
	private final JButton exportButton = new JButton("Export...");
	private final List<SavedSparqlQuery> savedQueries = new ArrayList<SavedSparqlQuery>();

	private SwingWorker<SparqlResultSet, Void> queryWorker;
	private Timer elapsedTimer;
	private long queryStartedAt;
	private SparqlResultSet currentResult = new SparqlResultSet(new ArrayList<String>());
	private boolean loadingSavedQuery;

	@Override
	protected void initialiseOWLView() {
		queryStore = new SavedSparqlQueryStore(Preferences.userNodeForPackage(SparqlQueryView.class).node("savedQueries"));
		initializeReasoner();
		setLayout(new BorderLayout());
		add(createToolbar(), BorderLayout.NORTH);
		add(createCenterComponent(), BorderLayout.CENTER);
		add(createStatusComponent(), BorderLayout.SOUTH);
		loadSavedQueries();
		setInitialQuery();
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
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		savedQueryCombo.setPrototypeDisplayValue(new SavedSparqlQuery("Saved queries", ""));
		savedQueryCombo.addActionListener(e -> loadSelectedSavedQuery());
		panel.add(new JLabel("Query:"));
		panel.add(savedQueryCombo);
		panel.add(new JButton(newQueryAction()));
		panel.add(new JButton(saveQueryAction()));
		panel.add(new JButton(deleteQueryAction()));
		panel.add(new JButton(insertPrefixesAction()));
		runButton.addActionListener(e -> runQuery());
		cancelButton.addActionListener(e -> cancelQuery());
		cancelButton.setEnabled(false);
		exportButton.addActionListener(e -> exportResults());
		panel.add(runButton);
		panel.add(cancelButton);
		panel.add(exportButton);
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

	private void loadSavedQueries() {
		savedQueries.clear();
		savedQueries.addAll(queryStore.loadQueries());
		refreshSavedQueryModel();
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

	private void runQuery() {
		if (reasoner == null || queryWorker != null) {
			return;
		}
		queryStartedAt = System.currentTimeMillis();
		statusLabel.setText("Running query...");
		updateRunningState(true);
		startElapsedTimer();
		final String query = queryPane.getText();
		queryWorker = new SwingWorker<SparqlResultSet, Void>() {
			@Override
			protected SparqlResultSet doInBackground() throws SparqlReasonerException {
				return reasoner.executeQuery(query);
			}

			@Override
			protected void done() {
				stopElapsedTimer();
				try {
					if (!isCancelled()) {
						currentResult = get();
						resultModel.setResults(currentResult);
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
	}
}
