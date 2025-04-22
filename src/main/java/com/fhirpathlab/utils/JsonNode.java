package com.fhirpathlab.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

interface IJsonNode extends Serializable {
    String getExpressionType();

    void setExpressionType(String expressionType);

    String getName();

    void setName(String name);

    List<JsonNode> getArguments();

    void setArguments(List<JsonNode> arguments);

    String getReturnType();

    void setReturnType(String returnType);

    Integer getPosition();

    void setPosition(Integer position);

    Integer getLength();

    void setLength(Integer length);

    Integer getLine();

    void setLine(Integer line);

    Integer getColumn();

    void setColumn(Integer column);
}

public class JsonNode implements IJsonNode {
    private String expressionType;
    private String name;
    private List<JsonNode> arguments;
    private String returnType;
    private Integer position;
    private Integer length;
    private Integer line;
    private Integer column;

    public void insertArgument(JsonNode node) {
        if (arguments == null)
            arguments = new ArrayList<>();
        arguments.add(0, node);
    }

    public void appendArgument(JsonNode node) {
        if (arguments == null)
            arguments = new ArrayList<>();
        arguments.add(node);
    }

    @Override
    public String getExpressionType() {
        return expressionType;
    }

    @JsonProperty("ExpressionType")
    @Override
    public void setExpressionType(String expressionType) {
        this.expressionType = expressionType;
    }

    @Override
    public String getName() {
        return name;
    }

    @JsonProperty("Name")
    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<JsonNode> getArguments() {
        return arguments;
    }

    @JsonProperty("Arguments")
    @Override
    public void setArguments(List<JsonNode> arguments) {
        this.arguments = arguments;
    }

    @Override
    public String getReturnType() {
        return returnType;
    }

    @JsonProperty("ReturnType")
    @Override
    public void setReturnType(String returnType) {
        if (returnType != null && returnType.length() == 0)
            this.returnType = " ";
        else
            this.returnType = returnType;
    }

    @Override
    public Integer getPosition() {
        return position;
    }

    @JsonProperty("Position")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Override
    public void setPosition(Integer position) {
        this.position = position;
    }

    @Override
    public Integer getLength() {
        return length;
    }

    @JsonProperty("Length")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Override
    public void setLength(Integer length) {
        this.length = length;
    }

    @Override
    public Integer getLine() {
        return line;
    }

    @JsonProperty("Line")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Override
    public void setLine(Integer line) {
        this.line = line;
    }

    @Override
    public Integer getColumn() {
        return column;
    }

    @JsonProperty("Column")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Override
    public void setColumn(Integer column) {
        this.column = column;
    }
}