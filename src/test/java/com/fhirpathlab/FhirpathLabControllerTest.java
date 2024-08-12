package com.fhirpathlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FhirpathLabController.class)
public class FhirpathLabControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // @MockBean
    // private MySingletonService mySingletonService;

    @Test
    public void testHelloEndpoint() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, FHIRPath Lab!{\"resourceType\":\"Patient\",\"id\":\"pat1\"}"));
    }

    @Test
    public void testPutPatient() throws Exception {
        String jsonContent = "{\"resourceType\":\"Patient\",\"id\":\"pat1\"}";

        mockMvc.perform(put("/api/patient")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(content().string("Received patient with ID: pat1"));
    }

}
