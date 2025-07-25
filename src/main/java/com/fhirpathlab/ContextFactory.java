package com.fhirpathlab;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.fhir.ucum.UcumEssenceService;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.IContextResourceLoader;
import org.hl7.fhir.r5.context.SimpleWorkerContext.PackageResourceLoader;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.terminologies.client.TerminologyClientManager.ITerminologyClientFactory;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.NpmPackage.PackageResourceInformation;
import org.springframework.stereotype.Service;

import com.google.gson.JsonSyntaxException;

@Service
public class ContextFactory {

  public ContextFactory() {
    super();
  }

  private org.hl7.fhir.r4b.context.SimpleWorkerContext contextR4b;
  private org.hl7.fhir.r5.context.SimpleWorkerContext contextR4basR5;
  private org.hl7.fhir.r5.context.SimpleWorkerContext contextR5;
  private UcumEssenceService ucumService;
  private NpmPackage r4NpmPackage;
  private NpmPackage r5NpmPackage;

  private NpmPackage getNpmPackageR4() throws FHIRException, IOException {
    if (r4NpmPackage == null) {
      var pkgMgr = new FilesystemPackageCacheManager.Builder().build();
      pkgMgr.setMinimalMemory(true);
      r4NpmPackage = pkgMgr.loadPackage("hl7.fhir.r4b.core", "4.3.0");
    }
    return r4NpmPackage;
  }

  private NpmPackage getNpmPackageR5() throws FHIRException, IOException {
    if (r5NpmPackage == null) {
      var pkgMgr = new FilesystemPackageCacheManager.Builder().build();
      pkgMgr.setMinimalMemory(true);
      r5NpmPackage = pkgMgr.loadPackage("hl7.fhir.r5.core", "5.0.0");
    }
    return r5NpmPackage;
  }

  public org.hl7.fhir.r4b.context.SimpleWorkerContext getContextR4b() throws FHIRException, IOException {
    if (contextR4b == null) {
      var context = new org.hl7.fhir.r4b.context.SimpleWorkerContext();
      context.setProgress(true);
      var pkg = getNpmPackageR4();
      context.loadFromPackage(pkg, new R4ContextLoader());
      context.getBinaries().clear();
      contextR4b = context;
      r4NpmPackage = null; // release the memory
    }
    return contextR4b;
  }

  private UcumEssenceService getUcumService() {
    if (ucumService == null) {
      // read the UCUM essence file `ucum-essence.xml` from the project resources 
      InputStream ucumStream = ContextFactory.class.getResourceAsStream("/ucum-essence.xml");
      try {
        ucumService = new UcumEssenceService(ucumStream);
      } catch (Exception e) {
        ucumService = null;
      }
    }
    return ucumService;
  }

  public org.hl7.fhir.r5.context.SimpleWorkerContext getContextR4bAsR5() throws FHIRException, IOException {
    if (contextR4basR5 == null) {
      var context = new FhirpathLabSimpleWorkerContextR5();
      context.setProgress(true);
      var pkg = getNpmPackageR4();
      context.loadFromPackage(pkg, new R5ContextLoader());
      context.unloadBinaries();
      contextR4basR5 = context;
      r4NpmPackage = null; // release the memory
      context.setUcumService(getUcumService());
    }
    return contextR4basR5;
  }

  public org.hl7.fhir.r5.context.SimpleWorkerContext getContextR5() throws FHIRException, IOException {
    if (contextR5 == null) {
      var context = new FhirpathLabSimpleWorkerContextR5();
      context.setProgress(true);
      var pkg = getNpmPackageR5();
      context.loadFromPackage(pkg, new R5ContextLoader());
      context.unloadBinaries();
      contextR5 = context;
      r4NpmPackage = null; // release the memory
      context.setUcumService(getUcumService());
    }
    return contextR5;
  }
}

class R4ContextLoader implements org.hl7.fhir.r4b.context.IWorkerContext.IContextResourceLoader {

  org.hl7.fhir.r4b.formats.JsonParser jsonParser = new org.hl7.fhir.r4b.formats.JsonParser();
  org.hl7.fhir.r4b.formats.XmlParser xmlParser = new org.hl7.fhir.r4b.formats.XmlParser();

  @Override
  public String[] getTypes() {
    return new String[] { "CodeSystem", "ValueSet", "ConceptMap", "StructureDefinition", "StructureMap", };
  }

  @Override
  public org.hl7.fhir.r4b.model.Bundle loadBundle(InputStream stream, boolean isJson)
      throws FHIRException, IOException {
    throw new UnsupportedOperationException("Unimplemented method 'loadBundle'");
  }

  @Override
  public org.hl7.fhir.r4b.model.Resource loadResource(InputStream stream, boolean isJson)
      throws FHIRException, IOException {
    if (isJson)
      return jsonParser.parse(stream);
    return xmlParser.parse(stream);
  }

  @Override
  public String getResourcePath(org.hl7.fhir.r4b.model.Resource resource) {
    throw new UnsupportedOperationException("Unimplemented method 'getResourcePath'");
  }

  @Override
  public org.hl7.fhir.r4b.context.IWorkerContext.IContextResourceLoader getNewLoader(NpmPackage npm)
      throws JsonSyntaxException, IOException {
    throw new UnsupportedOperationException("Unimplemented method 'getNewLoader'");
  }

}

class R5ContextLoader implements org.hl7.fhir.r5.context.IContextResourceLoader {

  org.hl7.fhir.r5.formats.JsonParser jsonParser = new org.hl7.fhir.r5.formats.JsonParser();
  org.hl7.fhir.r5.formats.XmlParser xmlParser = new org.hl7.fhir.r5.formats.XmlParser();

  @Override
  public Set<String> getTypes() {
    return org.hl7.fhir.utilities.Utilities.stringSet("ConceptMap", "StructureDefinition", "StructureMap");
  }

  @Override
  public Bundle loadBundle(InputStream stream, boolean isJson) throws FHIRException, IOException {
    throw new UnsupportedOperationException("Unimplemented method 'loadBundle'");
  }

  @Override
  public Resource loadResource(InputStream stream, boolean isJson) throws FHIRException, IOException {
    if (isJson)
      return jsonParser.parse(stream);
    return xmlParser.parse(stream);
  }

  @Override
  public String getResourcePath(Resource resource) {
    throw new UnsupportedOperationException("Unimplemented method 'getResourcePath'");
  }

  @Override
  public IContextResourceLoader getNewLoader(NpmPackage npm) throws JsonSyntaxException, IOException {
    throw new UnsupportedOperationException("Unimplemented method 'getNewLoader'");
  }

  @Override
  public List<CodeSystem> getCodeSystems() {
    throw new UnsupportedOperationException("Unimplemented method 'getCodeSystems'");
  }

  @Override
  public void setPatchUrls(boolean value) {
    throw new UnsupportedOperationException("Unimplemented method 'setPatchUrls'");
  }

  @Override
  public String patchUrl(String url, String resourceType) {
    return url;
  }

  @Override
  public IContextResourceLoader setLoadProfiles(boolean value) {
    throw new UnsupportedOperationException("Unimplemented method 'setLoadProfiles'");
  }

  @Override
  public ITerminologyClientFactory txFactory() {
    throw new UnsupportedOperationException("Unimplemented method 'txFactory'");
  }

  @Override
  public boolean wantLoad(NpmPackage pi, PackageResourceInformation pri) {
    return true;
  }

  @Override
  public PackageResourceLoader editInfo(PackageResourceLoader loader) {
    return loader;
  }

  @Override
  public Set<String> reviewActualTypes(Set<String> types) {
    return types;
  }

}