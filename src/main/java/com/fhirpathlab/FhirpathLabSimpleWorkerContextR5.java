package com.fhirpathlab;

import java.io.IOException;
import java.util.List;

import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.terminologies.utilities.ValidationResult;
import org.hl7.fhir.r5.utils.validation.ValidationContextCarrier;
import org.hl7.fhir.utilities.validation.ValidationOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FhirpathLabSimpleWorkerContextR5 extends org.hl7.fhir.r5.context.SimpleWorkerContext {

  public FhirpathLabSimpleWorkerContextR5(org.hl7.fhir.r5.context.SimpleWorkerContext other) throws IOException, FHIRException {
    super(other);
  }

  @Autowired
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

  @Override
  public ValidationResult validateCode(ValidationOptions options, Coding code, ValueSet vs) {
    return super.validateCode(options, code, vs);
  }

  @Override
  public ValidationResult validateCode(ValidationOptions arg0, CodeableConcept arg1, ValueSet arg2) {
    return super.validateCode(arg0, arg1, arg2);
  }

  @Override
  public ValidationResult validateCode(ValidationOptions options, String code, ValueSet vs) {
      return super.validateCode(options, code, vs);
  }

  @Override
  public ValidationResult validateCode(ValidationOptions optionsArg, Coding code, ValueSet vs,
          ValidationContextCarrier ctxt) {
      return super.validateCode(optionsArg, code, vs, ctxt);
  }

  @Override
  public ValidationResult validateCode(ValidationOptions options, String system, String version, String code,
          String display) {
      List<OperationOutcomeIssueComponent> issues = new java.util.ArrayList<>();
      return new ValidationResult(IssueSeverity.INFORMATION, "Not validating in the Mapper", issues);
  }

  @Override
  public ValidationResult validateCode(ValidationOptions options, String system, String version, String code,
          String display, ValueSet vs) {
      return super.validateCode(options, system, version, code, display, vs);
  }

  @Override
  public ValidationResult validateCode(ValidationOptions options, String path, Coding code, ValueSet vs) {
    return super.validateCode(options, path, code, vs);
  }
  @Override
  public ValidationResult validateCode(ValidationOptions arg0, String arg1, Coding arg2, ValueSet arg3,
      ValidationContextCarrier arg4) {
    return super.validateCode(arg0, arg1, arg2, arg3, arg4);
  }
}
