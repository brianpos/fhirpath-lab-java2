package com.fhirpathlab;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fhirpathlab.utils.AstMapper;
import com.fhirpathlab.utils.JsonNode;
import com.fhirpathlab.utils.LogicalModelBuilder;
import com.fhirpathlab.utils.ParamUtils;
import com.fhirpathlab.utils.SimplifiedExpressionNode;

import ca.uhn.fhir.fhirpath.FhirPathExecutionException;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r4b.model.CodeableConcept;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r5.elementmodel.ValidatedFragment;
import org.hl7.fhir.r5.fhirpath.ExpressionNode;
import org.hl7.fhir.r5.fhirpath.FHIRLexer.FHIRLexerException;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping({ "/fhir", "/fhir5" })
@CrossOrigin(origins = "*", // Replace with your client origin
        methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT }, allowedHeaders = { "Content-Type",
                "Accept", "Origin", "cache-control" }, exposedHeaders = { "Location", "Content-Location" })
public class FhirpathTestController {

    private final ContextFactory contextFactory;
    private static final Logger logger = LoggerFactory.getLogger(FhirpathTestController.class);
    private org.hl7.fhir.r4b.formats.XmlParser xmlParser;
    private org.hl7.fhir.r4b.formats.JsonParser jsonParser;
    private org.hl7.fhir.r5.formats.JsonParser jsonParserR5;

    public FhirpathTestController(ContextFactory contextFactory) {
        this.contextFactory = contextFactory;
        xmlParser = new org.hl7.fhir.r4b.formats.XmlParser();
        jsonParser = new org.hl7.fhir.r4b.formats.JsonParser();
        jsonParserR5 = new org.hl7.fhir.r5.formats.JsonParser();
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
    @PostMapping(value = "/$fhirpath-r5", consumes = { MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            "application/fhir+json;fhirVersion=4.0" })
    public ResponseEntity<String> evaluateFhirPathR5(@RequestBody String content,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        var outcome = new org.hl7.fhir.r4b.model.OperationOutcome();
        org.hl7.fhir.r4b.formats.IParser parser = null;
        org.hl7.fhir.r5.formats.IParser parserR5 = null;

        logger.info("Evaluating: fhir5/$fhirpath-r5");
        try {
            // Determine the appropriate parser based on Content-Type header
            if (contentType != null && contentType.contains(MediaType.APPLICATION_XML_VALUE)) {
                parser = xmlParser;
                parserR5 = new org.hl7.fhir.r5.formats.XmlParser();
                parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
            } else {
                parser = jsonParser; // Default to JSON parser
                parserR5 = new org.hl7.fhir.r5.formats.JsonParser();
                parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
            }

            // Parse the input content into a Patient resource
            var parameters = (org.hl7.fhir.r4b.model.Parameters) parser.parse(content);
            var parametersR5 = (org.hl7.fhir.r5.model.Parameters) parserR5.parse(content);  
            String contextExpression = null;
            if (parameters.getParameterValue("context") != null)
                contextExpression = parameters.getParameterValue("context").primitiveValue();
            String expression = null;
            if (parameters.getParameterValue("expression") != null)
                expression = parameters.getParameterValue("expression").primitiveValue();
            Parameters.ParametersParameterComponent variables = parameters.getParameter("variables");

            if (isNotBlank(expression)) {
                org.hl7.fhir.r5.elementmodel.Element resource = null;
                if (parametersR5.getParameter("resource") != null)
                    resource = getResourceR5(contextFactory.getContextR5(), parametersR5.getParameter("resource"));

                var responseParameters = evaluate(contextFactory.getContextR5(), resource, contextExpression, expression, variables);
                return new ResponseEntity<>(parser.composeString(responseParameters), HttpStatus.OK);
            }

            // There is no expression, so we should return that as an issue
            logger.error("Cannot evaluate without a fhirpath expression");
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.INCOMPLETE)
                    .setDetails(new CodeableConcept().setText("Cannot evaluate without a fhirpath expression"));

            return new ResponseEntity<>(parser.composeString(outcome), HttpStatus.BAD_REQUEST);
        } catch (org.hl7.fhir.exceptions.PathEngineException e) {
            logger.error("Error processing $fhirpath", e);
            var location = new java.util.ArrayList<StringType>();
            if (e.getLocation() != null)
                location.add(new StringType(e.getLocation().toString()));
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                    .setLocation(location)
                    .setDetails(new CodeableConcept().setText(e.getMessage()));
        } catch (org.hl7.fhir.exceptions.FHIRException e) {
            logger.error("Error processing $fhirpath", e);
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                    .setDetails(new CodeableConcept().setText(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error processing $fhirpath", e);
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                    .setDetails(new CodeableConcept().setText("Unknown error evaluating fhirpath expression"))
                    .setDiagnostics(e.getMessage());
        }
        try {
            logger.error("Unknown Error processing $fhirpath : result in outcome");
            return new ResponseEntity<>(parser.composeString(outcome), HttpStatus.BAD_REQUEST);
        } catch (Exception ep) {
            logger.error("Error reporting operationoutcome for $fhirpath result", ep);
            return new ResponseEntity<>(ep.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
    public ResponseEntity<String> evaluateFhirPath(@RequestBody String content,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        var outcome = new org.hl7.fhir.r4b.model.OperationOutcome();
        org.hl7.fhir.r4b.formats.IParser parser = null;

        logger.info("Evaluating: fhir/$fhirpath");
        try {
            // Determine the appropriate parser based on Content-Type header
            if (contentType != null && contentType.contains(MediaType.APPLICATION_XML_VALUE)) {
                parser = xmlParser;
                parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
            } else {
                parser = jsonParser; // Default to JSON parser
                parser.setOutputStyle(org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY);
            }

            // Parse the input content into a Patient resource
            var parameters = (org.hl7.fhir.r4b.model.Parameters) parser.parse(content);
            String contextExpression = null;
            if (parameters.getParameterValue("context") != null)
                contextExpression = parameters.getParameterValue("context").primitiveValue();
            String expression = null;
            if (parameters.getParameterValue("expression") != null)
                expression = parameters.getParameterValue("expression").primitiveValue();
            Parameters.ParametersParameterComponent variables = parameters.getParameter("variables");

            if (isNotBlank(expression)) {
                org.hl7.fhir.r5.elementmodel.Element resource = null;
                if (parameters.getParameter("resource") != null)
                    resource = getResource(contextFactory.getContextR4bAsR5(), parameters.getParameter("resource"));

                var responseParameters = evaluate(contextFactory.getContextR4bAsR5(), resource, contextExpression, expression, variables);
                return new ResponseEntity<>(parser.composeString(responseParameters), HttpStatus.OK);
            }

            // There is no expression, so we should return that as an issue
            logger.error("Cannot evaluate without a fhirpath expression");
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.INCOMPLETE)
                    .setDetails(new CodeableConcept().setText("Cannot evaluate without a fhirpath expression"));

            return new ResponseEntity<>(parser.composeString(outcome), HttpStatus.BAD_REQUEST);
        } catch (org.hl7.fhir.exceptions.PathEngineException e) {
            logger.error("Error processing $fhirpath", e);
            var location = new java.util.ArrayList<StringType>();
            if (e.getLocation() != null)
                location.add(new StringType(e.getLocation().toString()));
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                    .setLocation(location)
                    .setDetails(new CodeableConcept().setText(e.getMessage()));
        } catch (org.hl7.fhir.exceptions.FHIRException e) {
            logger.error("Error processing $fhirpath", e);
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                    .setDetails(new CodeableConcept().setText(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error processing $fhirpath", e);
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                    .setDetails(new CodeableConcept().setText("Unknown error evaluating fhirpath expression"))
                    .setDiagnostics(e.getMessage());
        }
        try {
            logger.error("Unknown Error processing $fhirpath : result in outcome");
            return new ResponseEntity<>(parser.composeString(outcome), HttpStatus.BAD_REQUEST);
        } catch (Exception ep) {
            logger.error("Error reporting operationoutcome for $fhirpath result", ep);
            return new ResponseEntity<>(ep.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private org.hl7.fhir.r5.elementmodel.Element getResource(org.hl7.fhir.r5.context.SimpleWorkerContext context, ParametersParameterComponent part) {
        try {
            if (part.getResource() != null) {
                // convert the resource into an element (via a JASON string)
                String jsonText = jsonParser.composeString(part.getResource());
                InputStream inputStream = new ByteArrayInputStream(jsonText.getBytes(StandardCharsets.UTF_8));
                var fragments = Manager.parse(context, inputStream,
                        org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON);
                if (fragments.size() != 1) {
                    throw new FHIRException("Unable to parse resource");
                }
                var element = fragments.get(0).getElement();
                return element;
            }

            // See if there is a json fragment extension
            if (part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/json-value") != null) {
                var jsonText = part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/json-value")
                        .getValue().primitiveValue();
                InputStream inputStream = new ByteArrayInputStream(jsonText.getBytes(StandardCharsets.UTF_8));
                var fragments = Manager.parse(context, inputStream,
                        org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON);
                if (fragments.size() != 1) {
                    throw new FHIRException("Unable to parse resource");
                }
                var element = fragments.get(0).getElement();
                return element;
            }
            // See if there is a xml fragment extension
            if (part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/xml-value") != null) {
                var xmlText = part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/xml-value")
                        .getValue().primitiveValue();
                InputStream inputStream = new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8));
                var fragments = Manager.parse(context, inputStream,
                        org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.XML);
                if (fragments.size() != 1) {
                    throw new FHIRException("Unable to parse resource");
                }
                var element = fragments.get(0).getElement();
                return element;
            }

        } catch (IOException e) {
            logger.error("Error converting resource to element model", e);
        }
        return null;
    }

    private org.hl7.fhir.r5.elementmodel.Element getResourceR5(org.hl7.fhir.r5.context.SimpleWorkerContext context, org.hl7.fhir.r5.model.Parameters.ParametersParameterComponent part) {
        try {
            if (part.getResource() != null) {
                // convert the resource into an element (via a JSON string)
                String jsonText = jsonParserR5.composeString(part.getResource());
                InputStream inputStream = new ByteArrayInputStream(jsonText.getBytes(StandardCharsets.UTF_8));
                var fragments = Manager.parse(context, inputStream,
                        org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON);
                if (fragments.size() != 1) {
                    throw new FHIRException("Unable to parse resource");
                }
                var element = fragments.get(0).getElement();
                return element;
            }

            // See if there is a json fragment extension
            if (part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/json-value") != null) {
                var jsonText = part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/json-value")
                        .getValue().primitiveValue();
                InputStream inputStream = new ByteArrayInputStream(jsonText.getBytes(StandardCharsets.UTF_8));
                var fragments = Manager.parse(context, inputStream,
                        org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.JSON);
                if (fragments.size() != 1) {
                    throw new FHIRException("Unable to parse resource");
                }
                var element = fragments.get(0).getElement();
                return element;
            }
            // See if there is a xml fragment extension
            if (part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/xml-value") != null) {
                var xmlText = part.getExtensionByUrl("http://fhir.forms-lab.com/StructureDefinition/xml-value")
                        .getValue().primitiveValue();
                InputStream inputStream = new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8));
                var fragments = Manager.parse(context, inputStream,
                        org.hl7.fhir.r5.elementmodel.Manager.FhirFormat.XML);
                if (fragments.size() != 1) {
                    throw new FHIRException("Unable to parse resource");
                }
                var element = fragments.get(0).getElement();
                return element;
            }

        } catch (IOException e) {
            logger.error("Error converting resource to element model", e);
        }
        return null;
    }

    private void processVariables(org.hl7.fhir.r5.context.SimpleWorkerContext context, Parameters.ParametersParameterComponent variables,
            ParametersParameterComponent paramsPart,
            FHIRPathTestEvaluationServicesR5 services) {
        if (variables != null && variables.getPart() != null && !variables.getPart().isEmpty()) {
            paramsPart.addPart(variables);
            var variableParts = variables.getPart();
            for (int i = 0; i < variableParts.size(); i++) {
                var part = variableParts.get(i);
                if (part.getResource() != null)
                    services.addVariable(part.getName(), getResource(context, part));
                else {
                    if (part.getExtensionByUrl(
                            "http://fhir.forms-lab.com/StructureDefinition/json-value") != null) {
                        // this is not currently supported...
                        var variableText = part.getExtensionByUrl(
                                "http://fhir.forms-lab.com/StructureDefinition/json-value").getValue().primitiveValue();
                        var sd = LogicalModelBuilder.build(variableText, part.getName(), null);
                        // services.context.cacheResource(sd);

                        InputStream inputStream = new ByteArrayInputStream(
                                variableText.getBytes(StandardCharsets.UTF_8));
                        // This isn't a native FHIR resource, so use a generic parser (which may be able
                        // to handle the logical models)
                        List<ValidatedFragment> fragments;
                        try {
                            if (variableText.startsWith("{")) {
                                var rawJsonParser = new org.hl7.fhir.r5.elementmodel.JsonParser(services.context);
                                fragments = rawJsonParser.parse(inputStream);
                            } else {
                                var rawXmlParser = new org.hl7.fhir.r5.elementmodel.XmlParser(services.context);
                                fragments = rawXmlParser.parse(inputStream);
                            }
                            if (fragments.size() == 1) {
                                var sourceVariable = fragments.get(0).getElement();
                                services.addVariable(part.getName(), sourceVariable);
                            } else {
                                throw new FHIRException("Unable to parse resource");
                            }
                        } catch (IOException e) {
                            logger.error("Error parsing custom variable value", e);
                        }

                        // services.addVariable(part.getName(), null);
                    } else {

                        services.addVariable(part.getName(), part.getValue());
                    }
                }
            }
        }
    }

    private void generateParseTree(String resourceType, String contextExpression, String expression,
            ParametersParameterComponent paramsPart, FHIRPathEngine engine,
            org.hl7.fhir.r4b.model.OperationOutcome outcome) {
        try {
            org.hl7.fhir.r5.fhirpath.ExpressionNode parseTree;
            parseTree = engine.parse(expression);

            // Also check the expression tree so that it decudes any errrors, and marks up
            // the object with return types
            try {
                if (contextExpression == null)
                    contextExpression = resourceType;
                if (!contextExpression.startsWith(resourceType))
                    contextExpression = resourceType + "." + contextExpression;

                engine.check(null, resourceType, resourceType, contextExpression, parseTree);
            } catch (FHIRLexerException e) {
                logger.error("Error parsing expression: ", e);
            } catch (org.hl7.fhir.exceptions.PathEngineException e) {
                logger.error("Error processing $fhirpath", e);
                var location = new java.util.ArrayList<StringType>();
                if (e.getLocation() != null)
                    location.add(new StringType(e.getLocation().toString()));
                outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                        .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.EXCEPTION)
                        .setLocation(location)
                        .setDetails(new CodeableConcept().setText(e.getMessage()));
            }
            SimplifiedExpressionNode simplifiedAST = SimplifiedExpressionNode.from(parseTree);
            JsonNode nodeParse = AstMapper.From(simplifiedAST, contextExpression);

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            String jsonAstTree = objectMapper.writeValueAsString(nodeParse).replace("\r", "");
            ParamUtils.add(paramsPart, "parseDebugTree", jsonAstTree);

            String jsonAstTree2 = objectMapper.writeValueAsString(simplifiedAST).replace("\r", "");
            ParamUtils.add(paramsPart, "parseDebugTreeJava", jsonAstTree2);

        } catch (IOException ex) {
            logger.error("Error generating parse tree: ", ex);
        }
    }

    private List<Base> evaluateContexts(String contextExpression, org.hl7.fhir.r5.elementmodel.Element sourceResource,
            FHIRPathEngine engine) {
        List<Base> contextOutputs;
        if (contextExpression != null) {
            try {
                contextOutputs = engine.evaluate(sourceResource, contextExpression);
            } catch (FhirPathExecutionException e) {
                throw new InvalidRequestException(
                        Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
            }
        } else {
            contextOutputs = new java.util.ArrayList<>();
            contextOutputs.add(sourceResource);
        }
        return contextOutputs;
    }

    public Parameters evaluate(org.hl7.fhir.r5.context.SimpleWorkerContext context, org.hl7.fhir.r5.elementmodel.Element resource, String contextExpression,
            String expression,
            Parameters.ParametersParameterComponent variables) throws IOException {
        var responseParameters = new Parameters();
        responseParameters.setId("fhirpath");
        var paramsPart = ParamUtils.add(responseParameters, "parameters");
        ParamUtils.add(paramsPart, "evaluator", "Java 6.5.19 (r4b)");
        ParamUtils.add(paramsPart, "context", contextExpression);
        ParamUtils.add(paramsPart, "expression", expression);

        var engine = new org.hl7.fhir.r5.fhirpath.FHIRPathEngine(context);

        FHIRPathTestEvaluationServicesR5 services = new FHIRPathTestEvaluationServicesR5(context);
        engine.setHostServices(services);

        // pass through all the variables
        processVariables(context, variables, paramsPart, services);

        // locate all of the context objects
        List<Base> contextOutputs = evaluateContexts(contextExpression, resource, engine);

        // Parse out the expression tree for the debug output
        var outcome = new org.hl7.fhir.r4b.model.OperationOutcome();
        var pathBasedContextExpression = contextExpression;
        if (contextExpression != null && contextOutputs.size() > 0) {
            var firstElement = (org.hl7.fhir.r5.elementmodel.Element)contextOutputs.get(0);
            if (firstElement != null)
                pathBasedContextExpression = firstElement.getPath().replaceAll("\\[[0-9]+\\]", "");
        }
        generateParseTree(resource.fhirType(), pathBasedContextExpression, expression, paramsPart, engine,
                outcome);
        if (outcome.hasIssue()) {
            var oucomePart = paramsPart.addPart();
            oucomePart.setName("debugOutcome");
            oucomePart.setResource(outcome);
        }

        processEvaluationResults(context, responseParameters, contextExpression, expression, engine, services,
                resource, contextOutputs);
        return responseParameters;
    }

    private void processEvaluationResults(org.hl7.fhir.r5.context.IWorkerContext context,
            Parameters responseParameters, String contextExpression, String expression,
            FHIRPathEngine engine, FHIRPathTestEvaluationServicesR5 services,
            org.hl7.fhir.r5.elementmodel.Element sourceResource, List<Base> contextOutputs) {
        var oc = new org.hl7.fhir.r5.elementmodel.ObjectConverter(context);

        for (int i = 0; i < contextOutputs.size(); i++) {
            org.hl7.fhir.r5.model.Base node = contextOutputs.get(i);
            var resultPart = ParamUtils.add(responseParameters, "result");
            services.setTraceToParameter(resultPart);
            if (contextExpression != null)
            if (node instanceof org.hl7.fhir.r5.elementmodel.Element) {
                var em = (org.hl7.fhir.r5.elementmodel.Element) node;
                var path = em.getPath().replace("[x]", "");
                if (!path.endsWith("]"))
                    path = path + String.format("[%d]", i);
                resultPart.setValue(new StringType(path));
            }
            else {
                resultPart.setValue(new StringType(String.format("%s[%d]", contextExpression, i)));
            }

            List<org.hl7.fhir.r5.model.Base> outputs;
            try {
                ExpressionNode exp = engine.parse(expression);
                outputs = engine.evaluate(null, sourceResource, sourceResource, node, exp);
            } catch (FhirPathExecutionException e) {
                throw new InvalidRequestException(
                        Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
            }

            for (Base nextOutput : outputs) {
                if (nextOutput instanceof org.hl7.fhir.r5.elementmodel.Element) {
                    var em = (org.hl7.fhir.r5.elementmodel.Element) nextOutput;
                    ParamUtils.addTypedElement(context, oc, resultPart, em);
                } else if (nextOutput instanceof org.hl7.fhir.r5.model.DataType) {
                    var dt = (org.hl7.fhir.r5.model.DataType) nextOutput;
                    ParamUtils.add(resultPart, dt.fhirType(), dt);
                }
            }
        }
    }
}
