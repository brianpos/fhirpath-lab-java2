package com.fhirpathlab;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

import org.hl7.fhir.r4.model.Patient;

@RestController
@RequestMapping("/api")
public class FhirpathLabController {
    @GetMapping("/hello")
    public String hello() {
        var patient = new Patient();
        patient.setId("pat1");
        var jsonParser = new org.hl7.fhir.r4.formats.JsonParser();
        try{
            var json = jsonParser.composeString(patient);
            return "Hello, FHIRPath Lab!" + json;
        }
        catch(Exception e){
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Handles PUT requests for FHIR Patient resources.
     *
     * This method parses the incoming content based on its type (JSON or XML),
     * converts it to a FHIR Patient object, and performs necessary operations.
     *
     * @param content The string content of the FHIR Patient resource.
     * @param contentType The MIME type of the content.
     * @return A confirmation message with the patient ID or an error message.
     * @throws Exception if the content cannot be parsed correctly.
     */
    @PutMapping(value = "/patient", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public String putPatient(@RequestBody String content, @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        try {
            // Determine the appropriate parser based on Content-Type header
            org.hl7.fhir.r4.formats.IParser parser;
            if (contentType != null && contentType.contains(MediaType.APPLICATION_XML_VALUE)) {
                parser = new org.hl7.fhir.r4.formats.XmlParser();
            } else {
                parser = new org.hl7.fhir.r4.formats.JsonParser(); // Default to JSON parser
            }

            // Parse the input content into a Patient resource
            Patient patient = (Patient)parser.parse(content);

            // Perform any operations with the patient object here
            // For demonstration, just return the patient id
            return "Received patient with ID: " + patient.getId();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }    }
}
