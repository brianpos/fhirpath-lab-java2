package com.fhirpathlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.context.TestConfiguration;

@WebMvcTest(FmlTransformController.class)
@Import(FmlTransformControllerTest.TestConfig.class)
class FmlTransformControllerTest extends TestBase {

    private static final String OPERATION_OUTCOME_FILE_EXTENSION =
            "http://hl7.org/fhir/StructureDefinition/operationoutcome-file";

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {

        @Bean
        public ContextFactory contextFactory() {
            return new ContextFactory();
        }
    }

    @Test
    void testTransform2() throws Exception {
        String jsonContent = ReadTestFile("transform", "request", "json");
        String expectedResponse = ReadTestFile("transform", "response", "json");

        var result = mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent));

        WriteTestFile("transform", "response.actual", "json", result.andReturn().getResponse().getContentAsString());

        result.andExpect(status().isOk())
                .andExpect(content().json(expectedResponse));
    }

    @Test
    void transformsUsingSrcMapWithTypedResponse() throws Exception {
        var parser = new org.hl7.fhir.r4b.formats.JsonParser();
        var request = (org.hl7.fhir.r4b.model.Parameters) parser.parse(ReadTestFile("transform", "request", "json"));
        request.getParameter("map").setName("srcMap");

        mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parser.composeString(request)))
                .andExpect(status().isOk())
                .andExpect(content().json(ReadTestFile("transform", "response", "json")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "content", "resource" })
    void transformsInlineResourceInXmlRequest(String parameterName) throws Exception {
        var request = readTransformRequest();
        request.getParameter("content").setName(parameterName);
        var parser = new org.hl7.fhir.r4b.formats.XmlParser();

        var result = mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_XML)
                .content(parser.composeString(request)))
                .andExpect(status().isOk())
                .andReturn();

        var patient = transformedPatient(parser.parse(result.getResponse().getContentAsString()),
                new org.hl7.fhir.r4b.formats.JsonParser(), "content".equals(parameterName));
        assertEquals("Simpson", patient.getNameFirstRep().getFamily());
    }

    @ParameterizedTest
    @CsvSource({
        "content, json, json",
        "content, application/json, json",
        "content, text/json, json",
        "content, application/fhir+json, json",
        "content, application/example+json, json",
        "content, ' APPLICATION/FHIR+JSON; charset=UTF-8 ', json",
        "content, xml, xml",
        "content, application/xml, xml",
        "content, text/xml, xml",
        "content, application/fhir+xml, xml",
        "content, application/example+xml, xml",
        "content, ' APPLICATION/FHIR+XML; charset=UTF-8 ', xml",
        "resource, json, json",
        "resource, xml, xml"
    })
    void transformsBinaryContent(String parameterName, String contentType, String format) throws Exception {
        var request = readTransformRequest();
        var parameter = request.getParameter("content").setName(parameterName);
        var input = assertInstanceOf(org.hl7.fhir.r4b.model.Patient.class, parameter.getResource());
        input.getNameFirstRep().setFamily("Garc\u00eda");
        org.hl7.fhir.r4b.formats.IParser payloadParser = "json".equals(format)
                ? new org.hl7.fhir.r4b.formats.JsonParser() : new org.hl7.fhir.r4b.formats.XmlParser();
        String payload = payloadParser.composeString(input);
        if ("json".equals(format)) {
            payload = " \r\n" + payload;
        }
        parameter.setResource(new org.hl7.fhir.r4b.model.Binary()
                .setContentType(contentType)
                .setData(payload.getBytes(StandardCharsets.UTF_8)));

        var patient = transformedPatient(performTransform(request), payloadParser, "content".equals(parameterName));
        assertEquals("Garc\u00eda", patient.getNameFirstRep().getFamily());
    }

    @Test
    void preservesBinaryXmlEncoding() throws Exception {
        var request = readTransformRequest();
        String xml = """
                <?xml version="1.0" encoding="UTF-16"?>
                <Patient xmlns="http://hl7.org/fhir">
                  <id value="example"/>
                  <name><family value="Garc\u00eda"/></name>
                </Patient>
                """;
        request.getParameter("content").setResource(new org.hl7.fhir.r4b.model.Binary()
                .setContentType("application/xml; charset=UTF-16")
                .setData(xml.getBytes(StandardCharsets.UTF_16)));

        var patient = transformedPatient(performTransform(request), new org.hl7.fhir.r4b.formats.XmlParser(), true);
        assertEquals("Garc\u00eda", patient.getNameFirstRep().getFamily());
    }

    @ParameterizedTest
    @ValueSource(strings = { "json", "xml" })
    void returnsLogicalOutputAsBinaryForContentRequests(String format) throws Exception {
        var request = readTransformRequest();
        var model = new org.hl7.fhir.r4b.model.StructureDefinition();
        model.setUrl("http://example.org/StructureDefinition/Greeting");
        model.setName("Greeting");
        model.setStatus(org.hl7.fhir.r4b.model.Enumerations.PublicationStatus.ACTIVE);
        model.setKind(org.hl7.fhir.r4b.model.StructureDefinition.StructureDefinitionKind.LOGICAL);
        model.setAbstract(false);
        model.setType("Greeting");
        model.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/Element");
        model.setDerivation(org.hl7.fhir.r4b.model.StructureDefinition.TypeDerivationRule.SPECIALIZATION);
        var root = model.getSnapshot().addElement().setPath("Greeting").setMin(0).setMax("*");
        root.setId("Greeting");
        var message = model.getSnapshot().addElement().setPath("Greeting.message").setMin(0).setMax("1");
        message.setId("Greeting.message");
        message.addType().setCode("string");
        request.addParameter().setName("model").setResource(model);
        request.getParameter("map").setValue(new org.hl7.fhir.r4b.model.StringType("""
                map "http://example.org/StructureMap/greeting" = "GreetingMap"
                uses "http://hl7.org/fhir/StructureDefinition/Patient" as source
                uses "http://example.org/StructureDefinition/Greeting" as target
                group patientMap(source src : Patient, target tgt : Greeting) {
                  src -> tgt.message = 'Garc\u00eda' "message";
                }
                """));
        var parameter = request.getParameter("content");
        org.hl7.fhir.r4b.formats.IParser inputParser = "json".equals(format)
                ? new org.hl7.fhir.r4b.formats.JsonParser() : new org.hl7.fhir.r4b.formats.XmlParser();
        parameter.setResource(new org.hl7.fhir.r4b.model.Binary().setContentType(format)
                .setData(inputParser.composeString(parameter.getResource()).getBytes(StandardCharsets.UTF_8)));

        var response = assertInstanceOf(org.hl7.fhir.r4b.model.Parameters.class, performTransform(request));
        assertEquals("return", response.getParameterFirstRep().getName());
        assertEquals(1, response.getParameters("return").size());
        assertEquals(0, response.getParameters("result").size());
        var returned = response.getParameter("return");
        assertFalse(returned.hasValue());
        var binary = assertInstanceOf(org.hl7.fhir.r4b.model.Binary.class, returned.getResource());
        String result = new String(binary.getData(), StandardCharsets.UTF_8);
        assertEquals("application/" + format, binary.getContentType());
        if ("json".equals(format)) {
            var json = com.google.gson.JsonParser.parseString(result).getAsJsonObject();
            assertEquals("Garc\u00eda", json.get("message").getAsString());
        } else {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(binary.getData()));
            assertEquals("Greeting", document.getDocumentElement().getLocalName());
            assertEquals("Garc\u00eda", ((org.w3c.dom.Element) document.getElementsByTagNameNS("*", "message").item(0))
                    .getAttribute("value"));
        }

        parameter.setName("resource");
        var legacyResponse = assertInstanceOf(org.hl7.fhir.r4b.model.Parameters.class, performTransform(request));
        assertEquals(0, legacyResponse.getParameters("return").size());
        assertEquals(1, legacyResponse.getParameters("result").size());
        assertArrayEquals(legacyResponse.getParameterValue("result").primitiveValue().getBytes(StandardCharsets.UTF_8),
                binary.getData());
    }

    @ParameterizedTest
    @ValueSource(strings = { "json", "xml" })
    void returnsSameFhirContentInRequestedRepresentation(String format) throws Exception {
        var request = readTransformRequest();
        var parameter = request.getParameter("content");
        org.hl7.fhir.r4b.formats.IParser payloadParser = "json".equals(format)
                ? new org.hl7.fhir.r4b.formats.JsonParser() : new org.hl7.fhir.r4b.formats.XmlParser();
        parameter.setResource(new org.hl7.fhir.r4b.model.Binary().setContentType(format)
                .setData(payloadParser.composeString(parameter.getResource()).getBytes(StandardCharsets.UTF_8)));

        var response = assertInstanceOf(org.hl7.fhir.r4b.model.Parameters.class, performTransform(request));
        assertEquals(0, response.getParameters("result").size());
        assertEquals(1, response.getParameters("return").size());
        var returned = assertInstanceOf(org.hl7.fhir.r4b.model.Bundle.class, response.getParameter("return").getResource());

        parameter.setName("resource");
        var legacyResponse = assertInstanceOf(org.hl7.fhir.r4b.model.Parameters.class, performTransform(request));
        assertEquals(0, legacyResponse.getParameters("return").size());
        assertEquals(1, legacyResponse.getParameters("result").size());
        var legacyResult = payloadParser.parse(legacyResponse.getParameterValue("result").primitiveValue());
        assertTrue(legacyResult.equalsDeep(returned), "Both request formats must return the same transformed resource");
    }

    @ParameterizedTest
    @ValueSource(strings = { "json", "xml" })
    void transformsLegacyStringResource(String format) throws Exception {
        var request = readTransformRequest();
        var parameter = request.getParameter("content").setName("resource");
        org.hl7.fhir.r4b.formats.IParser payloadParser = "json".equals(format)
                ? new org.hl7.fhir.r4b.formats.JsonParser() : new org.hl7.fhir.r4b.formats.XmlParser();
        String payload = payloadParser.composeString(parameter.getResource());
        parameter.setResource(null).setValue(new org.hl7.fhir.r4b.model.StringType(" \r\n" + payload + "\r\n "));

        var patient = transformedPatient(performTransform(request), payloadParser, false);
        assertEquals("Simpson", patient.getNameFirstRep().getFamily());
    }

    @ParameterizedTest
    @CsvSource({ "content, content", "resource, resource", "content, resource", "resource, content" })
    void rejectsDuplicateInputResources(String firstName, String secondName) throws Exception {
        var request = readTransformRequest();
        request.getParameter("content").setName(firstName);
        request.addParameter().setName(secondName).setResource(new org.hl7.fhir.r4b.model.Patient());

        assertInputError(request, "Provide exactly one content or resource parameter");
    }

    @Test
    void rejectsMissingInputResource() throws Exception {
        var request = readTransformRequest();
        request.getParameter().remove(request.getParameter("content"));

        assertInputError(request, "Missing content or resource parameter");
    }

    @ParameterizedTest
    @ValueSource(strings = { "content", "resource" })
    void rejectsInvalidInputResourceType(String parameterName) throws Exception {
        var request = readTransformRequest();
        request.getParameter("content").setName(parameterName).setResource(null)
                .setValue(new org.hl7.fhir.r4b.model.IntegerType(1));

        assertInputError(request, "Parameter '" + parameterName + "' must contain a resource"
                + ("resource".equals(parameterName) ? " or valueString" : ""));
    }

    @Test
    void requiresResourceForContentParameter() throws Exception {
        var request = readTransformRequest();
        var parameter = request.getParameter("content");
        String payload = new org.hl7.fhir.r4b.formats.JsonParser().composeString(parameter.getResource());
        parameter.setResource(null).setValue(new org.hl7.fhir.r4b.model.StringType(payload));

        assertInputError(request, "Parameter 'content' must contain a resource");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " " })
    void rejectsBinaryWithoutContentType(String contentType) throws Exception {
        var request = readTransformRequest();
        request.getParameter("content").setResource(new org.hl7.fhir.r4b.model.Binary()
                .setContentType(contentType).setData("{}".getBytes(StandardCharsets.UTF_8)));

        assertInputError(request, "Binary input must specify a contentType");
    }

    @ParameterizedTest
    @ValueSource(strings = { "text/plain", "application/octet-stream", "jsonish", "application/jsonp" })
    void rejectsUnsupportedBinaryContentType(String contentType) throws Exception {
        var request = readTransformRequest();
        request.getParameter("content").setResource(new org.hl7.fhir.r4b.model.Binary()
                .setContentType(contentType).setData("{}".getBytes(StandardCharsets.UTF_8)));

        assertInputError(request, "Unsupported Binary contentType '" + contentType + "': expected JSON or XML");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void rejectsBinaryWithoutData(boolean emptyData) throws Exception {
        var request = readTransformRequest();
        request.getParameter("content").setResource(new org.hl7.fhir.r4b.model.Binary()
                .setContentType("json").setData(emptyData ? new byte[0] : null));

        assertInputError(request, "Binary input must contain data");
    }

    @ParameterizedTest
    @ValueSource(strings = { "json", "xml" })
    void rejectsBinaryContentTypeMismatch(String contentType) throws Exception {
        var request = readTransformRequest();
        var parameter = request.getParameter("content");
        org.hl7.fhir.r4b.formats.IParser payloadParser = "json".equals(contentType)
                ? new org.hl7.fhir.r4b.formats.XmlParser() : new org.hl7.fhir.r4b.formats.JsonParser();
        byte[] payload = payloadParser.composeString(parameter.getResource()).getBytes(StandardCharsets.UTF_8);
        parameter.setResource(new org.hl7.fhir.r4b.model.Binary().setContentType(contentType).setData(payload));

        var outcome = assertInstanceOf(org.hl7.fhir.r4b.model.OperationOutcome.class, performTransform(request));
        assertEquals(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR,
                outcome.getIssueFirstRep().getSeverity());
    }

    @ParameterizedTest
    @CsvSource({
        "source, true", "source, false",
        "sourceMap, true", "sourceMap, false",
        "supportingMap, true", "supportingMap, false"
    })
    void rejectsUnsupportedStandardMapParameters(String parameterName, boolean includeLegacyRequest) throws Exception {
        var parser = new org.hl7.fhir.r4b.formats.JsonParser();
        var request = includeLegacyRequest
                ? (org.hl7.fhir.r4b.model.Parameters) parser.parse(ReadTestFile("transform", "request", "json"))
                : new org.hl7.fhir.r4b.model.Parameters();
        var parameter = request.addParameter().setName(parameterName);
        if ("source".equals(parameterName)) {
            parameter.setValue(new org.hl7.fhir.r4b.model.UriType("http://example.org/StructureMap/primary"));
        } else {
            parameter.setResource(new org.hl7.fhir.r4b.model.StructureMap()
                    .setUrl("http://example.org/StructureMap/primary"));
        }

        var result = mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parser.composeString(request)))
                .andExpect(status().isOk())
                .andReturn();

        var outcome = (org.hl7.fhir.r4b.model.OperationOutcome) parser
                .parse(result.getResponse().getContentAsString());
        assertEquals(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR,
                outcome.getIssueFirstRep().getSeverity());
        assertEquals("This implementation does not support the standard parameter '" + parameterName + "'",
                outcome.getIssueFirstRep().getDetails().getText());
    }

    @ParameterizedTest
    @ValueSource(strings = { "map", "srcMap" })
    void rejectsNonStringMaps(String parameterName) throws Exception {
        var request = new org.hl7.fhir.r4b.model.Parameters();
        request.addParameter().setName(parameterName).setValue(new org.hl7.fhir.r4b.model.IntegerType(1));
        request.addParameter().setName("resource").setResource(new org.hl7.fhir.r4b.model.Patient());
        var parser = new org.hl7.fhir.r4b.formats.JsonParser();

        var result = mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parser.composeString(request)))
                .andExpect(status().isOk())
                .andReturn();

        var outcome = (org.hl7.fhir.r4b.model.OperationOutcome) parser
                .parse(result.getResponse().getContentAsString());
        assertEquals("Map parameter 1 must contain a valueString", outcome.getIssueFirstRep().getDetails().getText());
    }

    @Test
    void rejectsMissingMaps() throws Exception {
        var request = new org.hl7.fhir.r4b.model.Parameters();
        request.addParameter().setName("resource").setResource(new org.hl7.fhir.r4b.model.Patient());
        var parser = new org.hl7.fhir.r4b.formats.JsonParser();

        var result = mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parser.composeString(request)))
                .andExpect(status().isOk())
                .andReturn();

        var outcome = (org.hl7.fhir.r4b.model.OperationOutcome) parser
                .parse(result.getResponse().getContentAsString());
        assertEquals("Missing map or srcMap parameter", outcome.getIssueFirstRep().getDetails().getText());
    }

            @ParameterizedTest
            @CsvSource({ "map, map", "srcMap, srcMap", "map, srcMap", "srcMap, map" })
            void transformsUsingGroupFromSupportingMap(String primaryParameterName, String supportingParameterName)
                    throws Exception {
            String primaryMap = """
                map "http://example.org/StructureMap/primary" = "Primary"

                uses "http://hl7.org/fhir/StructureDefinition/Patient" as source
                uses "http://hl7.org/fhir/StructureDefinition/Bundle" as target
                imports "http://example.org/StructureMap/supporting"

                group patientMap(source src : Patient, target bundle : Bundle) {
                                    src -> bundle.id = 'from-primary' then SetBundleType(src, bundle) "set-bundle-type";
                }
                """;
            String supportingMap = """
                map "http://example.org/StructureMap/supporting" = "Supporting"

                group SetBundleType(source src : Patient, target bundle : Bundle) {
                                    src -> bundle.type = 'batch' "set-type";
                }
                """;

            var request = new org.hl7.fhir.r4b.model.Parameters();
            addMap(request, primaryParameterName, primaryMap, "primary.map");
            addMap(request, supportingParameterName, supportingMap, "supporting.map");
            request.addParameter().setName("resource")
                .setResource(new org.hl7.fhir.r4b.model.Patient().setId("input"));
            var parser = new org.hl7.fhir.r4b.formats.JsonParser();

            var result = mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parser.composeString(request)))
                .andExpect(status().isOk())
                .andReturn();

            var response = (org.hl7.fhir.r4b.model.Parameters) parser
                .parse(result.getResponse().getContentAsString());
            String transformedJson = response.getParameterValue("result").primitiveValue();
            var transformed = (org.hl7.fhir.r4b.model.Bundle) parser.parse(transformedJson);
            assertEquals("from-primary", transformed.getIdElement().getIdPart());
            assertEquals(org.hl7.fhir.r4b.model.Bundle.BundleType.BATCH, transformed.getType());
            }

            @ParameterizedTest
            @ValueSource(strings = { "map", "srcMap" })
            void associatesTransformErrorsWithPrimaryMapFile(String parameterName) throws Exception {
            var request = new org.hl7.fhir.r4b.model.Parameters();
            addMap(request, parameterName, "not a valid map", "primary.map");
            request.addParameter().setName("resource").setResource(new org.hl7.fhir.r4b.model.Patient());
            var parser = new org.hl7.fhir.r4b.formats.JsonParser();

            var result = mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parser.composeString(request)))
                .andExpect(status().isOk())
                .andReturn();

            var outcome = (org.hl7.fhir.r4b.model.OperationOutcome) parser
                .parse(result.getResponse().getContentAsString());
            var fileExtension = outcome.getIssueFirstRep().getExtensionByUrl(OPERATION_OUTCOME_FILE_EXTENSION);
            assertNotNull(fileExtension);
            assertEquals("primary.map", fileExtension.getValue().primitiveValue());
            }

            @ParameterizedTest
            @CsvSource({ "map, map", "srcMap, srcMap", "map, srcMap", "srcMap, map" })
            void associatesSupportingMapParseErrorsWithSupportingMapFile(String primaryParameterName,
                    String supportingParameterName) throws Exception {
            String primaryMap = """
                map "http://example.org/StructureMap/primary" = "Primary"
                imports "http://example.org/StructureMap/supporting"
                group patientMap(source src, target tgt) {
                  src -> tgt "copy";
                }
                """;
            var request = new org.hl7.fhir.r4b.model.Parameters();
            addMap(request, primaryParameterName, primaryMap, "primary.map");
            addMap(request, supportingParameterName, "not a valid map", "supporting.map");
            request.addParameter().setName("resource")
                .setResource(new org.hl7.fhir.r4b.model.Patient().setId("input"));
            var parser = new org.hl7.fhir.r4b.formats.JsonParser();

            var result = mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parser.composeString(request)))
                .andExpect(status().isOk())
                .andReturn();

            var outcome = (org.hl7.fhir.r4b.model.OperationOutcome) parser
                .parse(result.getResponse().getContentAsString());
            var fileExtension = outcome.getIssueFirstRep().getExtensionByUrl(OPERATION_OUTCOME_FILE_EXTENSION);
            assertNotNull(fileExtension);
            assertEquals("supporting.map", fileExtension.getValue().primitiveValue());
            }

    private org.hl7.fhir.r4b.model.Parameters readTransformRequest() throws Exception {
        return (org.hl7.fhir.r4b.model.Parameters) new org.hl7.fhir.r4b.formats.JsonParser()
                .parse(ReadTestFile("transform", "request", "json"));
    }

    private org.hl7.fhir.r4b.model.Resource performTransform(org.hl7.fhir.r4b.model.Parameters request) throws Exception {
        var parser = new org.hl7.fhir.r4b.formats.JsonParser();
        var result = mockMvc.perform(post("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parser.composeString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return parser.parse(result.getResponse().getContentAsString());
    }

    private org.hl7.fhir.r4b.model.Patient transformedPatient(org.hl7.fhir.r4b.model.Resource response,
            org.hl7.fhir.r4b.formats.IParser resultParser, boolean standardInput) throws Exception {
        var parameters = assertInstanceOf(org.hl7.fhir.r4b.model.Parameters.class, response);
        org.hl7.fhir.r4b.model.Bundle bundle;
        if (standardInput) {
            assertEquals("return", parameters.getParameterFirstRep().getName());
            assertEquals(0, parameters.getParameters("result").size());
            assertEquals(1, parameters.getParameters("return").size());
            assertFalse(parameters.getParameter("return").hasValue());
            bundle = assertInstanceOf(org.hl7.fhir.r4b.model.Bundle.class, parameters.getParameter("return").getResource());
        } else {
            assertEquals("outcome", parameters.getParameterFirstRep().getName());
            assertEquals("result", parameters.getParameter().get(1).getName());
            assertEquals(0, parameters.getParameters("return").size());
            assertEquals(1, parameters.getParameters("result").size());
            assertFalse(parameters.getParameter("result").hasResource());
            bundle = assertInstanceOf(org.hl7.fhir.r4b.model.Bundle.class,
                    resultParser.parse(parameters.getParameterValue("result").primitiveValue()));
        }
        assertEquals("smile", bundle.getIdElement().getIdPart());
        assertEquals(org.hl7.fhir.r4b.model.Bundle.BundleType.TRANSACTION, bundle.getType());
        assertEquals(1, bundle.getEntry().size());
        var patient = assertInstanceOf(org.hl7.fhir.r4b.model.Patient.class, bundle.getEntryFirstRep().getResource());
        assertEquals("example", patient.getIdElement().getIdPart());
        return patient;
    }

    private void assertInputError(org.hl7.fhir.r4b.model.Parameters request, String message) throws Exception {
        var outcome = assertInstanceOf(org.hl7.fhir.r4b.model.OperationOutcome.class, performTransform(request));
        assertEquals(org.hl7.fhir.r4b.model.OperationOutcome.IssueSeverity.ERROR,
                outcome.getIssueFirstRep().getSeverity());
        assertEquals(message, outcome.getIssueFirstRep().getDetails().getText());
    }

            private static void addMap(org.hl7.fhir.r4b.model.Parameters request, String parameterName, String map,
                    String fileName) {
            request.addParameter().setName(parameterName)
                .setValue(new org.hl7.fhir.r4b.model.StringType(map))
                .addExtension(OPERATION_OUTCOME_FILE_EXTENSION, new org.hl7.fhir.r4b.model.StringType(fileName));
            }
}
