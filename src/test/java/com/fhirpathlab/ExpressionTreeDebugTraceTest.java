package com.fhirpathlab;

import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;

import org.hl7.fhir.r4b.model.OperationOutcome;
import org.hl7.fhir.r4b.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r5.fhirpath.ExpressionNode;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine.ExecutionContext;
import org.hl7.fhir.r5.model.Base;

import java.io.IOException;

@Import(ExpressionTreeDebugTraceTest.TestConfig.class)
class ExpressionTreeDebugTraceTest {
    ExpressionTreeDebugTraceTest() throws IOException {
        contextFactory = new ContextFactory();
        engineR5 = new org.hl7.fhir.r5.fhirpath.FHIRPathEngine(contextFactory.getContextR5());
    }

    @TestConfiguration
    static class TestConfig {

        public ContextFactory contextFactory() {
            return new ContextFactory();
        }
    }

    private ContextFactory contextFactory;
    org.hl7.fhir.r5.fhirpath.FHIRPathEngine engineR5;

    @Test
    void testOrOperator() {
        var node = FhirpathTestController.generateParseTree(
            "Patient", null, "true or false",
            new ParametersParameterComponent(), engineR5, true, new OperationOutcome());

        Assertions.assertNotNull(node);
        System.out.println("Parse tree: " + node.toString());
        Assertions.assertEquals("true or false", node.toString());
        //Assertions.assertEquals("http://hl7.org/fhirpath/System.Boolean", String.join(", ", result.getTypes()));
    }

    @Test
    void testChildProperty() {
        var node = FhirpathTestController.generateParseTree(
            "Patient", null, "name.given",
            new ParametersParameterComponent(), engineR5, true, new OperationOutcome());

        Assertions.assertNotNull(node);
        System.out.println("Parse tree: " + node.toString());
        Assertions.assertEquals("name.given", node.toString());
        //Assertions.assertEquals("http://hl7.org/fhirpath/System.Boolean", String.join(", ", result.getTypes()));
    }

    @Test
    void testWhereFunction() {

        engineR5.setTracer(new FHIRPathDebugTracer() {
            @Override
            public void traceExpression(ExecutionContext context, List<Base> focus, List<Base> result, ExpressionNode exp) {
                var expr = exp.toString();
                System.out.println("Expression: " + expr);
                System.out.println("Focus: " + focus.size() + " items");
                for (Base base : focus) {
                System.out.printf("   %s\n", base);
                }
                System.out.println("Result: " + result);
            }
        });

        var node = FhirpathTestController.generateParseTree(
            "Patient", null, "name.where(use = 'official')",
            new ParametersParameterComponent(), engineR5, true, new OperationOutcome());

        Assertions.assertNotNull(node);
        System.out.println("Parse tree: " + node.toString());
        Assertions.assertEquals("name.where(use = 'official')", node.toString());
        //Assertions.assertEquals("http://hl7.org/fhirpath/System.Boolean", String.join(", ", result.getTypes()));
    }


    @Test
    void testWhereMultipleArgsFunction() {
        var parseTree = engineR5.parse("name.where(use = 'official' or use = 'maiden')");
        System.out.println("Parse tree: " + parseTree.toString());

        var node = FhirpathTestController.generateParseTree(
            "Patient", null, "name.where(use = 'official' or use = 'maiden')",
            new ParametersParameterComponent()
            , engineR5, true, new OperationOutcome());

        Assertions.assertNotNull(node);
        System.out.println("Parse tree: " + node.toString());
        // Assertions.assertEquals("name.where(use = 'official' or use = 'maiden')", node.toString());
        //Assertions.assertEquals("http://hl7.org/fhirpath/System.Boolean", String.join(", ", result.getTypes()));
    }
}
