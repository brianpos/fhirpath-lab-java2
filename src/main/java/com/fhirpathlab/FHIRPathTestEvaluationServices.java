package com.fhirpathlab;

import java.util.List;
import java.util.HashMap;

import org.hl7.fhir.r4b.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r4b.fhirpath.FHIRPathUtilityClasses.FunctionDetails;
import org.hl7.fhir.r4b.fhirpath.TypeDetails;
import org.hl7.fhir.r4b.fhirpath.ExpressionNode.CollectionStatus;
import org.hl7.fhir.r4b.model.Base;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r4b.model.ValueSet;
import org.hl7.fhir.r4b.fhirpath.IHostApplicationServices;
import org.hl7.fhir.utilities.fhirpath.FHIRPathConstantEvaluationMode;

import com.fhirpathlab.utils.ParamUtils;

import org.hl7.fhir.r4b.elementmodel.ObjectConverter;
import org.hl7.fhir.r4b.context.IWorkerContext;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.PathEngineException;

public class FHIRPathTestEvaluationServices implements IHostApplicationServices {
    private Parameters.ParametersParameterComponent traceToParameter;
    private java.util.HashMap<String, org.hl7.fhir.r4b.model.Base> mapVariables;
    IWorkerContext context;
    ObjectConverter oc;

    public FHIRPathTestEvaluationServices(IWorkerContext context) {
        this.context = context;
        oc = new ObjectConverter(context);
        mapVariables = new HashMap<>();
    }

    public void setTraceToParameter(Parameters.ParametersParameterComponent theTraceToParameter) {
        traceToParameter = theTraceToParameter;
    }

    public void addVariable(String name, org.hl7.fhir.r4b.model.Base value) {
        mapVariables.put(name, value);
    }

    public java.util.Map<String, org.hl7.fhir.r4b.model.Base> getVariables() {
        return mapVariables;
    }

    @Override
    public List<org.hl7.fhir.r4b.model.Base> resolveConstant(FHIRPathEngine engine, Object appContext, String name,
            FHIRPathConstantEvaluationMode mode)
            throws PathEngineException {
        if (mapVariables != null) {
            if (mapVariables.containsKey(name)) {
                var result = new java.util.ArrayList<org.hl7.fhir.r4b.model.Base>();
                org.hl7.fhir.r4b.model.Base itemValue = mapVariables.get(name);
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
    public boolean log(String argument, List<org.hl7.fhir.r4b.model.Base> data) {
        if (traceToParameter != null) {
            Parameters.ParametersParameterComponent traceValue = ParamUtils.add(traceToParameter, "trace",
                    new StringType(argument));

            for (org.hl7.fhir.r4b.model.Base nextOutput : data) {
                if (nextOutput instanceof org.hl7.fhir.r4b.elementmodel.Element) {
                    var em = (org.hl7.fhir.r4b.elementmodel.Element) nextOutput;
                    // ParamUtils.addTypedElement(context, oc, traceValue, em);
                } else if (nextOutput instanceof org.hl7.fhir.r4b.model.DataType) {
                    var dt = (org.hl7.fhir.r4b.model.DataType) nextOutput;
                    if (dt instanceof StringType) {
                        StringType st = (StringType) dt;
                        if (st.getValue().equalsIgnoreCase(""))
                            ParamUtils.add(traceValue, "empty-string");
                        else
                            ParamUtils.add(traceValue, dt.fhirType(), st);
                    } else {
                        ParamUtils.add(traceValue, dt.fhirType(), dt);
                    }
                }
            }
            return true;
        }
        return false;

    }

    @Override
    public TypeDetails resolveConstantType(FHIRPathEngine engine, Object appContext, String name,
            FHIRPathConstantEvaluationMode mode) throws PathEngineException {
        if (mapVariables != null) {
            var key = name.substring(1);
            if (mapVariables.containsKey(key)) {
                org.hl7.fhir.r4b.model.Base itemValue = mapVariables.get(key);
                if (itemValue != null)
                    return new TypeDetails(CollectionStatus.SINGLETON, itemValue.fhirType());
            }
        }
        return new TypeDetails(null);
    }

    @Override
    public FunctionDetails resolveFunction(FHIRPathEngine engine, String functionName) {
        throw new UnsupportedOperationException("Unimplemented method 'resolveFunction'");
    }

    @Override
    public TypeDetails checkFunction(FHIRPathEngine engine, Object appContext, String functionName, TypeDetails focus,
            List<TypeDetails> parameters) throws PathEngineException {
        throw new UnsupportedOperationException("Unimplemented method 'checkFunction'");
    }

    @Override
    public List<Base> executeFunction(FHIRPathEngine engine, Object appContext, List<Base> focus, String functionName,
            List<List<Base>> parameters) {
        throw new UnsupportedOperationException("Unimplemented method 'executeFunction'");
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
        throw new UnsupportedOperationException("Unimplemented method 'paramIsType'");
    }
}
