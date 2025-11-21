# fhirpath-lab-java2

## Changelog

### 25 August 2025
* Update to org.hl7.fhir v6.7.7
* Update several other dependency projects to the latest versions

### 25 August 2025
* Update to org.hl7.fhir v6.6.5

### 8 August 2025
* debug tracing will include the value if it is primitive (even when it has a source in the resource path)

### 7 August 2025
* Update to org.hl7.fhir v6.6.3

### 29 July 2025
* add support for R6 ballot3 (using the R5 element model with R6 packaged structuredefinitions)

### 24 July 2025
* Update to org.hl7.fhir v6.6.2

### 12 July 2025
* Update to org.hl7.fhir v6.5.28
* remove the POC debug_trace custom function now proper tracing capabilities exist

### 16 June 2025
* New Debug Tracer for fhirpath evaluations!
* Update to org.hl7.fhir v6.5.27

### 16 June 2025
* Update to org.hl7.fhir v6.5.26

### 10 June 2025
* Update to org.hl7.fhir v6.5.24
* POC debug tracer introduced

### 3 June 2025
* Update to org.hl7.fhir v6.5.22

### 27 April 2025
* Update to org.hl7.fhir v6.5.20

### 22 April 2025
* Update to org.hl7.fhir v6.5.19
* Pass through the line numbering information from the expression (as is now mostly fixed in the updated version of the library)

### 15 April 2025
* Update to org.hl7.fhir v6.5.18
* Include support for checking the datatypes and returning them in the AST output
* return an operationoutcome in the debug output so that it's displayed even when the expression can be parsed.
   (e.g. Patient.name.families - can be parsed, just not evaluated)

### 30 October 2024
* Update to org.hl7.fhir v6.4.0
* Correct bug in running the Fhirpath engine where the "resource" context was not being set correctly.
* enhance the error reporting

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
