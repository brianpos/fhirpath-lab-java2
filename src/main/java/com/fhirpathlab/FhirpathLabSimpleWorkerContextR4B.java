package com.fhirpathlab;

import java.io.IOException;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.springframework.stereotype.Service;

@Service
public class FhirpathLabSimpleWorkerContextR4B extends org.hl7.fhir.r4b.context.SimpleWorkerContext {

  public FhirpathLabSimpleWorkerContextR4B() throws IOException, FHIRException {
    super();
    this.setProgress(true);
    
    // Initialize the package cache manager
    var pkgMgr = new FilesystemPackageCacheManager.Builder().build();
    pkgMgr.setMinimalMemory(true);
    var pkg = pkgMgr.loadPackage("hl7.fhir.r4b.core", "4.3.0");
    
    // Load the package (e.g., hl7.fhir.r4.core)
    this.loadFromPackage(pkg, null);
  }
}
