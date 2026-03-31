---
name: upgrade-hapi-core
description: 'Upgrade the HAPI FHIR core dependency (org.hl7.fhir) to the latest version. USE FOR: bumping hapifhir.version in pom.xml, updating version strings in controllers and test data, updating the changelog, verifying compilation and tests pass. DO NOT USE FOR: adding new features, refactoring code, or updating test output data beyond version stamps.'
argument-hint: 'Target version number, e.g. 6.10.0'
---

# Upgrade HAPI FHIR Core Dependency

## When to Use
- A new version of `org.hl7.fhir` has been published and needs to be adopted.
- The user asks to upgrade the HAPI core / FHIR core dependency.

## Prerequisites
- Java and Maven must be installed and available on PATH.
- The project must compile and tests must pass before starting the upgrade.

## Procedure

### 1. Determine the Target Version

If no version is specified, look up the latest release version from Maven Central:
- URL: https://mvnrepository.com/artifact/ca.uhn.hapi.fhir/org.hl7.fhir.r4
- Find the latest non-snapshot version number (e.g. `6.10.0`)

### 2. Identify the Current Version

Read the `<hapifhir.version>` property in `pom.xml` (around line 12). This is the **old version** that will be replaced everywhere. Note it for search-and-replace operations.

### 3. Update `pom.xml`

In `pom.xml`, update the `<hapifhir.version>` property value from the old version to the new version:

```xml
<properties>
    <hapifhir.version>NEW_VERSION</hapifhir.version>
</properties>
```

### 4. Update `changelog.md`

Add a new entry at the top of the changelog (after the `## Changelog` heading) with today's date and the new version:

```markdown
### DD Month YYYY
* Update to org.hl7.fhir vNEW_VERSION
```

Use the format: day (no leading zero), full month name, four-digit year.

### 5. Update Version Strings in Controllers

Two controller files contain hardcoded version strings that must be updated:

**`src/main/java/com/fhirpathlab/FhirpathTestController.java`**
- Search for `"Java OLD_VERSION ("` in the `ParamUtils.add(paramsPart, "evaluator", ...)` call
- Replace with `"Java NEW_VERSION ("`

**`src/main/java/com/fhirpathlab/FmlTransformController.java`**
- Search for `"Java OLD_VERSION ("` in the `ParamUtils.add(paramsPart, "evaluator", ...)` call
- Replace with `"Java NEW_VERSION ("`

### 6. Update Test Data Version Stamps

Three test data files contain version strings that must match the controllers:

**`src/test/data/simple.response.json`**
- Search for `"Java OLD_VERSION ("` in the `evaluator` valueString
- Replace with `"Java NEW_VERSION ("`

**`src/test/data/transform.response.json`**
- Search for `"Java OLD_VERSION ("` in the `evaluator` valueString
- Replace with `"Java NEW_VERSION ("`

**`src/test/data/transform.response.actual.json`**
- Search for `"Java OLD_VERSION ("` in the `evaluator` valueString
- Replace with `"Java NEW_VERSION ("`

### 7. Compile and Verify

Run the build:

```bash
mvn clean install
```

**If compilation succeeds and all tests pass** — the upgrade is complete.

**If compilation fails** — the HAPI core library may have changed interfaces that this project implements. Common fixes include:

- New methods added to interfaces (e.g. `IHostApplicationServices`, `IEvaluationContext`) — add implementations for the new methods. Check the interface definition in the HAPI source to determine the correct return type and behavior. A safe default is often to return `null` or throw `UnsupportedOperationException`.
- Method signature changes — update the method signatures in the implementing classes to match.
- Deprecated API removals — replace with the recommended alternative.

Fix only the compilation issues. Do **not** refactor unrelated code or make improvements beyond what is needed to compile.

### 8. Re-run Tests After Fixes

After fixing any compilation issues, run the build again:

```bash
mvn clean install
```

If tests fail due to version string mismatches, double-check that all version stamps from steps 5 and 6 were updated consistently.

Do **not** update test output data files (e.g. `*.hapi.json`, `*.hapi2.json`, `*.lab.json`, `*.lab2.json` in `src/test/test-data/`) — only the version stamp fields listed in step 6 should be modified.

## Files Changed (Summary)

| File | Change |
|------|--------|
| `pom.xml` | `hapifhir.version` property |
| `changelog.md` | New date + version entry at top |
| `src/main/java/com/fhirpathlab/FhirpathTestController.java` | Evaluator version string |
| `src/main/java/com/fhirpathlab/FmlTransformController.java` | Evaluator version string |
| `src/test/data/simple.response.json` | Evaluator version stamp |
| `src/test/data/transform.response.json` | Evaluator version stamp |
| `src/test/data/transform.response.actual.json` | Evaluator version stamp |
| `src/main/java/com/fhirpathlab/*.java` | Only if HAPI interface changes require compilation fixes |
