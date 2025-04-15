package com.fhirpathlab.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.Utilities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

interface ISimplifiedExpressionNode extends Serializable {

    String getName();

    void setName(String name);

    String getConstant();

    void setConstant(String constant);

    String getFunction();

    void setFunction(String function);

    boolean isProximal();

    void setProximal(boolean proximal);

    String getOperation();

    void setOperation(String operation);

    SimplifiedExpressionNode getInner();

    void setInner(SimplifiedExpressionNode value);

    SimplifiedExpressionNode getOpNext();

    void setOpNext(SimplifiedExpressionNode value);

    List<SimplifiedExpressionNode> getParameters();

    String getKind();

    void setKind(String kind);

    SimplifiedExpressionNode getGroup();

    void setGroup(SimplifiedExpressionNode group);

    String getUniqueId();

    String getTypes();

    void setTypes(String types);

    String getOpTypes();

    void setOpTypes(String types);

    Integer getStartLine();

    void setStartLine(Integer startLine);

    Integer getStartColumn();

    void setStartColumn(Integer startColumn);
}

public class SimplifiedExpressionNode implements ISimplifiedExpressionNode {

    // the expression will have one of either name or constant
    private String uniqueId;
    private String kind;
    private String name;
    private String constant;
    private String function;
    private List<SimplifiedExpressionNode> parameters; // will be created if there is a function
    private SimplifiedExpressionNode inner;
    private SimplifiedExpressionNode group;
    private String operation;
    private boolean proximal; // a proximal operation is the first in the sequence of operations. This is
                              // significant when evaluating the outcomes
    private SimplifiedExpressionNode opNext;
    private String types;
    private String opTypes;
    private Integer startLine;
    private Integer startColumn;

    public static SimplifiedExpressionNode from(org.hl7.fhir.r4b.fhirpath.ExpressionNode node) {
        if (node == null)
            return null;
        SimplifiedExpressionNode jsonNode = new SimplifiedExpressionNode();
        // jsonNode.uniqueId = node.getUniqueId();
        jsonNode.kind = node.getKind().toString();
        if (node.getKind() == org.hl7.fhir.r4b.fhirpath.ExpressionNode.Kind.Name
                || node.getKind() == org.hl7.fhir.r4b.fhirpath.ExpressionNode.Kind.Function
                        && (node.getFunction() == null || node.getFunction().toCode() == null))
            jsonNode.name = node.getName();
        var constVal = convertConstantToString(node.getConstant());
        if (constVal != null) {
            jsonNode.constant = constVal.value;
            jsonNode.types = constVal.type;
        }
        if (node.getFunction() != null)
            jsonNode.function = node.getFunction().toCode();

        var sp = node.getParameters();
        if (sp != null) {
            jsonNode.parameters = new ArrayList<>();
            for (org.hl7.fhir.r4b.fhirpath.ExpressionNode arg : node.getParameters()) {
                jsonNode.parameters.add(from(arg));
            }
        }

        jsonNode.inner = from(node.getInner());
        jsonNode.group = from(node.getGroup());
        if (node.getOperation() != null)
            jsonNode.operation = node.getOperation().toCode();
        jsonNode.proximal = node.isProximal();
        jsonNode.opNext = from(node.getOpNext());

        if (node.getTypes() != null) {
            jsonNode.types = getTypeNames(node.getTypes());
        }
        if (node.getOpTypes() != null) {
            jsonNode.opTypes = getTypeNames(node.getOpTypes());
        }

        return jsonNode;
    }

    public static String getTypeNames(org.hl7.fhir.r4b.fhirpath.TypeDetails types) {
        return types.getTypes().stream()
            .map(type -> {
                String typeName = type.startsWith("http://hl7.org/fhir/StructureDefinition/") 
                    ? type.replace("http://hl7.org/fhir/StructureDefinition/", "") 
                    : type;
                    if (typeName.startsWith("http://hl7.org/fhirpath/")) {
                        typeName = typeName.replace("http://hl7.org/fhirpath/", "");
                        typeName = typeName.substring(0, 1).toLowerCase() + typeName.substring(1);
                    }   
                    if (types.getCollectionStatus() == null || types.getCollectionStatus() == org.hl7.fhir.r4b.fhirpath.ExpressionNode.CollectionStatus.SINGLETON)
                        return typeName;
                    return typeName + "[]";
            })
            .distinct()
            .sorted()
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    public static SimplifiedExpressionNode from(org.hl7.fhir.r5.fhirpath.ExpressionNode node) {
        if (node == null)
            return null;
        SimplifiedExpressionNode jsonNode = new SimplifiedExpressionNode();
        jsonNode.uniqueId = node.getUniqueId();
        jsonNode.kind = node.getKind().toString();
        if (node.getKind() == org.hl7.fhir.r5.fhirpath.ExpressionNode.Kind.Name
                || node.getKind() == org.hl7.fhir.r5.fhirpath.ExpressionNode.Kind.Function
                        && (node.getFunction() == null || node.getFunction().toCode() == null))
            jsonNode.name = node.getName();
        var constVal = convertConstantToString(node.getConstant());
        if (constVal != null) {
            jsonNode.constant = constVal.value;
            jsonNode.types = constVal.type;
        }
        if (node.getFunction() != null)
            jsonNode.function = node.getFunction().toCode();

        var sp = node.getParameters();
        if (sp != null) {
            jsonNode.parameters = new ArrayList<>();
            for (org.hl7.fhir.r5.fhirpath.ExpressionNode arg : node.getParameters()) {
                jsonNode.parameters.add(from(arg));
            }
        }

        jsonNode.inner = from(node.getInner());
        jsonNode.group = from(node.getGroup());
        if (node.getOperation() != null)
            jsonNode.operation = node.getOperation().toCode();
        jsonNode.proximal = node.isProximal();
        jsonNode.opNext = from(node.getOpNext());

        if (node.getTypes() != null) {
            jsonNode.types = getTypeNames(node.getTypes());
        }
        if (node.getOpTypes() != null) {
            jsonNode.opTypes = getTypeNames(node.getOpTypes());
        }

        return jsonNode;
    }

    public static String getTypeNames(org.hl7.fhir.r5.fhirpath.TypeDetails types) {
        return types.getTypes().stream()
            .map(type -> {
                String typeName = type.startsWith("http://hl7.org/fhir/StructureDefinition/") 
                    ? type.replace("http://hl7.org/fhir/StructureDefinition/", "") 
                    : type;
                    if (typeName.startsWith("http://hl7.org/fhirpath/")) {
                        typeName = typeName.replace("http://hl7.org/fhirpath/", "");
                        typeName = typeName.substring(0, 1).toLowerCase() + typeName.substring(1);
                    }   
                if (types.getCollectionStatus() == null || types.getCollectionStatus() == org.hl7.fhir.r5.fhirpath.ExpressionNode.CollectionStatus.SINGLETON)
                    return typeName;
                return typeName + "[]";
            })
            .distinct()
            .sorted()
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    static class TypedValue {
        public static TypedValue create(String type, String value) {
            TypedValue tv = new TypedValue();
            tv.type = type;
            tv.value = value;
            return tv;
        }

        public String type;
        public String value;
    }

    static TypedValue convertConstantToString(org.hl7.fhir.r4b.model.Base constant) {
        if (constant == null)
            return null;

        StringBuilder b = new StringBuilder();
        if (constant instanceof org.hl7.fhir.r4b.model.StringType) {
            b.append(constant.primitiveValue());
        } else if (constant instanceof org.hl7.fhir.r4b.model.Quantity) {
            org.hl7.fhir.r4b.model.Quantity q = (org.hl7.fhir.r4b.model.Quantity) constant;
            b.append(q.getValue().toPlainString());
            if (q.hasUnit() || q.hasCode()) {
                b.append(" '");
                if (q.hasUnit()) {
                    b.append(q.getUnit());
                } else {
                    b.append(q.getCode());
                }
                b.append("'");
            }
        } else if (constant.primitiveValue() != null) {
            b.append(constant.primitiveValue());
        } else {
            b.append(Utilities.escapeJson(constant.toString()));
        }
        return TypedValue.create(constant.fhirType(), b.toString());
    }

    static TypedValue convertConstantToString(org.hl7.fhir.r5.model.Base constant) {
        if (constant == null)
            return null;

        StringBuilder b = new StringBuilder();
        if (constant instanceof org.hl7.fhir.r5.model.StringType) {
            b.append(constant.primitiveValue());
        } else if (constant instanceof org.hl7.fhir.r5.model.Quantity) {
            org.hl7.fhir.r5.model.Quantity q = (org.hl7.fhir.r5.model.Quantity) constant;
            b.append(q.getValue().toPlainString());
            if (q.hasUnit() || q.hasCode()) {
                b.append(" '");
                if (q.hasUnit()) {
                    b.append(q.getUnit());
                } else {
                    b.append(q.getCode());
                }
                b.append("'");
            }
        } else if (constant.primitiveValue() != null) {
            b.append(constant.primitiveValue());
        } else {
            b.append(Utilities.escapeJson(constant.toString()));
        }
        return TypedValue.create(constant.fhirType(), b.toString());
    }

    @JsonProperty("Name")
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("Constant")
    @Override
    public String getConstant() {
        return constant;
    }

    @Override
    public void setConstant(String constant) {
        this.constant = constant;
    }

    @JsonProperty("Function")
    @Override
    public String getFunction() {
        return function;
    }

    @Override
    public void setFunction(String function) {
        this.function = function;
    }

    @JsonProperty("IsProximal")
    @Override
    public boolean isProximal() {
        return proximal;
    }

    @Override
    public void setProximal(boolean proximal) {
        this.proximal = proximal;
    }

    @JsonProperty("Operation")
    @Override
    public String getOperation() {
        return operation;
    }

    @Override
    public void setOperation(String operation) {
        this.operation = operation;
    }

    @JsonProperty("Inner")
    @Override
    public SimplifiedExpressionNode getInner() {
        return inner;
    }

    @Override
    public void setInner(SimplifiedExpressionNode value) {
        this.inner = value;
    }

    @JsonProperty("OpNext")
    @Override
    public SimplifiedExpressionNode getOpNext() {
        return opNext;
    }

    @Override
    public void setOpNext(SimplifiedExpressionNode value) {
        this.opNext = value;
    }

    @JsonProperty("Parameters")
    @Override
    public List<SimplifiedExpressionNode> getParameters() {
        return parameters;
    }

    @Override
    public String getKind() {
        return kind;
    }

    @JsonProperty("Kind")
    @Override
    public void setKind(String kind) {
        this.kind = kind;
    }

    @JsonProperty("Group")
    @Override
    public SimplifiedExpressionNode getGroup() {
        return group;
    }

    @Override
    public void setGroup(SimplifiedExpressionNode group) {
        this.group = group;
    }

    @JsonProperty("UniqueId")
    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @JsonProperty("Types")
    @Override
    public String getTypes() {
        return types;
    }

    @Override
    public void setTypes(String types) {
        this.types = types;
    }

    @JsonProperty("OpTypes")
    @Override
    public String getOpTypes() {
        return opTypes;
    }

    @Override
    public void setOpTypes(String opTypes) {
        this.opTypes = opTypes;
    }

    @JsonProperty("StartLine")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Override
    public Integer getStartLine() {
        return startLine;
    }

    @Override
    public void setStartLine(Integer startLine) {
        this.startLine = startLine;
    }
    
    @JsonProperty("StartColumn")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Override
    public Integer getStartColumn() {
        return startColumn;
    }

    @Override
    public void setStartColumn(Integer startColumn) {
        this.startColumn = startColumn;
    }
}