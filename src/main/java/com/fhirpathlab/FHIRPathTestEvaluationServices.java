package com.fhirpathlab;

import java.util.List;
import java.util.HashMap;

import org.hl7.fhir.r4b.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r4b.fhirpath.FHIRPathEngine.IEvaluationContext;
import org.hl7.fhir.r4b.fhirpath.FHIRPathUtilityClasses.FunctionDetails;
import org.hl7.fhir.r4b.fhirpath.TypeDetails;
import org.hl7.fhir.r4b.model.Base;
import org.hl7.fhir.r4b.model.DataType;
import org.hl7.fhir.r4b.model.Enumeration;
import org.hl7.fhir.r4b.model.Extension;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r4b.model.ValueSet;

import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.PathEngineException;

public class FHIRPathTestEvaluationServices implements IEvaluationContext {
  public Parameters.ParametersParameterComponent traceToParameter;
  public java.util.HashMap<String, org.hl7.fhir.r4b.model.Base> mapVariables;

  public FHIRPathTestEvaluationServices() {
    mapVariables = new HashMap<>();
  }

  public static ParametersParameterComponent addPart(Parameters.ParametersParameterComponent theParameter,
      String theName, org.hl7.fhir.r4b.model.DataType theValue) {
    var result = theParameter.addPart();
    result.setName(theName);
    result.setValue(theValue);
    return result;
  }

  public static ParametersParameterComponent addPart(Parameters.ParametersParameterComponent theParameter,
      String theName) {
    var result = theParameter.addPart();
    result.setName(theName);
    return result;
  }

  private static ParametersParameterComponent addPartResource(Parameters.ParametersParameterComponent theParameter,
      String theName, org.hl7.fhir.r4b.model.Resource theValue) {
    var result = theParameter.addPart();
    result.setName(theName);
    result.setResource(theValue);
    return result;
  }

  @Override
  public List<org.hl7.fhir.r4b.model.Base> resolveConstant(FHIRPathEngine engine, Object appContext, String name,
      boolean beforeContext, boolean explicitConstant)
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
      Parameters.ParametersParameterComponent traceValue = addPart(traceToParameter, "trace", new StringType(argument));
      org.hl7.fhir.r4b.formats.IParser parser = new org.hl7.fhir.r4b.formats.JsonParser();

      for (org.hl7.fhir.r4b.model.Base nextOutput : data) {
        try {
          if (nextOutput instanceof org.hl7.fhir.r4b.model.Resource) {
            addPartResource(traceValue, nextOutput.fhirType(), (org.hl7.fhir.r4b.model.Resource) nextOutput);

          } else if (nextOutput instanceof org.hl7.fhir.r4b.model.BackboneElement) {
            Parameters.ParametersParameterComponent backboneValue = traceValue.addPart();
            backboneValue.setName(nextOutput.fhirType());
            // String backboneJson = parser.composeString(nextOutput,
            // nextOutput.fhirType());
            // backboneValue.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
            // new StringType(backboneJson));

          } else if (nextOutput instanceof StringType) {
            StringType st = (StringType) nextOutput;
            if (st.getValue() == "")
              addPart(traceValue, "empty-string");
            else
              addPart(traceValue, nextOutput.fhirType(), (StringType) nextOutput);

          } else if (nextOutput instanceof Enumeration) {
            var en = (org.hl7.fhir.r4b.model.ICoding) nextOutput;
            addPart(traceValue, nextOutput.fhirType(), new org.hl7.fhir.r4b.model.CodeType(en.getCode()));

          } else if (nextOutput instanceof Extension) {
            var ext = (org.hl7.fhir.r4b.model.Extension) nextOutput;
            addPart(traceValue, nextOutput.fhirType()).addExtension(ext);

          } else if (nextOutput instanceof DataType) {
            var dt = (DataType) nextOutput;
            addPart(traceValue, nextOutput.fhirType(), dt);

          } else {
            addPart(traceValue, nextOutput.fhirType(), (DataType) nextOutput);

          }
        } catch (java.lang.Exception e) {
          addPart(traceValue, "cast-error", new StringType(e.getMessage()));
          // ParametersUtil.addParameterToParameters(ctx, resultPart,
          // nextOutput.fhirType());
        }
      }
      return true;
    }
    return false;

  }

  @Override
  public TypeDetails resolveConstantType(FHIRPathEngine engine, Object appContext, String name,
      boolean explicitConstant) throws PathEngineException {
    throw new UnsupportedOperationException("Unimplemented method 'resolveConstantType'");
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
}
