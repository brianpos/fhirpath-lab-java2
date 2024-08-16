package com.fhirpathlab;

import java.io.IOException;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.Resource;
import org.springframework.stereotype.Service;

@Service
public class FhirpathLabSimpleWorkerContextR5 extends org.hl7.fhir.r5.context.SimpleWorkerContext {

  public FhirpathLabSimpleWorkerContextR5() throws IOException, FHIRException {
    super(new SimpleWorkerContextBuilder().fromNothing());
  }

  public void unloadBinaries(){
    this.binaries.clear();
  }

  @Override
  public <T extends Resource> T fetchResource(Class<T> className, String uri) {
    T r = super.fetchResource(className, uri);
    if (r != null)
      return r;
    return null;
  }
}
