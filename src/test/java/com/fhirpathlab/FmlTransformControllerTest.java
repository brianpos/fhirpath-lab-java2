package com.fhirpathlab;

import org.hl7.fhir.exceptions.FHIRException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.Assert;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.context.TestConfiguration;

import java.io.File;
import com.google.common.io.Files;
import java.nio.charset.Charset;

@WebMvcTest(FmlTransformController.class)
@Import(FmlTransformControllerTest.TestConfig.class)
class FmlTransformControllerTest extends TestBase {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {

        @Bean
        public FhirpathLabSimpleWorkerContextR5 simpleWorkerContext() throws java.io.IOException, FHIRException {
            // Return the real instance of FhirpathLabSimpleWorkerContextR5
            return new FhirpathLabSimpleWorkerContextR5();
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
}
