package com.fhirpathlab;

import java.util.List;

import org.hl7.fhir.r4b.model.Parameters;
import org.hl7.fhir.r4b.model.StringType;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.terminologies.ConceptMapEngine;
import org.hl7.fhir.r5.utils.structuremap.ITransformerServices;
import org.hl7.fhir.r5.elementmodel.Manager;

public class TransformSupportServicesR5 implements ITransformerServices {

    private Parameters.ParametersParameterComponent traceToParameter;
    private List<Base> outputs;
    private SimpleWorkerContext context;

    public TransformSupportServicesR5(SimpleWorkerContext worker, List<Base> outputs) {
      this.context = worker;
      this.outputs = outputs;
    }

    public void setTraceToParameter(Parameters.ParametersParameterComponent theTraceToParameter) {
      traceToParameter = theTraceToParameter;
  }

  @Override
    public Base createType(Object appInfo, String name) throws FHIRException {
      StructureDefinition sd = context.fetchResource(StructureDefinition.class, name);
      return Manager.build(context, sd);
    }

    @Override
    public Base createResource(Object appInfo, Base res, boolean atRootOfTransform) {
      if (atRootOfTransform)
        outputs.add(res);
      return res;
    }

    @Override
    public Coding translate(Object appInfo, Coding source, String conceptMapUrl) throws FHIRException {
      ConceptMapEngine cme = new ConceptMapEngine(context);
      return cme.translate(source, conceptMapUrl);
    }

    @Override
    public Base resolveReference(Object appContext, String url) throws FHIRException {
      org.hl7.fhir.r5.model.Resource resource = context.fetchResource(org.hl7.fhir.r5.model.Resource.class, url);
      return resource;
      // if (resource != null) {
      // String inStr =
      // FhirContext.forR4Cached().newJsonParser().encodeResourceToString(resource);
      // try {
      // return Manager.parseSingle(context, new
      // ByteArrayInputStream(inStr.getBytes()), FhirFormat.JSON);
      // } catch (IOException e) {
      // throw new FHIRException("Cannot convert resource to element model");
      // }
      // }
      // throw new FHIRException("resolveReference, url not found: " + url);
    }

    @Override
    public List<Base> performSearch(Object appContext, String url) throws FHIRException {
      throw new FHIRException("performSearch is not supported yet");
    }

    @Override
    public void log(String message) {
      if (traceToParameter != null) {
        Parameters.ParametersParameterComponent traceValue = traceToParameter.addPart();
        traceValue.setName("debug");
        traceValue.setValue(new StringType(message));
      }
    }
  }
