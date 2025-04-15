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
import java.util.List;

import org.hl7.fhir.r4b.model.Base;
import org.hl7.fhir.r4b.model.DateType;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.Patient;
// import org.hl7.fhir.r4b.fhirpath.TypeDetails;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;

class AstMapperTest {
    AstMapperTest() throws IOException {
        contextFactory = new ContextFactory();
        engine = new org.hl7.fhir.r4b.fhirpath.FHIRPathEngine(contextFactory.getContextR4b());
        engineR5 = new org.hl7.fhir.r5.fhirpath.FHIRPathEngine(contextFactory.getContextR5());

        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        prettyPrinter = new MyPrettyPrinter();
        objectMapper.setDefaultPrettyPrinter(prettyPrinter);
    }

    private ContextFactory contextFactory;

    org.hl7.fhir.r4b.fhirpath.FHIRPathEngine engine;
    org.hl7.fhir.r5.fhirpath.FHIRPathEngine engineR5;

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
        testExpression(testName, "Patient", "Patient", expression);
    }

    private void testExpression(String testName, String resourceType, String context, String expression) {
        var parseTree = engine.parse(expression);

        // Check the expression to load in the datatypes
        var result = engine.check(null, resourceType, context, parseTree);

        SimplifiedExpressionNode simplifiedAST = SimplifiedExpressionNode.from(parseTree);
        JsonNode nodeParse = AstMapper.From(simplifiedAST, "Patient");

        try {

            String jsonHapiAst = objectMapper.writeValueAsString(simplifiedAST);
            String jsonFhirPathLabAst = objectMapper.writeValueAsString(nodeParse);

            // Write the direct results of the test to the output (so you can compare it)
            // Uncomment the following line to write the actual response to a file for debugging
            // WriteJsonTestFile(testName, "hapi2", jsonHapiAst);
            // WriteJsonTestFile(testName, "lab2", jsonFhirPathLabAst);

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
        JsonNode nodeParse = AstMapper.From(simplifiedAST, "Patient");

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
    void testCheckFunctionality() {
        var parseTree = engineR5.parse("true\r\n" + //
                "implies (\r\n" + //
                "    strength.presentation.ofType(Ratio).exists() and\r\n" + //
                "    strength.presentation.numerator.where(\r\n" + //
                "        code = '[arb\\'U]' and\r\n" + //
                "        system = 'http://unitsofmeasure.org'\r\n" + //
                "    )\r\n" + //
                ")");
        org.hl7.fhir.r5.fhirpath.TypeDetails result = engineR5.check(null, "Ingredient", "Ingredient",
                "Ingredient.substance", parseTree);
        Assertions.assertEquals(1, result.getTypes().size(),
                "Unexpected number of types: " + String.join(", ", result.getTypes()));
        Assertions.assertEquals("http://hl7.org/fhirpath/System.Boolean", String.join(", ", result.getTypes()));
    }

    @Test
    void testCheckFunctionality2() {
        var parseTree = engineR5.parse("true and strength.presentation.numerator.where( code = '[arb\\'U]' )");
        org.hl7.fhir.r5.fhirpath.TypeDetails result = engineR5.check(null, "Ingredient", "Ingredient",
                "Ingredient.substance", parseTree);
        Assertions.assertEquals(1, result.getTypes().size(),
                "Unexpected number of types: " + String.join(", ", result.getTypes()));
        Assertions.assertEquals("http://hl7.org/fhirpath/System.Boolean", String.join(", ", result.getTypes()));
    }

    @Test
    void testCheckFunctionality3() {
        var parseTree = engineR5.parse("true and strength.presentation.numerator.where( code = '[arb\\'U]' )");
        org.hl7.fhir.r5.fhirpath.TypeDetails result = engineR5.check(null, "Ingredient", "Ingredient",
                "Ingredient.substance", parseTree);
        Assertions.assertEquals(1, result.getTypes().size(),
                "Unexpected number of types: " + String.join(", ", result.getTypes()));
        Assertions.assertEquals("http://hl7.org/fhirpath/System.Boolean", String.join(", ", result.getTypes()));
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
        testExpression("functionTest1", "Patient", "Patient.name", "trace('trc').given.join(' ').combine(family).join(', ')");
    }

    @Test
    void functionTest2() {
        testExpression("functionTest2", "Patient", "Patient.name", "trace('trc', family.first()).given.join(' ').combine(family).join(', ')");
    }

    @Test
    void functionWithAParameter() {
        testExpression("functionWithAParameter", "select(name.first()).given");
    }

    // @Test
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

    @Test
    public void testEvaluate_ToStringOnDateValue() {
        Patient input = new Patient();
        var dtv = new DateType("2024");
        input.setBirthDateElement(dtv);
        List<Base> results = engine.evaluate(input, "Patient.birthDate.toString()");
        assertEquals(1, results.size());
        assertEquals("2024", results.get(0).toString());
    }

    @Test
    public void testEvaluate_ToStringOnExtensionOnlyValue() {
        Patient input = new Patient();
        var dtv = new DateType();
        input.setBirthDateElement(dtv);
        List<Base> results = engine.evaluate(input, "Patient.birthDate.toString()");
        assertEquals(0, results.size());
    }
}