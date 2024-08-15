package com.fhirpathlab;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

// This pretty printer will format to the same style as done by the VSCode json formatter
// so that standardizing things is nice n easy
public class MyPrettyPrinter extends DefaultPrettyPrinter {

    public MyPrettyPrinter() {
      DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter("  ", System.getProperty("line.separator"));
      indentObjectsWith(indenter);
      indentArraysWith(indenter);
      _objectFieldValueSeparatorWithSpaces = ": ";
    }
  
    private MyPrettyPrinter(MyPrettyPrinter pp) {
      super(pp);
    }
  
    @Override
    public void writeEndArray(JsonGenerator g, int nrOfValues) throws java.io.IOException {
      if (!_arrayIndenter.isInline()) {
        --_nesting;
      }
      if (nrOfValues > 0) {
        _arrayIndenter.writeIndentation(g, _nesting);
      }
      g.writeRaw(']');
    }
  
    @Override
    public DefaultPrettyPrinter createInstance() {
      return new MyPrettyPrinter(this);
    }
  }