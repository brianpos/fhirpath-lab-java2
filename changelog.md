# fhirpath-lab-java2

## Changelog

### 30 October 2024
* Update to org.hl7.fhir v6.4.0
* Correct bug in running the Fhirpath engine where the "resource" context was not being set correctly.

### 16 September 2024
* Update to org.hl7.fhir v6.3.25 (now that we can just adopt things this quickly)
* resolve bug with generating snapshots which multiple logical models (my code)

### 28 August 2024
* Update to org.hl7.fhir v6.3.21 (now that we can just adopt things this quickly)

### 15 August 2024
* New implementation! 
* Now uses the core HL7 FHIRPath engine without the HAPI libraries
* Using org.hl7.fhir v6.3.20

### 18 May 2024
* Update to the HAPI 7.2.0 engine

### 2 November 2023
* Update to the HAPI 6.8.5 engine
* Add support for the FHIR Mapping Engine
* Share the worker context property more widely (more efficient use of memory)

### 7 September 2023
* Correct null reference exception when processing variables that have no value provided

### 21 August 2023
* Update to the HAPI 6.8.0 engine
* Use the newly added capacity to serialize fragments to JSON so that returning backbone elements is now possible
