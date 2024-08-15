package com.fhirpathlab;

import java.io.IOException;

import org.hl7.fhir.r4b.context.IWorkerContext;
import org.hl7.fhir.r4b.elementmodel.Manager;
import org.hl7.fhir.r4b.formats.IParser.OutputStyle;
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

    public static ParametersParameterComponent add(Parameters.ParametersParameterComponent theParameter,
            String theName, org.hl7.fhir.r4b.model.DataType theValue) {
        var result = theParameter.addPart();
        result.setName(theName);
        result.setValue(theValue);
        return result;
    }

    public static ParametersParameterComponent add(Parameters.ParametersParameterComponent theParameter,
            String theName, String theValue) {
        var result = theParameter.addPart();
        result.setName(theName);
        result.setValue(new StringType(theValue));
        return result;
    }

    public static ParametersParameterComponent add(Parameters.ParametersParameterComponent theParameter,
            String theName) {
        var result = theParameter.addPart();
        result.setName(theName);
        return result;
    }

    public static ParametersParameterComponent add(Parameters.ParametersParameterComponent theParameter,
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

    public static ParametersParameterComponent addTypedElement(IWorkerContext context,
            org.hl7.fhir.r4b.elementmodel.ObjectConverter oc,
            Parameters.ParametersParameterComponent theParameter,
            org.hl7.fhir.r4b.elementmodel.Element em) {
        var result = theParameter.addPart();
        result.setName(em.fhirType());

        try {
            if (em.isResource()) {
                result.setResource(oc.convert(em));

            } else if (em.fhirType().equalsIgnoreCase("BackboneElement")) {
                var stream = new java.io.ByteArrayOutputStream();
                Manager.compose(context, em, stream, Manager.FhirFormat.JSON, OutputStyle.NORMAL, em.fhirType());
                String backboneJson = stream.toString().replace("\"resourceType\":\"BackboneElement\",", "");
                result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                        new StringType(backboneJson));

            } else if (em.fhirType().equalsIgnoreCase("Extension")) {
                var stream = new java.io.ByteArrayOutputStream();
                Manager.compose(context, em, stream, Manager.FhirFormat.JSON, OutputStyle.NORMAL, em.fhirType());
                String extensionJson = stream.toString().replace("\"resourceType\":\"Extension\",", "");
                result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                        new StringType(extensionJson));

            } else if (em.isPrimitive()) {
                var dt = oc.convertToType(em);
                if (dt instanceof StringType) {
                    StringType st = (StringType) dt;
                    if (st.getValue().equalsIgnoreCase(""))
                        result.setName("empty-string");
                    else
                        result.setValue(st);
                } else {
                    result.setValue(dt);
                }

            } else {
                // Let over are the bigger DataTypes such as Identifier/HumanName/Period etc.
                var dt = em.asType();
                result.setValue(dt);
            }

        } catch (IOException e) {
            ParamUtils.add(result, "cast-error", new StringType(e.getMessage()));
        } catch (java.lang.IllegalArgumentException e) {
            ParamUtils.add(result, "cast-error", new StringType(e.getMessage()));
        }

        return result;
    }

}
