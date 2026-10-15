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
import org.hl7.fhir.r4b.model.Binary;
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
import java.util.Locale;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/fhir")
@CrossOrigin(origins = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT }) // Replace with your
                                                                                                    // client origin
public class FmlTransformController {

    private static final String OPERATION_OUTCOME_FILE_EXTENSION =
            "http://hl7.org/fhir/StructureDefinition/operationoutcome-file";
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
     * Parses JSON or XML Parameters and transforms the supplied content using FML maps.
     *
     * @param content     The serialized Parameters resource.
     * @param contentType The MIME type of the request.
     * @return The transformation results or an OperationOutcome describing the error.
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
        ParamUtils.add(paramsPart, "evaluator", "Java 7.0.1 (r4b)");
        String primaryMapFile = null;
        String errorMapFile = null;

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
            var parameters = (org.hl7.fhir.r4b.model.Parameters) parser.parse(content);

            List<Parameters.ParametersParameterComponent> mapParameters = new ArrayList<>();
            List<Parameters.ParametersParameterComponent> contentParameters = new ArrayList<>();
            for (Parameters.ParametersParameterComponent parameter : parameters.getParameter()) {
                String name = parameter.getName();
                if ("source".equals(name) || "sourceMap".equals(name) || "supportingMap".equals(name)) {
                    throw new FHIRException("This implementation does not support the standard parameter '" + name + "'");
                }
                if ("map".equals(name) || "srcMap".equals(name)) {
                    mapParameters.add(parameter);
                }
                if ("content".equals(name) || "resource".equals(name)) {
                    contentParameters.add(parameter);
                }
            }

            if (contentParameters.isEmpty()) {
                throw new FHIRException("Missing content or resource parameter");
            }
            if (contentParameters.size() != 1) {
                throw new FHIRException("Provide exactly one content or resource parameter");
            }

            // Create a new context for the call
            var context = contextFactory.getContextR4bAsR5();
            var localContext = new org.hl7.fhir.r5.context.SimpleWorkerContext(context);
            readCustomStructureDefinitions(localContext, parameters);

            // Parse map and srcMap parameters in request order. The first map is the
            // transform entry point and the remaining maps are available to resolve imports.
            List<org.hl7.fhir.r5.model.Base> outputs = new ArrayList<>();
            var transformerServices = new TransformSupportServicesR5(localContext, outputs);
            var paramsTrace = ParamUtils.add(responseParameters, "trace");
            transformerServices.setTraceToParameter(paramsTrace);
            org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities smu = new org.hl7.fhir.r5.utils.structuremap.StructureMapUtilities(
                    localContext, transformerServices);
            smu.setDebug(true);
            StructureMap map = null;
            for (int mapIndex = 0; mapIndex < mapParameters.size(); mapIndex++) {
                Parameters.ParametersParameterComponent mapParameter = mapParameters.get(mapIndex);
                if (!(mapParameter.getValue() instanceof StringType)) {
                    throw new FHIRException("Map parameter " + (mapIndex + 1) + " must contain a valueString");
                }

                String fml = mapParameter.getValue().primitiveValue();
                String mapFile = getStringExtension(mapParameter, OPERATION_OUTCOME_FILE_EXTENSION);
                errorMapFile = mapFile;
                if (mapIndex == 0) {
                    primaryMapFile = mapFile;
                }
                ParamUtils.add(paramsPart, "map", fml);

                StructureMap suppliedMap = smu.parse(fml, mapFile != null ? mapFile : contentType);
                if (mapFile != null) {
                    for (StructureMap.StructureMapGroupComponent group : suppliedMap.getGroup()) {
                        group.addExtension(OPERATION_OUTCOME_FILE_EXTENSION,
                                new org.hl7.fhir.r5.model.StringType(mapFile));
                    }
                }
                try
                {
                    localContext.cacheResource(suppliedMap);
                } catch (org.hl7.fhir.exceptions.DefinitionException e) {
                    outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.WARNING)
                            .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.DUPLICATE)
                            .setDetails(new org.hl7.fhir.r4b.model.CodeableConcept().setText(e.getMessage()));
                }
                if (map == null) {
                    map = suppliedMap;
                }
            }
            if (map == null) {
                throw new FHIRException("Missing map or srcMap parameter");
            }
            errorMapFile = primaryMapFile;

            var contentParameter = contentParameters.get(0);
            var resource = contentParameter.getResource();
            byte[] resourceBytes;
            FhirFormat resourceFormat;
            if (resource instanceof Binary binary) {
                resourceFormat = getBinaryInputFormat(binary.getContentType());
                if (!binary.hasData()) {
                    throw new FHIRException("Binary input must contain data");
                }
                resourceBytes = binary.getData();
            } else if (resource != null) {
                resourceBytes = jsonParser.composeString(resource).getBytes(StandardCharsets.UTF_8);
                resourceFormat = FhirFormat.JSON;
            } else if ("resource".equals(contentParameter.getName())
                    && contentParameter.getValue() instanceof StringType text && text.hasValue()) {
                String resourceText = text.getValue().trim();
                if (resourceText.isEmpty()) {
                    throw new FHIRException("Input resource must not be empty");
                }
                resourceBytes = resourceText.getBytes(StandardCharsets.UTF_8);
                resourceFormat = resourceText.startsWith("{") ? FhirFormat.JSON : FhirFormat.XML;
            } else {
                throw new FHIRException("Parameter '" + contentParameter.getName() + "' must contain a resource"
                        + ("resource".equals(contentParameter.getName()) ? " or valueString" : ""));
            }
            inputFormatIsJson = resourceFormat == FhirFormat.JSON;

            org.hl7.fhir.r5.elementmodel.Element sourceResource;
            ByteArrayInputStream inputStream = new ByteArrayInputStream(resourceBytes);
            try {
                sourceResource = Manager.parseSingle(localContext, inputStream, resourceFormat);
            } catch (FHIRFormatError e) {
                // This isn't a native FHIR resource, so use a generic parser (which may be able
                // to handle the logical models)
                inputStream.reset();
                List<ValidatedFragment> fragments;
                if (inputFormatIsJson) {
                    var rawJsonParser = new org.hl7.fhir.r5.elementmodel.JsonParser(localContext);
                    fragments = rawJsonParser.parse(inputStream);
                } else {
                    var rawXmlParser = new org.hl7.fhir.r5.elementmodel.XmlParser(localContext);
                    fragments = rawXmlParser.parse(inputStream);
                }
                if (fragments.size() == 1) {
                    sourceResource = fragments.get(0).getElement();
                } else {
                    throw new FHIRException("Unable to parse resource");
                }
            }

            org.hl7.fhir.r5.elementmodel.Element target = getTargetResourceFromStructureMap(localContext, map);
            smu.transform(null, sourceResource, map, target);

            org.hl7.fhir.r5.elementmodel.ParserBase outputParser;
            if (inputFormatIsJson) {
                outputParser = new org.hl7.fhir.r5.elementmodel.JsonParser(localContext);
            } else {
                outputParser = new org.hl7.fhir.r5.elementmodel.XmlParser(localContext);
            }
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            outputParser.compose(target, boas, OutputStyle.PRETTY, null);
            var result = boas.toString(StandardCharsets.UTF_8);
            boas.close();

            if ("content".equals(contentParameter.getName())) {
                resultPart.setName("return");
                responseParameters.getParameter().remove(resultPart);
                responseParameters.getParameter().add(0, resultPart);
                if (target.getProperty().getStructure().getKind() == StructureDefinition.StructureDefinitionKind.RESOURCE) {
                    resultPart.setResource(inputFormatIsJson ? jsonParser.parse(result) : xmlParser.parse(result));
                } else {
                    resultPart.setResource(new Binary()
                            .setContentType(inputFormatIsJson ? MediaType.APPLICATION_JSON_VALUE : MediaType.APPLICATION_XML_VALUE)
                            .setData(boas.toByteArray()));
                }
            } else {
                resultPart.setValue(new org.hl7.fhir.r4b.model.StringType(result));
            }

            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.INFORMATION)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.INFORMATIONAL)
                    .setDiagnostics("Transformation completed successfully");
            return new ResponseEntity<>(parser.composeString(responseParameters), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error processing $transform", e);
            outcome = new org.hl7.fhir.r4b.model.OperationOutcome();
            ParamUtils.add(responseParameters, "outcome", outcome);
                var issue = outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                    .setDetails(new org.hl7.fhir.r4b.model.CodeableConcept().setText(e.getMessage()));
                    setIssueFile(issue, errorMapFile != null ? errorMapFile : primaryMapFile);
                    // .setDiagnostics(e.getMessage());
            try {
                logger.error("Unknown Error processing $transform : result in outcome");
                return new ResponseEntity<>(parser.composeString(outcome), HttpStatus.OK);
            } catch (Exception ep) {
                logger.error("Error reporting operationoutcome for $transform result", ep);
                return new ResponseEntity<>(ep.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private static FhirFormat getBinaryInputFormat(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new FHIRException("Binary input must specify a contentType");
        }
        String format = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("json".equals(format) || format.endsWith("/json") || format.endsWith("+json")) {
            return FhirFormat.JSON;
        }
        if ("xml".equals(format) || format.endsWith("/xml") || format.endsWith("+xml")) {
            return FhirFormat.XML;
        }
        throw new FHIRException("Unsupported Binary contentType '" + contentType + "': expected JSON or XML");
    }

    private static String getStringExtension(org.hl7.fhir.r4b.model.BackboneElement element, String url) {
        var extension = element.getExtensionByUrl(url);
        return extension != null && extension.hasValue() ? extension.getValue().primitiveValue() : null;
    }

    private static void setIssueFile(org.hl7.fhir.r4b.model.OperationOutcome.OperationOutcomeIssueComponent issue,
            String fileName) {
        if (fileName != null && !fileName.isBlank() && issue.getExtensionByUrl(OPERATION_OUTCOME_FILE_EXTENSION) == null) {
            issue.addExtension(OPERATION_OUTCOME_FILE_EXTENSION, new StringType(fileName));
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
                pu.generateSnapshot(sdb, sd, (sd.hasWebPath()) ? Utilities.extractBaseUrl(sd.getWebPath()) : null,
                        sdb.getName(), null);
            }
            context.cacheResource(sd);
        }

        if (resource instanceof org.hl7.fhir.r4b.model.ConceptMap) {
            var cm = (org.hl7.fhir.r5.model.ConceptMap) VersionConvertorFactory_43_50
                    .convertResource(resource);
            context.cacheResource(cm);
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
