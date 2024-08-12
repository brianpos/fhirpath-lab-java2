package com.fhirpathlab;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.springframework.http.HttpHeaders;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.ArrayList;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/fhir")
@CrossOrigin(origins = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT }) // Replace with your client origin
public class FhirpathLabTransformController {

    private final FhirpathLabSimpleWorkerContextR5 context;

    public FhirpathLabTransformController(FhirpathLabSimpleWorkerContextR5 context) {
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
    @PostMapping(value = "/$transform", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE, "application/fhir+json;fhirVersion=4.0" })
    public String evaluateFhirPath(@RequestBody String content,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        var responseParameters = new org.hl7.fhir.r4b.model.Parameters();
        responseParameters.setId("map");
        var paramsTrace = ParamUtils.add(responseParameters, "trace");
        var paramsPart = ParamUtils.add(responseParameters, "parameters");
        ParamUtils.addPart(paramsPart, "evaluator", "Java 6.3.19 (r4b)");

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

            // read the `map` named parameter from the valueString
            var fml = parameters.getParameter("map").getValue().primitiveValue();
            ParamUtils.addPart(paramsPart, "map", fml);

            // read the `resource` named parameter
            var resource = parameters.getParameter("resource").getResource();
            var resourceJson = parser.composeString(resource);
            InputStream inputStream = new ByteArrayInputStream(resourceJson.getBytes(StandardCharsets.UTF_8));
            org.hl7.fhir.r5.elementmodel.Element sourceResource = Manager.parseSingle((context), inputStream,
                    FhirFormat.JSON);

            // Parse the map into a structureMap
            List<org.hl7.fhir.r5.model.Base> outputs = new ArrayList<>();
            var transformerServices = new TransformSupportServicesR5(context, outputs);
            org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities smu = new org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities(
                    context, transformerServices);
            var map = smu.parse(fml, contentType);

            org.hl7.fhir.r5.elementmodel.Element target = getTargetResourceFromStructureMap(map);
            smu.transform(null, sourceResource, map, target);

            // convert the result back into json
            var outputParser = new org.hl7.fhir.r5.elementmodel.JsonParser(context);
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            outputParser.compose(target, boas, OutputStyle.PRETTY, null);
            var result = new String(boas.toByteArray());
            boas.close();
            var resultPart = ParamUtils.add(responseParameters, "result");
            resultPart.setValue(new org.hl7.fhir.r4b.model.StringType(result));

            var outcome = new org.hl7.fhir.r4b.model.OperationOutcome();
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.INFORMATION)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.INFORMATIONAL)
                    .setDiagnostics("Transformation completed successfully");
            ParamUtils.add(responseParameters, "outcome", outcome);
            return parser.composeString(responseParameters);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private org.hl7.fhir.r5.elementmodel.Element getTargetResourceFromStructureMap(StructureMap map) {
        String targetTypeUrl = null;
        for (StructureMap.StructureMapStructureComponent component : map.getStructure()) {
            if (component.getMode() == StructureMap.StructureMapModelMode.TARGET) {
                targetTypeUrl = component.getUrl();
                break;
            }
        }

        // if (targetTypeUrl == null) {
        // log.error("Unable to determine resource URL for target type");
        // throw new FHIRException("Unable to determine resource URL for target type");
        // }

        StructureDefinition structureDefinition = context.fetchResource(StructureDefinition.class, targetTypeUrl);
        // for (StructureDefinition sd :
        // _ctx.fetchResourcesByType(StructureDefinition.class)) {
        // if (sd.getUrl().equalsIgnoreCase(targetTypeUrl)) {
        // structureDefinition = sd;
        // break;
        // }
        // }

        // if (structureDefinition == null) {
        // log.error("Unable to find StructureDefinition for target type ('" +
        // targetTypeUrl + "')");
        // throw new FHIRException("Unable to find StructureDefinition for target type
        // ('" + targetTypeUrl + "')");
        // }

        return Manager.build(context, structureDefinition);
    }

}
