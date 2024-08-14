package com.fhirpathlab;

import java.io.IOException;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.springframework.stereotype.Service;

@Service
public class FhirpathLabSimpleWorkerContextR5 extends org.hl7.fhir.r5.context.SimpleWorkerContext {

  public FhirpathLabSimpleWorkerContextR5() throws IOException, FHIRException {
    super(new SimpleWorkerContextBuilder().fromNothing());
    this.setProgress(true);
    
    // Initialize the package cache manager
    var pkgMgr = new FilesystemPackageCacheManager.Builder().build();
    pkgMgr.setMinimalMemory(true);
    var pkg = pkgMgr.loadPackage("hl7.fhir.r4b.core", "4.3.0");
    
    // Load the package (e.g., hl7.fhir.r4.core)
    this.loadFromPackage(pkg, null);
  }

  @Override
  public <T extends Resource> T fetchResource(Class<T> className, String uri) {
    T r = super.fetchResource(className, uri);
    if (r != null) return r;
    return null;
  }
}
