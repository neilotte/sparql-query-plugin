# Protégé SPARQL Workspace Plugin

## Install

1. Download these files from the GitHub release:
   - `sparql-query-plugin-3.0.0.jar`
   - `rdf-library-3.0.0.jar`
2. Quit Protégé if it is running.
3. Copy both jar files into the Protégé plugins directory:
   - macOS app bundle: `Protégé.app/Contents/bundles`
   - Other installs: the `bundles` directory inside the Protégé installation
4. Start Protégé.
5. Open the SPARQL view from the Protégé tabs/views menu.

## What changed

- Saved query library and recent query history.
- Query templates for common SELECT, CONSTRUCT, INSERT, and DELETE workflows.
- Prefix insertion from currently loaded ontologies.
- Fast run/cancel controls and export to CSV, TSV, JSON, XML, and text.
- SPARQL Update support for INSERT and DELETE against a selected target ontology.
- Update preview confirmation and one-step undo for the last applied update.
- Direct OWL axiom conversion for common update triples, reducing unnecessary parser-driven rewrites.
- Turtle saves prefer the plain Rio Turtle writer after SPARQL updates, which avoids the OWLAPI comment banners and generated-by footer produced by the classic Turtle renderer.

## Notes

The direct update converter handles common ontology edits such as declarations, subclass axioms, labels/comments, domains, ranges, class assertions, and object/data property assertions. More complex RDF, such as blank-node class expressions, still falls back to OWLAPI RDF parsing and is reported in the update preview as fallback triples.
