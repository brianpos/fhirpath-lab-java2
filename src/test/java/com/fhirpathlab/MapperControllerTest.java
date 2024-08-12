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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.context.TestConfiguration;

import java.io.File;
import com.google.common.io.Files;
import java.nio.charset.Charset;

@WebMvcTest(FhirpathLabTransformController.class)
@Import(MapperControllerTest.TestConfig.class)
class MapperControllerTest {

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
    void testTransform2() throws Exception {
        String jsonContent = ReadTestFile("transform", "request", "json");
        String expectedResponse = ReadTestFile("transform", "response", "json");

        mockMvc.perform(put("/fhir/$transform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponse));
    }
}
