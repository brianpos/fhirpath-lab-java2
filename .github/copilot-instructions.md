# Copilot Instructions for fhirpath-lab-java2

## Project Architecture
- Java server implementing HL7 FHIRPath and FML (FHIR Mapping Language) engines.
- Main code in `src/main/java/com/fhirpathlab/`.
- Uses `org.hl7.fhir.r4b.elementmodel` for flexible serialization and multi-version FHIR content.
- Web interface entry: `src/main/webapp/index.jsp` and `WEB-INF/web.xml`.
- Test data and cases in `test/data/` and `test/test-data/`.

## Build & Developer Workflow
- Standard build: `mvn clean install` (see README).
- For debugging with local HAPI changes:
  1. Build HAPI source with `mvn clean install -Dmaven.test.skip=true`.
  2. Update this project's `pom.xml` to use the new HAPI snapshot version.
- Output artifacts and compiled classes in `target/`.

## Key Patterns & Conventions
- FHIRPath and FML logic is separated for clarity; see main package for core engine classes.
- Uses snapshot versions for HAPI dependencies to allow local debugging and rapid iteration.
- Test data is organized by feature and format (e.g., `.hapi.json`, `.lab.json`).
- FHIR resource handling is version-agnostic via elementmodel, not model classes.

## Integration Points
- Relies on HAPI FHIR libraries (see `pom.xml`).
- External XML resource: `src/main/resources/ucum-essence.xml` for UCUM units.
- Webapp is Java/JSP-based, not Spring Boot or REST by default.

## Examples
- To add a new FHIRPath test, place input/output JSON in `test/data/` and reference in test code under `test/java/com/fhirpathlab/`.
- To update HAPI dependency, change the version in `pom.xml` and rebuild.

## Tips for AI Agents
- Always check for version-specific logic in engine classes.
- When debugging, ensure the correct HAPI snapshot is referenced.
- Use provided test data for regression and feature validation.
- Reference the README for build and debug instructions.

---
Feedback welcome: Please suggest improvements or clarify unclear sections for better agent productivity.
