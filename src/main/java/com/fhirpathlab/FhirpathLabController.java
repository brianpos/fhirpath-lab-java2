package com.fhirpathlab;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

import org.hl7.fhir.r4b.model.Patient;

@RestController
@RequestMapping("/fhir")
@CrossOrigin(origins = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT }) // Replace with your
                                                                                                    // client origin
public class FhirpathLabController {

    private final FhirpathLabSimpleWorkerContextR4B context;

    public FhirpathLabController(FhirpathLabSimpleWorkerContextR4B context)
    {
        this.context = context;
    }

    /**
     * Handles PUT requests for FHIR Patient resources.
     *
     * This method parses the incoming content based on its type (JSON or XML),
     * converts it to a FHIR Patient object, and performs necessary operations.
     *
     * @param content     The string content of the FHIR Patient resource.
     * @param contentType The MIME type of the content.
     * @return A confirmation message with the patient ID or an error message.
     * @throws Exception if the content cannot be parsed correctly.
     */
    @PostMapping(value = "/$fhirpath", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE,
        "application/fhir+json;fhirVersion=4.0" })
    public String evaluateFhirPath(@RequestBody String content, @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        try {
            // Determine the appropriate parser based on Content-Type header
            org.hl7.fhir.r4b.formats.IParser parser;
            if (contentType != null && contentType.contains(MediaType.APPLICATION_XML_VALUE)) {
                parser = new org.hl7.fhir.r4b.formats.XmlParser();
                parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
            } else {
                parser = new org.hl7.fhir.r4b.formats.JsonParser(); // Default to JSON parser
                parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
            }

            // Parse the input content into a Patient resource
            var parameters = (org.hl7.fhir.r4b.model.Parameters) parser.parse(content);
            parameters.setId("1");

            var resultPart = parameters.addParameter();
            resultPart.setName("result");

            var engine = new org.hl7.fhir.r4b.fhirpath.FHIRPathEngine(context);

            FHIRPathTestEvaluationServices services = new FHIRPathTestEvaluationServices(resultPart);
            engine.setHostServices(services);
            var result = engine.evaluate(parameters, "descendants().trace('prop').id");

            // Perform any operations with the patient object here
            // For demonstration, just return the patient id
            return parser.composeString(parameters);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
