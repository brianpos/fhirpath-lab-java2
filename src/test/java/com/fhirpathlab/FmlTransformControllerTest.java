package com.fhirpathlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
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
            void transformsUsingGroupFromSupportingMap() throws Exception {
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
            addMap(request, primaryMap, "primary.map");
            addMap(request, supportingMap, "supporting.map");
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

            @Test
            void associatesTransformErrorsWithPrimaryMapFile() throws Exception {
            var request = new org.hl7.fhir.r4b.model.Parameters();
            addMap(request, "not a valid map", "primary.map");
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

            @Test
            void associatesSupportingMapParseErrorsWithSupportingMapFile() throws Exception {
            String primaryMap = """
                map "http://example.org/StructureMap/primary" = "Primary"
                imports "http://example.org/StructureMap/supporting"
                group patientMap(source src, target tgt) {
                  src -> tgt "copy";
                }
                """;
            var request = new org.hl7.fhir.r4b.model.Parameters();
            addMap(request, primaryMap, "primary.map");
            addMap(request, "not a valid map", "supporting.map");
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

            private static void addMap(org.hl7.fhir.r4b.model.Parameters request, String map, String fileName) {
            request.addParameter().setName("map")
                .setValue(new org.hl7.fhir.r4b.model.StringType(map))
                .addExtension(OPERATION_OUTCOME_FILE_EXTENSION, new org.hl7.fhir.r4b.model.StringType(fileName));
            }
}
