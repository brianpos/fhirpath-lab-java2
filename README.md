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

### Debugging with local changes to the core HAPI libs (when testing changes to the engines)
Download the HAPI source code and build it with the `install` goal.
The *secret sauce* here is that the way the project manages its source is that it always sets a `snapshot` version in the POM that means it will have a different number to the publicly released ones.
Build and install that project using the command:
```
mvn clean install `-Dmaven.test.skip=true
```
Then update this project's pom.xml file with the version number of the snapshot you just built.
This should then let everything just work and permit you to debug the changes you are making to the core HAPI libraries!
