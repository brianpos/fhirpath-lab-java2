package com.fhirpathlab;

import java.io.IOException;

import org.hl7.fhir.exceptions.FHIRException;
import org.springframework.stereotype.Service;

@Service
public class FhirpathLabSimpleWorkerContextR4B extends org.hl7.fhir.r4b.context.SimpleWorkerContext {

  public FhirpathLabSimpleWorkerContextR4B() throws IOException, FHIRException {
    super();
  }
}
