package com.fhirpathlab;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.hl7.fhir.r4b.model.Bundle;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;

import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.exceptions.FHIRException;

import java.util.List;
import java.util.ArrayList;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/fhir")
@CrossOrigin(origins = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT }) // Replace with your
                                                                                                    // client origin
public class FhirpathLabTransformController {

    private final FhirpathLabSimpleWorkerContextR5 context;
    private org.hl7.fhir.r4b.formats.XmlParser xmlParser;
    private org.hl7.fhir.r4b.formats.JsonParser jsonParser;

    public FhirpathLabTransformController(FhirpathLabSimpleWorkerContextR5 context) {
        this.context = context;
        xmlParser = new org.hl7.fhir.r4b.formats.XmlParser();
        jsonParser = new org.hl7.fhir.r4b.formats.JsonParser();
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
    @PostMapping(value = "/$transform", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE,
            "application/fhir+json;fhirVersion=4.0" })
    public ResponseEntity<String> evaluateFmlTransform(@RequestBody String content,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        var responseParameters = new org.hl7.fhir.r4b.model.Parameters();
        responseParameters.setId("map");
        var paramsTrace = ParamUtils.add(responseParameters, "trace");
        var paramsPart = ParamUtils.add(responseParameters, "parameters");
        ParamUtils.add(paramsPart, "evaluator", "Java 6.3.20 (r4b)");
        org.hl7.fhir.r4b.model.OperationOutcome outcome;

        // Determine the appropriate parser based on Content-Type header
        org.hl7.fhir.r4b.formats.IParser parser;
        if (contentType != null && contentType.contains(MediaType.APPLICATION_XML_VALUE)) {
            parser = xmlParser;
            parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
        } else {
            parser = jsonParser; // Default to JSON parser
            parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
        }

        try {
            // Parse the input content into a Patient resource
            var parameters = (org.hl7.fhir.r4b.model.Parameters) parser.parse(content);

            // read the `map` named parameter from the valueString
            var fml = parameters.getParameter("map").getValue().primitiveValue();
            ParamUtils.add(paramsPart, "map", fml);

            // read the `resource` named parameter
            org.hl7.fhir.r5.elementmodel.Element sourceResource = null;
            String resourceText = null;
            var resource = parameters.getParameter("resource").getResource();
            if (resource != null) {
                resourceText = jsonParser.composeString(resource);
            } else {
                var pv = parameters.getParameterValue("resource");
                if (pv instanceof StringType) {
                    resourceText = pv.primitiveValue().trim();
                }
            }
            if (resourceText.startsWith("{")) {
                InputStream inputStream = new ByteArrayInputStream(resourceText.getBytes(StandardCharsets.UTF_8));
                sourceResource = Manager.parseSingle((context), inputStream, FhirFormat.JSON);
            } else {
                InputStream inputStream = new ByteArrayInputStream(resourceText.getBytes(StandardCharsets.UTF_8));
                sourceResource = Manager.parseSingle((context), inputStream, FhirFormat.XML);
            }

            // Create a new context for the call
            var localContext = new org.hl7.fhir.r5.context.SimpleWorkerContext(context);
            readCustomStructureDefinitions(localContext, parameters);

            // Parse the map into a structureMap
            List<org.hl7.fhir.r5.model.Base> outputs = new ArrayList<>();
            var transformerServices = new TransformSupportServicesR5(localContext, outputs);
            transformerServices.traceToParameter = paramsTrace;
            org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities smu = new org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities(
                    localContext, transformerServices);
            smu.setDebug(true);
            var map = smu.parse(fml, contentType);

            org.hl7.fhir.r5.elementmodel.Element target = getTargetResourceFromStructureMap(map);
            smu.transform(null, sourceResource, map, target);

            // convert the result back into json
            var outputParser = new org.hl7.fhir.r5.elementmodel.JsonParser(localContext);
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            outputParser.compose(target, boas, OutputStyle.PRETTY, null);
            var result = new String(boas.toByteArray());
            boas.close();
            var resultPart = ParamUtils.add(responseParameters, "result");
            resultPart.setValue(new org.hl7.fhir.r4b.model.StringType(result));

            outcome = new org.hl7.fhir.r4b.model.OperationOutcome();
            ParamUtils.add(responseParameters, "outcome", outcome);
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.INFORMATION)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.INFORMATIONAL)
                    .setDiagnostics("Transformation completed successfully");
            return new ResponseEntity<>(parser.composeString(responseParameters), HttpStatus.OK);
        } catch (Exception e) {
            outcome = new org.hl7.fhir.r4b.model.OperationOutcome();
            ParamUtils.add(responseParameters, "outcome", outcome);
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                    .setDiagnostics(e.getMessage());
            try {
                return new ResponseEntity<>(parser.composeString(outcome), HttpStatus.BAD_REQUEST);
            } catch (Exception ep) {
                return new ResponseEntity<>(ep.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
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

        if (targetTypeUrl == null) {
            throw new FHIRException("Unable to determine resource URL for target type");
        }

        StructureDefinition structureDefinition = context.fetchResource(StructureDefinition.class, targetTypeUrl);

        if (structureDefinition == null) {
            throw new FHIRException("Unable to find StructureDefinition for target type ('" + targetTypeUrl + "')");
        }

        return Manager.build(context, structureDefinition);
    }

    private void readCustomStructureDefinitions(org.hl7.fhir.r5.context.SimpleWorkerContext context,
            Parameters operationParameters) {
        List<Parameters.ParametersParameterComponent> models = operationParameters.getParameter();
        for (Parameters.ParametersParameterComponent model : models) {
            // VersionConvertorFactory_43_50.
            scanResource(context, model.getResource());

            // If there is no resource, but content in the string, assume it's the raw SD
            // content
            try {

                if (model.getValue() instanceof StringType) {
                    StringType str = (StringType) model.getValue();
                    if (str.getValue().startsWith("<")) {
                        org.hl7.fhir.r4b.model.Resource r = xmlParser.parse(str.getValue());
                        scanResource(context, r);
                    } else {
                        org.hl7.fhir.r4b.model.Resource r = jsonParser.parse(str.getValue());
                        scanResource(context, r);
                    }
                }
            } catch (IOException ex) {
                // ignore
            }
        }
    }

    private void scanResource(org.hl7.fhir.r5.context.SimpleWorkerContext context,
            org.hl7.fhir.r4b.model.Resource resource) {
        if (resource instanceof org.hl7.fhir.r4b.model.StructureDefinition) {
            var sd = (org.hl7.fhir.r5.model.StructureDefinition) VersionConvertorFactory_43_50
                    .convertResource(resource);
            context.cacheResource(sd);
        }
        if (resource instanceof Bundle) {
            Bundle bundle = (Bundle) resource;
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource() instanceof org.hl7.fhir.r4b.model.StructureDefinition) {
                    var sd = (org.hl7.fhir.r5.model.StructureDefinition) VersionConvertorFactory_43_50
                            .convertResource(entry.getResource());
                    context.cacheResource(sd);
                }
            }
        }
    }

}
