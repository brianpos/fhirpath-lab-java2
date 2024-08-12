package com.fhirpathlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.Assert;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import com.google.common.io.Files;
import java.nio.charset.Charset;

import org.hl7.fhir.exceptions.FHIRException;

@WebMvcTest(FhirpathLabController.class)
@Import(FhirpathLabControllerTest.TestConfig.class)
class FhirpathLabControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {

        @Bean
        public FhirpathLabSimpleWorkerContextR4B simpleWorkerContext() throws java.io.IOException, FHIRException {
            // Return the real instance of FhirpathLabSimpleWorkerContextR4B
            return new FhirpathLabSimpleWorkerContextR4B();
        }
    }

    private static String ReadTestFile(String testName, String testSuffix, String format) {
        try {
            String workingDir = System.getProperty("user.dir");
            return Files.asCharSource(new File(
                    workingDir + "/src/test/data/" + testName + "." + testSuffix + "." + format),
                    Charset.defaultCharset()).read();
        } catch (Exception e) {
            Assert.isTrue(false, e.getMessage());
            return null;
        }
    }

    @Test
    void testHelloEndpoint() throws Exception {
        mockMvc.perform(get("/fhir/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, FHIRPath Lab!{\"resourceType\":\"Patient\",\"id\":\"pat1\"}"));
    }

    @Test
    void testPutPatient() throws Exception {
        String jsonContent = "{\"resourceType\":\"Patient\",\"id\":\"pat1\"}";

        mockMvc.perform(put("/fhir/patient")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(content().string("Received patient with ID: pat1"));
    }

    @Test
    void testSimple() throws Exception {
        String jsonContent = ReadTestFile("simple", "request", "json");
        String expectedResponse = ReadTestFile("simple", "response", "json");

        mockMvc.perform(put("/fhir/fhirpath")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponse));
    }
}
