package org.protege.editor.owl.rdf;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SparqlResultExporter {
	public enum Format {
		CSV("CSV", "csv"),
		TSV("TSV", "tsv"),
		JSON("JSON", "json"),
		XML("XML", "xml"),
		TEXT("Text", "txt");

		private final String label;
		private final String extension;

		Format(String label, String extension) {
			this.label = label;
			this.extension = extension;
		}

		public String getLabel() {
			return label;
		}

		public String getExtension() {
			return extension;
		}

		@Override
		public String toString() {
			return label + " (*." + extension + ")";
		}
	}

	public void export(SparqlResultSet resultSet, Format format, File file) throws IOException {
		File target = withExtension(file, format.getExtension());
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8));
		try {
			switch (format) {
				case CSV:
					writeDelimited(resultSet, writer, ',');
					break;
				case TSV:
					writeDelimited(resultSet, writer, '\t');
					break;
				case JSON:
					writeJson(resultSet, writer);
					break;
				case XML:
					writeXml(resultSet, writer);
					break;
				case TEXT:
					writeDelimited(resultSet, writer, '\t');
					break;
				default:
					throw new IllegalArgumentException("Unsupported export format: " + format);
			}
		}
		finally {
			writer.close();
		}
	}

	private File withExtension(File file, String extension) {
		String name = file.getName();
		if (name.toLowerCase().endsWith("." + extension)) {
			return file;
		}
		return new File(file.getParentFile(), name + "." + extension);
	}

	private void writeDelimited(SparqlResultSet resultSet, BufferedWriter writer, char delimiter) throws IOException {
		writeDelimitedRow(resultSet.getColumnNames(), writer, delimiter);
		for (List<Object> row : resultSet.getRows()) {
			for (int i = 0; i < row.size(); i++) {
				if (i > 0) {
					writer.write(delimiter);
				}
				writer.write(escapeDelimited(value(row.get(i)), delimiter));
			}
			writer.newLine();
		}
	}

	private void writeDelimitedRow(List<String> values, BufferedWriter writer, char delimiter) throws IOException {
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				writer.write(delimiter);
			}
			writer.write(escapeDelimited(values.get(i), delimiter));
		}
		writer.newLine();
	}

	private String escapeDelimited(String value, char delimiter) {
		if (value == null) {
			return "";
		}
		boolean quote = value.indexOf(delimiter) >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
		if (!quote) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private void writeJson(SparqlResultSet resultSet, BufferedWriter writer) throws IOException {
		writer.write("[");
		writer.newLine();
		for (int rowIndex = 0; rowIndex < resultSet.getRowCount(); rowIndex++) {
			if (rowIndex > 0) {
				writer.write(",");
				writer.newLine();
			}
			writer.write("  {");
			for (int columnIndex = 0; columnIndex < resultSet.getColumnCount(); columnIndex++) {
				if (columnIndex > 0) {
					writer.write(",");
				}
				writer.newLine();
				writer.write("    \"");
				writer.write(escapeJson(resultSet.getColumnName(columnIndex)));
				writer.write("\": \"");
				writer.write(escapeJson(value(resultSet.getResult(rowIndex, columnIndex))));
				writer.write("\"");
			}
			writer.newLine();
			writer.write("  }");
		}
		writer.newLine();
		writer.write("]");
		writer.newLine();
	}

	private String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			switch (ch) {
				case '"':
					builder.append("\\\"");
					break;
				case '\\':
					builder.append("\\\\");
					break;
				case '\b':
					builder.append("\\b");
					break;
				case '\f':
					builder.append("\\f");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '\t':
					builder.append("\\t");
					break;
				default:
					if (ch < 32) {
						builder.append(String.format("\\u%04x", (int) ch));
					}
					else {
						builder.append(ch);
					}
			}
		}
		return builder.toString();
	}

	private void writeXml(SparqlResultSet resultSet, BufferedWriter writer) throws IOException {
		writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		writer.newLine();
		writer.write("<sparql-results>");
		writer.newLine();
		for (int rowIndex = 0; rowIndex < resultSet.getRowCount(); rowIndex++) {
			writer.write("  <result>");
			writer.newLine();
			for (int columnIndex = 0; columnIndex < resultSet.getColumnCount(); columnIndex++) {
				writer.write("    <binding name=\"");
				writer.write(escapeXml(resultSet.getColumnName(columnIndex)));
				writer.write("\">");
				writer.write(escapeXml(value(resultSet.getResult(rowIndex, columnIndex))));
				writer.write("</binding>");
				writer.newLine();
			}
			writer.write("  </result>");
			writer.newLine();
		}
		writer.write("</sparql-results>");
		writer.newLine();
	}

	private String escapeXml(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
	}

	private String value(Object value) {
		return value == null ? "" : value.toString();
	}
}
