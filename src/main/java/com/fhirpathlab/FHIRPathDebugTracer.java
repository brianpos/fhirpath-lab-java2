package com.fhirpathlab;

import java.util.List;

import org.hl7.fhir.r5.fhirpath.ExpressionNode;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine.ExecutionContext;
import org.hl7.fhir.r5.model.Base;

public class FHIRPathDebugTracer implements org.hl7.fhir.r5.fhirpath.FHIRPathEngine.IDebugTracer {

    public FHIRPathDebugTracer() {
        // Constructor
    }

    @Override
    public void traceExpression(ExecutionContext context, List<Base> focus, List<Base> result, ExpressionNode exp) {
        // Implement tracing logic here
        var expr = exp.toString();
        System.out.println("Expression: " + expr);
        System.out.println("Focus: " + focus.size() + " items");
        for (Base base : focus) {
            System.out.printf("   %s\n", base);
        }
        System.out.println("Result: " + result);
    }

    @Override
    public void traceOperationExpression(ExecutionContext context, List<Base> focus, List<Base> result,
            ExpressionNode exp) {
        // test version just passes through the same
        traceExpression(context, focus, result, exp);
    }
}