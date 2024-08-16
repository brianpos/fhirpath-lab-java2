package com.fhirpathlab;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fhirpathlab.utils.AstMapper;
import com.fhirpathlab.utils.JsonNode;
import com.fhirpathlab.utils.SimplifiedExpressionNode;
import com.google.common.io.Files;

import java.nio.charset.Charset;

import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.Patient;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;

class AstMapperTest {
    AstMapperTest() throws IOException {
        contextFactory = new ContextFactory();
        engine = new org.hl7.fhir.r4b.fhirpath.FHIRPathEngine(contextFactory.getContextR4b());

        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        prettyPrinter = new MyPrettyPrinter();
        objectMapper.setDefaultPrettyPrinter(prettyPrinter);
    }

    private ContextFactory contextFactory;

    org.hl7.fhir.r4b.fhirpath.FHIRPathEngine engine;
    ObjectMapper objectMapper;
    DefaultPrettyPrinter prettyPrinter;

    private static String ReadJsonTestFile(String testName, String testSuffix) {
        return ReadTestFile(testName, testSuffix, "json");
    }

    private static String ReadTestFile(String testName, String testSuffix, String format) {
        try {
            String workingDir = System.getProperty("user.dir");
            return Files.asCharSource(new File(
                    workingDir + "/src/test/test-data/" + testName + "." + testSuffix + "." + format),
                    Charset.defaultCharset()).read();
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
            return null;
        }
    }

    private static void WriteJsonTestFile(String testName, String testSuffix, String content) {
        WriteTestFile(testName, testSuffix, "json", content);
    }

    private static void WriteTestFile(String testName, String testSuffix, String format, String content) {
        try {
            String workingDir = System.getProperty("user.dir");
            Files.asCharSink(new File(
                    workingDir + "/src/test/test-data/" + testName + "." + testSuffix + "." + format),
                    Charset.defaultCharset()).write(content);
        } catch (Exception e) {
            System.out.println(e);
            Assertions.fail(e.getMessage());
        }
    }

    private void testExpression(String testName, String expression) {
        var parseTree = engine.parse(expression);
        SimplifiedExpressionNode simplifiedAST = SimplifiedExpressionNode.from(parseTree);
        JsonNode nodeParse = AstMapper.From(simplifiedAST);

        try {

            String jsonHapiAst = objectMapper.writeValueAsString(simplifiedAST);
            String jsonFhirPathLabAst = objectMapper.writeValueAsString(nodeParse);

            // Add your assertions here...
            assertEquals(ReadJsonTestFile(testName, "hapi"), jsonHapiAst, testName + ": HAPI AST incorrect");
            assertEquals(ReadJsonTestFile(testName, "lab"), jsonFhirPathLabAst,
                    testName + ": FHIRPathLab AST incorrect");

        } catch (JsonProcessingException e) {
            System.out.println(e);
            Assertions.fail(e.getMessage());
        }
    }

    // This is similar to testExpression except that it will write the results as
    // the expected output
    @SuppressWarnings("unused")
    private void learnExpression(String testName, String expression) {
        var parseTree = engine.parse(expression);
        SimplifiedExpressionNode simplifiedAST = SimplifiedExpressionNode.from(parseTree);
        JsonNode nodeParse = AstMapper.From(simplifiedAST);

        try {

            String jsonHapiAst = objectMapper.writeValueAsString(simplifiedAST);
            String jsonFhirPathLabAst = objectMapper.writeValueAsString(nodeParse);

            // Add your assertions here...
            WriteJsonTestFile(testName, "hapi", jsonHapiAst);
            WriteJsonTestFile(testName, "lab", jsonFhirPathLabAst);

        } catch (JsonProcessingException e) {
            System.out.println(e);
            Assertions.fail(e.getMessage());
        }
    }

    @Test
    void operationTest1() {
        testExpression("operationTest1", "'a' + 'b' & 'c' + 'd'");
    }

    @Test
    void operationTest2() {
        testExpression("operationTest2", "'a' + 'b' & 'c'");
    }

    @Test
    void unaryTest1() {
        testExpression("unaryTest1", "-1");
    }

    @Test
    void unaryTest2() {
        testExpression("unaryTest2", "+1");
    }

    @Test
    void propertyChain() {
        testExpression("propertyChain", "Patient.name.given");
    }

    @Test
    void propertyChainThenFunction() {
        testExpression("propertyChainThenFunction", "Patient.name.given.first()");
    }

    @Test
    void functionTest1() {
        testExpression("functionTest1", "trace('trc').given.join(' ').combine(family).join(', ')");
    }

    @Test
    void functionTest2() {
        testExpression("functionTest2", "trace('trc', family.first()).given.join(' ').combine(family).join(', ')");
    }

    @Test
    void functionWithAParameter() {
        testExpression("functionWithAParameter", "select(name.first()).given");
    }

    @Test
    void selectVariable() {
        testExpression("selectVariable", "select(%a)");
    }

    @Test
    void apiCheckJoinExpressionEmpty() throws IOException {
        var controller = new FhirpathTestController(contextFactory);
        var patientExample = new Patient();
        patientExample.setId("example");
        patientExample.addName().addGiven("John").addGiven("Doe").setFamily("Smith");
        patientExample.addName().addGiven("Johnny").addGiven("Doe");

        var result = controller.evaluate(patientExample, null, "{}.join(',')", null);

        // locate the actual result part of the parameters
        var parameters = (Parameters) result;
        var actualResults = parameters.getParameter("result");
        Assertions.assertEquals(1, actualResults.getPart().size());
        Assertions.assertEquals("empty-string", actualResults.getPartFirstRep().getName());
        Assertions.assertNull(actualResults.getPartFirstRep().getValue());
    }
}