package com.fhirpathlab;

import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r4b.model.Parameters.ParametersParameterComponent;

public class ParamUtils {

  private ParamUtils() {
  }

  public static ParametersParameterComponent add(Parameters theParameter, String theName) {
    var result = theParameter.addParameter();
    result.setName(theName);
    return result;
  }

  public static ParametersParameterComponent addPart(Parameters.ParametersParameterComponent theParameter,
      String theName, org.hl7.fhir.r4b.model.DataType theValue) {
    var result = theParameter.addPart();
    result.setName(theName);
    result.setValue(theValue);
    return result;
  }

  public static ParametersParameterComponent addPart(Parameters.ParametersParameterComponent theParameter,
      String theName, String theValue) {
    var result = theParameter.addPart();
    result.setName(theName);
    result.setValue(new StringType(theValue));
    return result;
  }

  public static ParametersParameterComponent addPart(Parameters.ParametersParameterComponent theParameter,
      String theName) {
    var result = theParameter.addPart();
    result.setName(theName);
    return result;
  }

  public static ParametersParameterComponent addPartResource(Parameters.ParametersParameterComponent theParameter,
      String theName, org.hl7.fhir.r4b.model.Resource theValue) {
    var result = theParameter.addPart();
    result.setName(theName);
    result.setResource(theValue);
    return result;
  }

  public static ParametersParameterComponent add(Parameters theParameter,
      String theName, org.hl7.fhir.r4b.model.Resource theValue) {
    var result = theParameter.addParameter();
    result.setName(theName);
    result.setResource(theValue);
    return result;
  }
}
