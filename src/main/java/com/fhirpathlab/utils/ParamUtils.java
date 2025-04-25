package com.fhirpathlab.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.r5.model.PrimitiveType;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StringType;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4b.model.Factory;
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
        setParameterDataTypeValue(result, theValue);
        return result;
    }

    public static ParametersParameterComponent add(ParametersParameterComponent theParameter, String theName,
            DataType theValue) {
        var result = theParameter.addPart();
        result.setName(theName);
        var r4dt = converDataType(theValue);
        setParameterDataTypeValue(result, r4dt);
        return result;
    }

    @SuppressWarnings("rawtypes")
    public static org.hl7.fhir.r4b.model.DataType converDataType(org.hl7.fhir.r5.model.DataType theValue) {
        org.hl7.fhir.r4b.model.DataType result = null;
        org.hl7.fhir.r5.formats.JsonParser jp5 = new org.hl7.fhir.r5.formats.JsonParser();
        org.hl7.fhir.r4b.formats.JsonParser jp4 = new org.hl7.fhir.r4b.formats.JsonParser();

        if (theValue.isPrimitive()) {
            var np = new Factory().create(theValue.fhirType());
            if (np instanceof org.hl7.fhir.r4b.model.PrimitiveType) {
                ((org.hl7.fhir.r4b.model.PrimitiveType) np)
                        .setValueAsString(((org.hl7.fhir.r5.model.PrimitiveType) theValue).asStringValue());
                result = np;
            }
            if (theValue.hasExtension()) {
                // migrate any extensions to the new type
                for (var ext : theValue.getExtension()) {
                    var newExt = new org.hl7.fhir.r4b.model.Extension(ext.getUrl(), converDataType(ext.getValue()));
                    ((org.hl7.fhir.r4b.model.PrimitiveType) np).addExtension(newExt);
                }
            }
        } else {

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
            var jsonText = stream.toString();

            if (em.getPath() != null) {
                result.addExtension("http://fhir.forms-lab.com/StructureDefinition/resource-path",
                        new StringType(em.getPath().replace("[x]", "")));
            }
            if (em.isResource()) {
                // if this is an R4 resource, we can put it into the object model
                if (em.getProperty().getStructure().getFhirVersion().toCode().charAt(0) == '4') {
                    var resource = jsonParser.parse(jsonText);
                    result.setResource(resource);
                } else {
                    result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                            new StringType(jsonText));
                }

            } else if (em.fhirType().equalsIgnoreCase("BackboneElement")) {
                String backboneJson = jsonText.replace("\"resourceType\":\"BackboneElement\",", "");
                result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                        new StringType(backboneJson));

            } else if (em.fhirType().equalsIgnoreCase("Extension")) {
                String extensionJson = jsonText.replace("\"resourceType\":\"Extension\",", "");
                result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                        new StringType(extensionJson));

            } else if (em.isPrimitive()) {
                var dt = convertToType(em);
                var r4dt = converDataType(dt);
                setParameterDataTypeValue(result, r4dt);

            } else {
                // Let over are the bigger DataTypes such as Identifier/HumanName/Period etc.
                try {
                    var val = jsonParser.parseAnyType(jsonText, em.fhirType());
                    result.setValue(val); // throw's if the type isn't a valid params type, in which case go the json
                                          // thing...
                } catch (Error ex) {
                    jsonText = jsonText.replace("\"resourceType\":\"" + em.fhirType() + "\",", "");
                    result.addExtension("http://fhir.forms-lab.com/StructureDefinition/json-value",
                            new StringType(jsonText));
                }
            }

        } catch (IOException e) {
            ParamUtils.add(result, "cast-error", new StringType(e.getMessage()));
        } catch (java.lang.IllegalArgumentException e) {
            ParamUtils.add(result, "cast-error", new StringType(e.getMessage()));
        }

        return result;
    }

    public static DataType convertToType(org.hl7.fhir.r5.elementmodel.Element element) throws FHIRException {
        DataType b = new org.hl7.fhir.r5.model.Factory().create(element.fhirType());
        if (b instanceof PrimitiveType) {
            ((PrimitiveType) b).setValueAsString(element.primitiveValue());
            // If there are extenions, walk them too...
            for (org.hl7.fhir.r5.elementmodel.Element child : element.getChildren()) {
                if (Utilities.existsInList(child.getName(), "extension", "modifierExtension")) {
                    var urlChild = child.getNamedChild("url");
                    if (urlChild != null) {
                        // this is a child extension, so add it to the parent
                        org.hl7.fhir.r5.model.Extension ext = new org.hl7.fhir.r5.model.Extension(urlChild.getValue());
                        ((PrimitiveType) b).addExtension(ext);
                        // set the value of the extension
                        var valueChild = child.getNamedChild("value");
                        if (valueChild != null) {
                            // this is a child extension, so add it to the parent
                            var value = convertToType(valueChild);
                            if (value != null) {
                                ext.setValue(value);
                            }
                        }
                    }
                }
            }
        } else {
            for (org.hl7.fhir.r5.elementmodel.Element child : element.getChildren()) {
                b.setProperty(child.getName(), convertToType(child));
            }
        }
        return b;
    }

    /**
     * This is a helper method to set the value of the parameter. It will set the
     * name to "empty-string" if the value is an empty string.
     * 
     * @param result The ParametersParameterComponent to set the value for.
     * @param dt     The DataType to set as the value.
     */
    private static void setParameterDataTypeValue(ParametersParameterComponent result,
            org.hl7.fhir.r4b.model.DataType dt) {
        result.setValue(dt);
        if (dt instanceof StringType) {
            StringType st = (StringType) dt;
            if (st.getValue().equalsIgnoreCase("")) {
                result.setName("empty-string");
                if (!st.hasExtension())
                    result.setValue(null);
            }
        }
    }
}
