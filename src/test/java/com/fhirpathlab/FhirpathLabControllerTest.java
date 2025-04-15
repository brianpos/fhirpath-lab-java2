package com.fhirpathlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FhirpathTestController.class)
@Import(FhirpathLabControllerTest.TestConfig.class)
class FhirpathLabControllerTest extends TestBase {

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
    void testSimple() throws Exception {
        String jsonContent = ReadTestFile("simple", "request", "json");
        String expectedResponse = ReadTestFile("simple", "response", "json");

        var result = mockMvc.perform(post("/fhir/$fhirpath")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent));

        // Uncomment the following line to write the actual response to a file for debugging
        // WriteTestFile("simple", "response.actual", "json", result.andReturn().getResponse().getContentAsString());

        result.andExpect(status().isOk())
                .andExpect(content().json(expectedResponse));
    }
}
