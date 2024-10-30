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
import com.fhirpathlab.utils.ParamUtils;
import com.fhirpathlab.utils.SimplifiedExpressionNode;

import ca.uhn.fhir.fhirpath.FhirPathExecutionException;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.hl7.fhir.r4b.elementmodel.Manager;
import org.hl7.fhir.r4b.model.Base;
import org.hl7.fhir.r4b.model.CodeableConcept;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r4b.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r4b.fhirpath.ExpressionNode;
import org.hl7.fhir.r4b.fhirpath.FHIRLexer.FHIRLexerException;
import org.hl7.fhir.r4b.fhirpath.FHIRPathEngine;
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
@RequestMapping("/fhir")
@CrossOrigin(origins = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT }) // Replace with your
                                                                                                    // client origin
public class FhirpathTestController {

    private final ContextFactory contextFactory;
    private static final Logger logger = LoggerFactory.getLogger(FhirpathTestController.class);
    private org.hl7.fhir.r4b.formats.XmlParser xmlParser;
    private org.hl7.fhir.r4b.formats.JsonParser jsonParser;

    public FhirpathTestController(ContextFactory contextFactory) {
        this.contextFactory = contextFactory;
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
                org.hl7.fhir.r4b.model.Resource resource = null;
                if (parameters.getParameter("resource") != null)
                    resource = parameters.getParameter("resource").getResource();

                var responseParameters = evaluate(resource, contextExpression, expression, variables);
                return new ResponseEntity<>(parser.composeString(responseParameters), HttpStatus.OK);
            }

            // There is no expression, so we should return that as an issue
            logger.error("Cannot evaluate without a fhirpath expression");
            outcome.addIssue().setSeverity(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR)
                    .setCode(org.hl7.fhir.r4b.model.OperationOutcome.IssueType.INCOMPLETE)
                    .setDetails(new CodeableConcept().setText("Cannot evaluate without a fhirpath expression"));

            return new ResponseEntity<>(parser.composeString(outcome), HttpStatus.BAD_REQUEST);
        } catch (FHIRLexerException e) {
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

    private void processVariables(Parameters.ParametersParameterComponent variables,
            ParametersParameterComponent paramsPart,
            FHIRPathTestEvaluationServices services) {
        if (variables != null && variables.getPart() != null && !variables.getPart().isEmpty()) {
            paramsPart.addPart(variables);
            var variableParts = variables.getPart();
            for (int i = 0; i < variableParts.size(); i++) {
                var part = variableParts.get(i);
                if (part.getResource() != null)
                    services.addVariable(part.getName(), part.getResource());
                else {
                    if (part.getExtensionByUrl(
                            "http://fhir.forms-lab.com/StructureDefinition/json-value") != null) {
                        // this is not currently supported...
                        services.addVariable(part.getName(), null);
                    } else {
                        services.addVariable(part.getName(), part.getValue());
                    }
                }
            }
        }
    }

    private void generateParseTree(String expression, ParametersParameterComponent paramsPart, FHIRPathEngine engine) {
        try {
            org.hl7.fhir.r4b.fhirpath.ExpressionNode parseTree;
            parseTree = engine.parse(expression);
            SimplifiedExpressionNode simplifiedAST = SimplifiedExpressionNode.from(parseTree);
            JsonNode nodeParse = AstMapper.From(simplifiedAST);

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

    private List<Base> evaluateContexts(String contextExpression, org.hl7.fhir.r4b.elementmodel.Element sourceResource,
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

    public Parameters evaluate(org.hl7.fhir.r4b.model.Resource resource, String contextExpression, String expression,
            Parameters.ParametersParameterComponent variables) throws IOException {
        var responseParameters = new Parameters();
        responseParameters.setId("fhirpath");
        var paramsPart = ParamUtils.add(responseParameters, "parameters");
        ParamUtils.add(paramsPart, "evaluator", "Java 6.4.0 (r4b)");
        ParamUtils.add(paramsPart, "context", contextExpression);
        ParamUtils.add(paramsPart, "expression", expression);

        String resourceText = null;
        if (resource != null) {
            resourceText = jsonParser.composeString(resource);
        }
        InputStream inputStream = new ByteArrayInputStream(resourceText.getBytes(StandardCharsets.UTF_8));
        org.hl7.fhir.r4b.elementmodel.Element sourceResource = null;
        var context = contextFactory.getContextR4b();
        sourceResource = Manager.parseSingle((context), inputStream, FhirFormat.JSON);

        var engine = new org.hl7.fhir.r4b.fhirpath.FHIRPathEngine(context);

        FHIRPathTestEvaluationServices services = new FHIRPathTestEvaluationServices(context);
        engine.setHostServices(services);

        // pass through all the variables
        processVariables(variables, paramsPart, services);

        // Parse out the expression tree for the debug output
        generateParseTree(expression, paramsPart, engine);

        // locate all of the context objects
        List<Base> contextOutputs = evaluateContexts(contextExpression, sourceResource, engine);

        processEvaluationResults(context, responseParameters, contextExpression, expression, engine, services, sourceResource, contextOutputs);
        return responseParameters;
    }

    private void processEvaluationResults(org.hl7.fhir.r4b.context.IWorkerContext context, Parameters responseParameters, String contextExpression, String expression,
            FHIRPathEngine engine, FHIRPathTestEvaluationServices services, org.hl7.fhir.r4b.elementmodel.Element sourceResource, List<Base> contextOutputs) {
        var oc = new org.hl7.fhir.r4b.elementmodel.ObjectConverter(context);

        for (int i = 0; i < contextOutputs.size(); i++) {
            org.hl7.fhir.r4b.model.Base node = contextOutputs.get(i);
            var resultPart = ParamUtils.add(responseParameters, "result");
            services.setTraceToParameter(resultPart);
            if (contextExpression != null)
                resultPart.setValue(new StringType(String.format("%s[%d]", contextExpression, i)));

            List<org.hl7.fhir.r4b.model.Base> outputs;
            try {
                ExpressionNode exp = engine.parse(expression);
                outputs = engine.evaluate(null, sourceResource, sourceResource, node, exp);
            } catch (FhirPathExecutionException e) {
                throw new InvalidRequestException(
                        Msg.code(327) + "Error parsing FHIRPath expression: " + e.getMessage());
            }

            for (Base nextOutput : outputs) {
                if (nextOutput instanceof org.hl7.fhir.r4b.elementmodel.Element) {
                    var em = (org.hl7.fhir.r4b.elementmodel.Element) nextOutput;
                    ParamUtils.addTypedElement(context, oc, resultPart, em);
                } else if (nextOutput instanceof org.hl7.fhir.r4b.model.DataType) {
                    var dt = (org.hl7.fhir.r4b.model.DataType) nextOutput;
                    if (dt instanceof StringType) {
                        StringType st = (StringType) dt;
                        if (st.getValue().equalsIgnoreCase(""))
                            ParamUtils.add(resultPart, "empty-string");
                        else
                            ParamUtils.add(resultPart, dt.fhirType(), st);
                    } else {
                        ParamUtils.add(resultPart, dt.fhirType(), dt);
                    }
                }
            }
        }
    }
}
