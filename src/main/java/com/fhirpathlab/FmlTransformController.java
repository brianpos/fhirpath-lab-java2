package com.fhirpathlab;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fhirpathlab.utils.ParamUtils;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.hl7.fhir.r4b.model.Bundle;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.ValidatedFragment;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;

import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class FmlTransformController {

    private final ContextFactory contextFactory;
    private static final Logger logger = LoggerFactory.getLogger(FhirpathTestController.class);
    private org.hl7.fhir.r4b.formats.XmlParser xmlParser;
    private org.hl7.fhir.r4b.formats.JsonParser jsonParser;

    public FmlTransformController(ContextFactory contextFactory) {
        this.contextFactory = contextFactory;
        xmlParser = new org.hl7.fhir.r4b.formats.XmlParser();
        jsonParser = new org.hl7.fhir.r4b.formats.JsonParser();
    }

    /**
     * Handles POST requests for $transform requests.
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
        org.hl7.fhir.r4b.model.OperationOutcome outcome;
        outcome = new org.hl7.fhir.r4b.model.OperationOutcome();
        ParamUtils.add(responseParameters, "outcome", outcome);
        var resultPart = ParamUtils.add(responseParameters, "result");
        var paramsPart = ParamUtils.add(responseParameters, "parameters");
        ParamUtils.add(paramsPart, "evaluator", "Java 6.3.20 (r4b)");

        logger.info("Evaluating: fhir/$transform");

        // Determine the appropriate parser based on Content-Type header
        boolean inputFormatIsJson = false;
        org.hl7.fhir.r4b.formats.IParser parser;
        if (contentType != null && contentType.contains(MediaType.APPLICATION_XML_VALUE)) {
            parser = xmlParser;
            parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
        } else {
            parser = jsonParser; // Default to JSON parser
            parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
            inputFormatIsJson = true;
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

            // Create a new context for the call
            var context = contextFactory.getContextR5();
            var localContext = new org.hl7.fhir.r5.context.SimpleWorkerContext(context);
            readCustomStructureDefinitions(localContext, parameters);

            // Parse the map into a structureMap
            List<org.hl7.fhir.r5.model.Base> outputs = new ArrayList<>();
            var transformerServices = new TransformSupportServicesR5(localContext, outputs);
            var paramsTrace = ParamUtils.add(responseParameters, "trace");
            transformerServices.setTraceToParameter(paramsTrace);
            org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities smu = new org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities(
                    localContext, transformerServices);
            smu.setDebug(true);
            var map = smu.parse(fml, contentType);

            InputStream inputStream = new ByteArrayInputStream(resourceText.getBytes(StandardCharsets.UTF_8));
            try {
                if (resourceText.startsWith("{")) {
                    sourceResource = Manager.parseSingle(localContext, inputStream, FhirFormat.JSON);
                    inputFormatIsJson = true;
                } else {
                    sourceResource = Manager.parseSingle(localContext, inputStream, FhirFormat.XML);
                    inputFormatIsJson = false;
                }
            } catch (FHIRFormatError e) {
                // This isn't a native FHIR resource, so use a generic parser (which may be able
                // to handle the logical models)
                List<ValidatedFragment> fragments;
                if (resourceText.startsWith("{")) {
                    var rawJsonParser = new org.hl7.fhir.r5.elementmodel.JsonParser(localContext);
                    fragments = rawJsonParser.parse(inputStream);
                    inputFormatIsJson = true;
                } else {
                    var rawXmlParser = new org.hl7.fhir.r5.elementmodel.XmlParser(localContext);
                    fragments = rawXmlParser.parse(inputStream);
                    inputFormatIsJson = false;
                }
                if (fragments.size() == 1) {
                    sourceResource = fragments.get(0).getElement();
                } else {
                    throw new FHIRException("Unable to parse resource");
                }
            }

            // By using the local context here we are possibly permitting a logical model
            // be used as the target resource, not sure if that really should be
            // permitted...
            org.hl7.fhir.r5.elementmodel.Element target = getTargetResourceFromStructureMap(localContext, map);
            smu.transform(null, sourceResource, map, target);

            // convert the result back into json
            org.hl7.fhir.r5.elementmodel.ParserBase outputParser;
            if (inputFormatIsJson) {
                outputParser = new org.hl7.fhir.r5.elementmodel.JsonParser(localContext);
            } else {
                outputParser = new org.hl7.fhir.r5.elementmodel.XmlParser(localContext);
            }
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            outputParser.compose(target, boas, OutputStyle.PRETTY, null);
            var result = new String(boas.toByteArray());
            boas.close();
            resultPart.setValue(new org.hl7.fhir.r4b.model.StringType(result));

            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.INFORMATION)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.INFORMATIONAL)
                    .setDiagnostics("Transformation completed successfully");
            return new ResponseEntity<>(parser.composeString(responseParameters), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error processing $transform", e);
            outcome = new org.hl7.fhir.r4b.model.OperationOutcome();
            ParamUtils.add(responseParameters, "outcome", outcome);
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                    .setDiagnostics(e.getMessage());
            try {
                logger.error("Unknown Error processing $transform : result in outcome");
                return new ResponseEntity<>(parser.composeString(outcome), HttpStatus.BAD_REQUEST);
            } catch (Exception ep) {
                logger.error("Error reporting operationoutcome for $transform result", ep);
                return new ResponseEntity<>(ep.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private org.hl7.fhir.r5.elementmodel.Element getTargetResourceFromStructureMap(
            org.hl7.fhir.r5.context.SimpleWorkerContext context, StructureMap map) {
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
        List<Parameters.ParametersParameterComponent> models = operationParameters.getParameters("model");
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

            if (!sd.hasSnapshot()) {
                // Generate the snapshot
                StructureDefinition sdb = context.fetchResource(StructureDefinition.class, sd.getBaseDefinition());
                if (sdb == null)
                    throw new DefinitionException(context.formatMessage(I18nConstants.UNABLE_TO_FIND_BASE__FOR_,
                            sd.getBaseDefinition(), sd.getUrl()));
                if (sdb.getDerivation() == null) {
                    sdb.setDerivation(StructureDefinition.TypeDerivationRule.SPECIALIZATION);
                }
                List<ValidationMessage> messages = new ArrayList<>();
                org.hl7.fhir.r5.conformance.profile.ProfileUtilities pu = new org.hl7.fhir.r5.conformance.profile.ProfileUtilities(
                        context, messages, null);
                pu.setThrowException(false);
                pu.generateSnapshot(sd, sdb, (sd.hasWebPath()) ? Utilities.extractBaseUrl(sd.getWebPath()) : null,
                        sdb.getName(), null);
            }
            context.cacheResource(sd);
        }
        if (resource instanceof Bundle) {
            Bundle bundle = (Bundle) resource;
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource() != null) {
                    scanResource(context, entry.getResource());
                }
            }
        }
    }

}
