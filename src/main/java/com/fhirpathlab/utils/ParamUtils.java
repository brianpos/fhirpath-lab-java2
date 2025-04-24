package com.fhirpathlab.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.utilities.graphql.Parser;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.ValidatedFragment;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.r4b.model.Enumerations.FHIRVersion;
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

    public static ParametersParameterComponent add(ParametersParameterComponent theParameter, String theName,
            DataType theValue) {
        var result = theParameter.addPart();
        result.setName(theName);
        if (theValue instanceof org.hl7.fhir.r5.model.StringType) {
            org.hl7.fhir.r5.model.StringType st = (org.hl7.fhir.r5.model.StringType) theValue;
            if (st.getValue().equalsIgnoreCase("")) {
                result.setName("empty-string");
                // result.setValue(new StringType("empty-string"));
            } else {
                var valWithExt = new StringType(st.getValue());
                result.setValue(valWithExt);
                // what about any extensions?
            }
        } else {
            // and need to convert the DataType to the correct type
            // ByteArrayOutputStream bs = new ByteArrayOutputStream();
            // org.hl7.fhir.r5.formats.JsonParser jp5 = new
            // org.hl7.fhir.r5.formats.JsonParser();
            // org.hl7.fhir.r4b.formats.JsonParser jp4 = new
            // org.hl7.fhir.r4b.formats.JsonParser();
            // try {
            // jp5.compose(bs, theValue, "root");
            // ByteArrayInputStream bi = new ByteArrayInputStream(bs.toByteArray());
            // var dt4 = jp4.parseType(bi, theValue.fhirType());
            // ParamUtils.add(result, theValue.fhirType(), dt4);
            // } catch (IOException e) {
            // }
            result.setValue(converDataType(theValue));
            // ParamUtils.add(result, theValue.fhirType(), converDataType(theValue));
        }

        // what about any extensions?
        if (theValue.hasExtension()) {

        }
        return result;
    }

    public static org.hl7.fhir.r4b.model.DataType converDataType(org.hl7.fhir.r5.model.DataType theValue) {
        org.hl7.fhir.r4b.model.DataType result = null;
        org.hl7.fhir.r5.formats.JsonParser jp5 = new org.hl7.fhir.r5.formats.JsonParser();
        org.hl7.fhir.r4b.formats.JsonParser jp4 = new org.hl7.fhir.r4b.formats.JsonParser();

        if (theValue.isPrimitive()) {

        }
        if (theValue instanceof org.hl7.fhir.r5.model.StringType) {
            org.hl7.fhir.r5.model.StringType st = (org.hl7.fhir.r5.model.StringType) theValue;
            if (st.getValue().equalsIgnoreCase("")) {
                result = new StringType("empty-string");
            } else {
                if (theValue instanceof org.hl7.fhir.r5.model.CodeType)
                    result = new org.hl7.fhir.r4b.model.CodeType(st.getValue());
                else if (theValue instanceof org.hl7.fhir.r5.model.MarkdownType)
                    result = new org.hl7.fhir.r4b.model.MarkdownType(st.getValue());
                else
                    result = new StringType(st.getValue());
            }
        } else if (theValue instanceof org.hl7.fhir.r5.model.UriType) {
            org.hl7.fhir.r5.model.UriType st = (org.hl7.fhir.r5.model.UriType) theValue;
            if (st.getValue().equalsIgnoreCase("")) {
                result = new StringType("empty-string");
            } else {
                if (theValue instanceof org.hl7.fhir.r5.model.CanonicalType)
                    result = new org.hl7.fhir.r4b.model.CanonicalType(st.getValue());

                else if (theValue instanceof org.hl7.fhir.r5.model.IdType)
                    result = new org.hl7.fhir.r4b.model.IdType(st.getValue());

                else if (theValue instanceof org.hl7.fhir.r5.model.OidType)
                    result = new org.hl7.fhir.r4b.model.OidType(st.getValue());

                else if (theValue instanceof org.hl7.fhir.r5.model.SidType) {
                    var newSid = new org.hl7.fhir.r4b.model.SidType();
                    newSid.fromStringValue(st.getValue());
                    result = newSid;
                }

                else if (theValue instanceof org.hl7.fhir.r5.model.UrlType)
                    result = new org.hl7.fhir.r4b.model.UrlType(st.getValue());

                else if (theValue instanceof org.hl7.fhir.r5.model.UuidType)
                    result = new org.hl7.fhir.r4b.model.UuidType(st.getValue());

                else
                    result = new org.hl7.fhir.r4b.model.UriType(st.getValue());
            }
        } else if (theValue instanceof org.hl7.fhir.r5.model.IntegerType) {
            org.hl7.fhir.r5.model.IntegerType st = (org.hl7.fhir.r5.model.IntegerType) theValue;
            result = new org.hl7.fhir.r4b.model.IntegerType(st.getValue());
        } else if (theValue instanceof org.hl7.fhir.r5.model.DecimalType) {
            org.hl7.fhir.r5.model.DecimalType st = (org.hl7.fhir.r5.model.DecimalType) theValue;
            result = new org.hl7.fhir.r4b.model.DecimalType(st.getValue());
        } else if (theValue instanceof org.hl7.fhir.r5.model.Integer64Type) {
            org.hl7.fhir.r5.model.Integer64Type st = (org.hl7.fhir.r5.model.Integer64Type) theValue;
            result = new org.hl7.fhir.r4b.model.Integer64Type(st.getValue());
        } else if (theValue instanceof org.hl7.fhir.r5.model.BooleanType) {
            org.hl7.fhir.r5.model.BooleanType st = (org.hl7.fhir.r5.model.BooleanType) theValue;
            result = new org.hl7.fhir.r4b.model.BooleanType(st.getValue());
        } else if (theValue instanceof org.hl7.fhir.r5.model.Base64BinaryType) {
            org.hl7.fhir.r5.model.Base64BinaryType st = (org.hl7.fhir.r5.model.Base64BinaryType) theValue;
            result = new org.hl7.fhir.r4b.model.Base64BinaryType(st.getValue());
        } else if (theValue instanceof org.hl7.fhir.r5.model.DateType) {
            org.hl7.fhir.r5.model.DateType st = (org.hl7.fhir.r5.model.DateType) theValue;
            result = new org.hl7.fhir.r4b.model.DateType(st.getValue(), st.getPrecision());
        } else if (theValue instanceof org.hl7.fhir.r5.model.DateTimeType) {
            org.hl7.fhir.r5.model.DateTimeType st = (org.hl7.fhir.r5.model.DateTimeType) theValue;
            result = new org.hl7.fhir.r4b.model.DateTimeType(st.getValue(), st.getPrecision());
        } else if (theValue instanceof org.hl7.fhir.r5.model.InstantType) {
            org.hl7.fhir.r5.model.InstantType st = (org.hl7.fhir.r5.model.InstantType) theValue;
            result = new org.hl7.fhir.r4b.model.InstantType(st.getValue());
        } else if (theValue instanceof org.hl7.fhir.r5.model.TimeType) {
            org.hl7.fhir.r5.model.TimeType st = (org.hl7.fhir.r5.model.TimeType) theValue;
            result = new org.hl7.fhir.r4b.model.TimeType(st.getValue());
        } else {

            // I think we just have enumeration and Xhtml as the left over types...

            // and need to convert the DataType to the correct type
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            try {
                jp5.compose(bs, theValue, "root");
                ByteArrayInputStream bi = new ByteArrayInputStream(bs.toByteArray());
                result = jp4.parseType(bi, theValue.fhirType());
            } catch (IOException e) {
            }
        }
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

    @SuppressWarnings("deprecation")
    public static ParametersParameterComponent addTypedElement(IWorkerContext context,
            org.hl7.fhir.r5.elementmodel.ObjectConverter oc,
            Parameters.ParametersParameterComponent theParameter,
            org.hl7.fhir.r5.elementmodel.Element em) {
        var result = theParameter.addPart();
        result.setName(em.fhirType());

        try {
            var jsonParser = new org.hl7.fhir.r4b.formats.JsonParser();
            var stream = new java.io.ByteArrayOutputStream();
            Manager.compose(context, em, stream, Manager.FhirFormat.JSON, OutputStyle.NORMAL, em.fhirType());

            if (em.isResource()) {
                // if this is an R4 resource, we can put it into the object model
                if (em.getProperty().getStructure().getFhirVersion().toCode().charAt(0) == '4') {
                    var resource = jsonParser.parse(stream.toString());
                    result.setResource(resource);
                } else {
                    result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                            new StringType(stream.toString()));
                }

            } else if (em.fhirType().equalsIgnoreCase("BackboneElement")) {
                String backboneJson = stream.toString().replace("\"resourceType\":\"BackboneElement\",", "");
                result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                        new StringType(backboneJson));

            } else if (em.fhirType().equalsIgnoreCase("Extension")) {
                String extensionJson = stream.toString().replace("\"resourceType\":\"Extension\",", "");
                result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                        new StringType(extensionJson));

            } else if (em.isPrimitive()) {
                var dt = oc.convertToType(em);
                var r4dt = converDataType(dt);
                result.setValue(r4dt);
                // if (dt instanceof org.hl7.fhir.r5.model.StringType) {
                // org.hl7.fhir.r5.model.StringType st = (org.hl7.fhir.r5.model.StringType) dt;
                // if (st.getValue().equalsIgnoreCase(""))
                // result.setName("empty-string");
                // else {
                // var valWithExt = new StringType(st.getValue());
                // result.setValue(valWithExt);
                // // what about any extensions?
                // }
                // } else {
                // // and need to convert the DataType to the correct type
                // var val = jsonParser.parseAnyType(stream.toString(), dt.fhirType());
                // result.setValue(val);
                // }

            } else {
                // Let over are the bigger DataTypes such as Identifier/HumanName/Period etc.
                try {
                    var val = jsonParser.parseAnyType(stream.toString(), em.fhirType());
                    result.setValue(val);
                } catch (FHIRFormatError ex) {
                    result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                            new StringType(stream.toString()));
                }
            }

        } catch (IOException e) {
            ParamUtils.add(result, "cast-error", new StringType(e.getMessage()));
        } catch (java.lang.IllegalArgumentException e) {
            ParamUtils.add(result, "cast-error", new StringType(e.getMessage()));
        }

        return result;
    }

    @SuppressWarnings("deprecation")
    public static ParametersParameterComponent addTypedElement(IWorkerContext context,
            @SuppressWarnings("deprecation") org.hl7.fhir.r4b.elementmodel.ObjectConverter oc,
            Parameters.ParametersParameterComponent theParameter,
            @SuppressWarnings("deprecation") org.hl7.fhir.r4b.elementmodel.Element em) {
        var result = theParameter.addPart();
        result.setName(em.fhirType());

        try {
            if (em.isResource()) {
                result.setResource(oc.convert(em));

            } else if (em.fhirType().equalsIgnoreCase("BackboneElement")) {
                var stream = new java.io.ByteArrayOutputStream();
                // Manager.compose(context, em, stream, Manager.FhirFormat.JSON,
                // OutputStyle.NORMAL, em.fhirType());
                String backboneJson = stream.toString().replace("\"resourceType\":\"BackboneElement\",", "");
                result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                        new StringType(backboneJson));

            } else if (em.fhirType().equalsIgnoreCase("Extension")) {
                var stream = new java.io.ByteArrayOutputStream();
                // Manager.compose(context, em, stream, Manager.FhirFormat.JSON,
                // OutputStyle.NORMAL, em.fhirType());
                String extensionJson = stream.toString().replace("\"resourceType\":\"Extension\",", "");
                result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                        new StringType(extensionJson));

            } else if (em.isPrimitive()) {
                @SuppressWarnings("deprecation")
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
                @SuppressWarnings("deprecation")
                var dt = em.asType();
                result.setValue(dt);
            }

            // } catch (IOException e) {
            // ParamUtils.add(result, "cast-error", new StringType(e.getMessage()));
        } catch (java.lang.IllegalArgumentException e) {
            ParamUtils.add(result, "cast-error", new StringType(e.getMessage()));
        }

        return result;
    }
}
