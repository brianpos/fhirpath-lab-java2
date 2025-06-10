package com.fhirpathlab;

import java.util.List;
import java.util.HashMap;

import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine.IEvaluationContext;
import org.hl7.fhir.r5.fhirpath.FHIRPathUtilityClasses.FunctionDetails;
import org.hl7.fhir.r5.fhirpath.TypeDetails;
import org.hl7.fhir.r5.fhirpath.ExpressionNode.CollectionStatus;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r5.model.ValueSet;

import com.fhirpathlab.utils.ParamUtils;

import org.hl7.fhir.r5.elementmodel.ObjectConverter;
import org.hl7.fhir.r5.context.IWorkerContext;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.PathEngineException;

public class FHIRPathTestEvaluationServicesR5 implements IEvaluationContext {
    private Parameters.ParametersParameterComponent traceToParameter;
    private Parameters.ParametersParameterComponent debugTraceToParameter;
    private java.util.HashMap<String, org.hl7.fhir.r5.model.Base> mapVariables;
    IWorkerContext context;
    ObjectConverter oc;

    public FHIRPathTestEvaluationServicesR5(IWorkerContext context) {
        this.context = context;
        oc = new ObjectConverter(context);
        mapVariables = new HashMap<>();
    }

    public IWorkerContext getContext() {
        return context;
    }

    public void setTraceToParameter(Parameters.ParametersParameterComponent theTraceToParameter) {
        traceToParameter = theTraceToParameter;
    }

    public void setDebugTraceToParameter(Parameters.ParametersParameterComponent theDebugTraceToParameter) {
        debugTraceToParameter = theDebugTraceToParameter;
    }

    public void addVariable(String name, org.hl7.fhir.r5.model.Base value) {
        mapVariables.put(name, value);
    }

    public void addVariable(String name, org.hl7.fhir.r4b.model.Base value) {
        // convert the object to R5 object models

        // mapVariables.put(name, value);
    }

    public java.util.Map<String, org.hl7.fhir.r5.model.Base> getVariables() {
        return mapVariables;
    }

    @Override
    public List<org.hl7.fhir.r5.model.Base> resolveConstant(FHIRPathEngine engine, Object appContext, String name,
            boolean beforeContext, boolean explicitConstant)
            throws PathEngineException {
        if (mapVariables != null) {
            if (mapVariables.containsKey(name)) {
                var result = new java.util.ArrayList<org.hl7.fhir.r5.model.Base>();
                org.hl7.fhir.r5.model.Base itemValue = mapVariables.get(name);
                if (itemValue != null)
                    result.add(itemValue);
                return result;
            }
            // return null; // don't return null as the lack of the variable being defined
            // is an issue
        }
        throw new NotImplementedException(
                "Variable: `%" + name + "` was not provided");
    }

    @Override
    public boolean log(String argument, List<org.hl7.fhir.r5.model.Base> data) {
        if (traceToParameter != null) {
            Parameters.ParametersParameterComponent traceValue = ParamUtils.add(traceToParameter, "trace",
                    new org.hl7.fhir.r4b.model.StringType(argument));

            for (org.hl7.fhir.r5.model.Base nextOutput : data) {
                if (nextOutput instanceof org.hl7.fhir.r5.elementmodel.Element) {
                    var em = (org.hl7.fhir.r5.elementmodel.Element) nextOutput;
                    ParamUtils.addTypedElement(context, oc, traceValue, em);
                } else if (nextOutput instanceof org.hl7.fhir.r5.model.DataType) {
                    var dt = (org.hl7.fhir.r5.model.DataType) nextOutput;
                    ParamUtils.add(traceValue, dt.fhirType(), dt);
                }
            }
            return true;
        }
        return false;

    }

    @Override
    public TypeDetails resolveConstantType(FHIRPathEngine engine, Object appContext, String name,
            boolean explicitConstant) throws PathEngineException {
        if (mapVariables != null) {
            var key = name.substring(1);
            if (mapVariables.containsKey(key)) {
                org.hl7.fhir.r5.model.Base itemValue = mapVariables.get(key);
                if (itemValue != null)
                    return new TypeDetails(CollectionStatus.SINGLETON, itemValue.fhirType());
            }
        }
        return new TypeDetails(null);
    }

    @Override
    public FunctionDetails resolveFunction(FHIRPathEngine engine, String functionName) {
        if (functionName.compareToIgnoreCase("debug_trace") == 0) {
            return new FunctionDetails("debug_trace", 2, 3);
        }
        throw new UnsupportedOperationException("Unimplemented method 'resolveFunction'");
    }

    @Override
    public TypeDetails checkFunction(FHIRPathEngine engine, Object appContext, String functionName, TypeDetails focus,
            List<TypeDetails> parameters) throws PathEngineException {
        if (functionName.compareToIgnoreCase("debug_trace") == 0) {
            return focus;
        }
        throw new UnsupportedOperationException("Unimplemented method 'checkFunction'");
    }

    @Override
    public List<Base> executeFunction(FHIRPathEngine engine, Object appContext, List<Base> focus, String functionName,
            List<List<Base>> parameters) {
        if (functionName.compareToIgnoreCase("debug_trace") == 0) {

            // parameter 1 is the name/location of the function
            // parameter 2 is $this
            // parameter 3 is $index (if present)
            if (debugTraceToParameter != null) {
                Parameters.ParametersParameterComponent traceValue = ParamUtils.add(debugTraceToParameter,
                        parameters.get(0).get(0).toString());

                for (org.hl7.fhir.r5.model.Base nextOutput : focus) {
                    if (nextOutput instanceof org.hl7.fhir.r5.elementmodel.Element) {
                        var em = (org.hl7.fhir.r5.elementmodel.Element) nextOutput;
                        ParamUtils.addTypedElement(context, oc, traceValue, em, true);
                    } else if (nextOutput instanceof org.hl7.fhir.r5.model.DataType) {
                        var dt = (org.hl7.fhir.r5.model.DataType) nextOutput;
                        ParamUtils.add(traceValue, dt.fhirType(), dt);
                    }
                }

                for (org.hl7.fhir.r5.model.Base nextOutput : parameters.get(1)) {
                    if (nextOutput instanceof org.hl7.fhir.r5.elementmodel.Element) {
                        var em = (org.hl7.fhir.r5.elementmodel.Element) nextOutput;
                        var p = ParamUtils.addTypedElement(context, oc, traceValue, em, true);
                        p.setName("this-" + p.getName());
                    } else if (nextOutput instanceof org.hl7.fhir.r5.model.DataType) {
                        var dt = (org.hl7.fhir.r5.model.DataType) nextOutput;
                        var p = ParamUtils.add(traceValue, dt.fhirType(), dt);
                        p.setName("this-" + p.getName());
                    }
                }

                if (parameters.size() > 2) {
                    var index = parameters.get(2).get(0);
                    var p = new Parameters.ParametersParameterComponent()
                            .setName("index");
                    Integer rawIndex = ((org.hl7.fhir.r5.model.IntegerType) index).getValue();
                    p.setValue(new org.hl7.fhir.r4b.model.IntegerType(rawIndex));
                    traceValue.addPart(p);
                }
            }

            return focus;
        }
        throw new UnsupportedOperationException("Unimplemented method 'executeFunction' " + functionName);
    }

    @Override
    public Base resolveReference(FHIRPathEngine engine, Object appContext, String url, Base refContext)
            throws FHIRException {
        throw new UnsupportedOperationException("Unimplemented method 'resolveReference'");
    }

    @Override
    public boolean conformsToProfile(FHIRPathEngine engine, Object appContext, Base item, String url)
            throws FHIRException {
        // if (url.equals("http://hl7.org/fhir/StructureDefinition/Patient"))
        // return true;
        // if (url.equals("http://hl7.org/fhir/StructureDefinition/Person"))
        // return false;
        throw new FHIRException("unknown profile " + url);
    }

    @Override
    public ValueSet resolveValueSet(FHIRPathEngine engine, Object appContext, String url) {
        throw new UnsupportedOperationException("Unimplemented method 'resolveValueSet'");
    }

    @Override
    public boolean paramIsType(String name, int index) {
        if (name.compareToIgnoreCase("debug_trace") == 0) {
            return false;
        }

        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'paramIsType'");
    }
}
