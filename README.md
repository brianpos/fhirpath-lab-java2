# fhirpath-lab-java2

## Overview
Java server implementation of the HL7 fhirpath and FML (fhir mapping language) engines.
This project is a part of the [fhirpath-lab](https://github.com/brianpos/fhirpath-lab) project.

This project is a continuation from the old project [fhirpath-lab-java](https://github.com/brianpos/fhirpath-lab-java) which will be retired once this project has been running for a while.

Internally it uses the `org.hl7.fhir.r4b.elementmodel` classes rather than the `org.hl7.fhir.r4b.model` classes in order to be able to utilize the serialization to json fragments as is required in the FHIRPath testing, and also handle random multi-version content required for the FML logical model processing.

## Building the project
Build it with
```
$ mvn clean install
```
