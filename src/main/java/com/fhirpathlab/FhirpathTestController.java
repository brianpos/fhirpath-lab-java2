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

import ca.uhn.fhir.fhirpath.FhirPathExecutionException;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.hl7.fhir.r4b.elementmodel.Manager;
import org.hl7.fhir.r4b.model.Base;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r4b.elementmodel.Manager.FhirFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

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

    private final FhirpathLabSimpleWorkerContextR4B context;
    private org.hl7.fhir.r4b.formats.XmlParser xmlParser;
    private org.hl7.fhir.r4b.formats.JsonParser jsonParser;

    public FhirpathTestController(FhirpathLabSimpleWorkerContextR4B context) {
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
    @PostMapping(value = "/$fhirpath", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE,
            "application/fhir+json;fhirVersion=4.0" })
    public ResponseEntity<String> evaluateFhirPath(@RequestBody String content,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        org.hl7.fhir.r4b.model.OperationOutcome outcome;
        org.hl7.fhir.r4b.formats.IParser parser = null;
        var responseParameters = new Parameters();
        responseParameters.setId("fhirpath");

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

                var paramsPart = ParamUtils.add(responseParameters, "parameters");
                ParamUtils.add(paramsPart, "evaluator", "Java 6.3.20 (r4b)");
                ParamUtils.add(paramsPart, "context", contextExpression);
                ParamUtils.add(paramsPart, "expression", expression);
                org.hl7.fhir.r4b.model.Resource resource = null;
                if (parameters.getParameter("resource") != null)
                    resource = parameters.getParameter("resource").getResource();

                String resourceText = null;
                if (resource != null) {
                    resourceText = jsonParser.composeString(resource);
                }
                InputStream inputStream = new ByteArrayInputStream(resourceText.getBytes(StandardCharsets.UTF_8));
                org.hl7.fhir.r4b.elementmodel.Element sourceResource = null;
                sourceResource = Manager.parseSingle((context), inputStream, FhirFormat.JSON);

                var engine = new org.hl7.fhir.r4b.fhirpath.FHIRPathEngine(context);

                FHIRPathTestEvaluationServices services = new FHIRPathTestEvaluationServices(context);
                engine.setHostServices(services);

                // pass through all the variables
                if (variables != null) {
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

                // Parse out the expression tree for the debug output
                try {
                    org.hl7.fhir.r4b.fhirpath.ExpressionNode parseTree;
                    parseTree = engine.parse(expression);
                    SimplifiedExpressionNode simplifiedAST = SimplifiedExpressionNode.from(parseTree);
                    JsonNode nodeParse = AstMapper.From(simplifiedAST);

                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

                    String jsonAstTree = objectMapper.writeValueAsString(nodeParse);
                    ParamUtils.add(paramsPart, "parseDebugTree", jsonAstTree);

                    String jsonAstTree2 = objectMapper.writeValueAsString(simplifiedAST);
                    ParamUtils.add(paramsPart, "parseDebugTreeJava", jsonAstTree2);

                } catch (IOException ex) {
                    System.out.println(ex.getMessage());
                }

                // locate all of the context objects
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

                var oc = new org.hl7.fhir.r4b.elementmodel.ObjectConverter(context);

                for (int i = 0; i < contextOutputs.size(); i++) {
                    org.hl7.fhir.r4b.model.Base node = contextOutputs.get(i);
                    var resultPart = ParamUtils.add(responseParameters, "result");
                    services.setTraceToParameter(resultPart);
                    if (contextExpression != null)
                        resultPart.setValue(new StringType(String.format("%s[%d]", contextExpression, i)));

                    List<org.hl7.fhir.r4b.model.Base> outputs;
                    try {
                        outputs = engine.evaluate(node, expression);
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
}
