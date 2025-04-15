package com.fhirpathlab.utils;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4b.fhirpath.ExpressionNode.Kind;

public class AstMapper {

    static public JsonNode From(SimplifiedExpressionNode node, String contextType) {
        return From(node, ProximalNode(contextType));
    }

    static private JsonNode SimpleFrom(SimplifiedExpressionNode node, JsonNode parent) {
        if (node.getKind() == "Name") {
            JsonNode memberNode = new JsonNode();
            memberNode.setExpressionType("ChildExpression");
            memberNode.setName(node.getName());
            if (node.getTypes() != null && node.getTypes().length() > 0)
                memberNode.setReturnType(node.getTypes());
            else
                memberNode.setReturnType(" ");
            if (parent != null)
                memberNode.insertArgument(parent);
            else{
                if (node.isProximal()) {
                    memberNode.insertArgument(ProximalNode(node.getTypes()));
                }
                else
                memberNode.insertArgument(ProximalNode(" "));
            }

            if (node.getInner() != null) {
                var innerNode = AstMapper.From(node.getInner(), memberNode);
                return innerNode;
            }
            return memberNode;
        }

        if (node.getKind() == "Function") {
            JsonNode nodeFunction = new JsonNode();
            nodeFunction.setExpressionType("FunctionCallExpression");
            nodeFunction.setName(node.getFunction());
            if (node.getTypes() != null && node.getTypes().length() > 0)
                nodeFunction.setReturnType(node.getTypes());
            else
                nodeFunction.setReturnType(" ");

            if (parent != null)
                nodeFunction.insertArgument(parent);
            else
                nodeFunction.insertArgument(ProximalNode(" "));

            if (node.getParameters() != null) {
                for (SimplifiedExpressionNode arg : node.getParameters()) {
                    nodeFunction.appendArgument(From(arg, parent.getReturnType()));
                }
            }

            if (node.getInner() != null) {
                var innerNode = AstMapper.From(node.getInner(), nodeFunction);
                return innerNode;
            }

            return nodeFunction;
        }

        if (node.getKind() == "Constant") {
            JsonNode nodeConstant = new JsonNode();
            nodeConstant.setName(node.getConstant());
            nodeConstant.setExpressionType("ConstantExpression");
            if (node.getTypes() != null && node.getTypes().length() > 0 && !node.getTypes().startsWith("%"))
                nodeConstant.setReturnType(node.getTypes());
            else
                nodeConstant.setReturnType(" ");

            if (node.getInner() != null) {
                var innerNode = AstMapper.From(node.getInner(), nodeConstant);
                return innerNode;
            }

            return nodeConstant;
        }

        if (node.getKind() == "Group") {
            // Groups (brackets) are skipped in the AST representation (as they are
            // redundant)
            // for now
            return From(node.getGroup(), parent);
        }

        if (node.getKind() == "Unary") {
            JsonNode unaryNode = new JsonNode();
            unaryNode.setExpressionType("UnaryExpression");
            unaryNode.setName(node.getOperation());
            if (node.getOpTypes() != null && node.getOpTypes().length() > 0)
                unaryNode.setReturnType(node.getOpTypes());
            else
                unaryNode.setReturnType(" ");
            var nextOp = node.getOpNext();
            unaryNode.insertArgument(ProximalNode(parent != null ? parent.getReturnType() : " "));
            unaryNode.appendArgument(SimpleFrom(nextOp, unaryNode));
            return unaryNode;
        }
        return null;
    }

    static public JsonNode From(SimplifiedExpressionNode node, JsonNode parent) {
        var sf = SimpleFrom(node, parent);
        if (sf != null && node.getOperation() == null)
            return sf;

        var nextOp = node.getOpNext();
        if (node.getKind() == "Unary" && node.getOpNext() != null)
            nextOp = nextOp.getOpNext();
        if (nextOp == null)
            return sf;

        // only thing left are binary operations
        return ProcessBinaryOperation(node, nextOp, sf, parent);
    }

    // the parent parameter is used to indicate that needs to be embedded
    static public JsonNode ProcessBinaryOperation(SimplifiedExpressionNode leftExprNode,
            SimplifiedExpressionNode rightExprNode, JsonNode leftNode, JsonNode parent) {
        JsonNode binaryOperationNode = new JsonNode();
        binaryOperationNode.setExpressionType("BinaryExpression");
        binaryOperationNode.setName(leftExprNode.getOperation());
        if (leftExprNode.getOpTypes() != null)
            binaryOperationNode.setReturnType(leftExprNode.getOpTypes());
        else
            binaryOperationNode.setReturnType(leftExprNode.getTypes());

        var rightNode = SimpleFrom(rightExprNode, parent);
        binaryOperationNode.appendArgument(leftNode);
        binaryOperationNode.appendArgument(rightNode);

        var nextOp = rightExprNode.getOpNext();
        if (rightExprNode.getKind() == "Unary" && rightExprNode.getOpNext() != null)
            nextOp = nextOp.getOpNext();
        if (nextOp == null)
            return binaryOperationNode;

        return ProcessBinaryOperation(rightExprNode, nextOp, binaryOperationNode, parent);
    }

    static private JsonNode ProximalNode(String returnType) {
        JsonNode proximalNode = new JsonNode();
        proximalNode.setExpressionType("AxisExpression");
        proximalNode.setName("builtin.that");
        proximalNode.setReturnType(returnType);
        return proximalNode;
    }

    // direct port of the fhirpath-lab invert tree function
    static public List<JsonNode> InvertTree(JsonNode ast) {
        JsonNode rootItem = new JsonNode();
        rootItem.setExpressionType(ast.getExpressionType());
        rootItem.setName(ast.getName());
        if (ast.getReturnType() != null)
            rootItem.setReturnType(ast.getReturnType());

        var result = new ArrayList<JsonNode>();
        if (ast.getArguments() != null && ast.getArguments().size() > 0) {
            var focus = InvertTree((JsonNode) (ast.getArguments().toArray()[0]));
            result.addAll(focus);
            if (ast.getArguments().size() > 1) {
                rootItem.setArguments(new ArrayList<>());
                for (var element : ast.getArguments()) {
                    if (element != ast.getArguments().get(0)) {
                        var elementArgs = InvertTree((JsonNode) element);
                        rootItem.getArguments().addAll(elementArgs);
                    }
                }
            }
        }
        result.add(rootItem);
        return result;
    }

    static public JsonNode From(org.hl7.fhir.r4b.fhirpath.ExpressionNode node) {
        JsonNode jsonNode = new JsonNode();
        if (node.getKind() == Kind.Name)
            jsonNode.setExpressionType("ChildExpression");
        else if (node.getKind() == Kind.Function) {
            jsonNode.setExpressionType("FunctionExpression");
            jsonNode.setName(node.getFunction().toString());
        } else
            jsonNode.setExpressionType(node.getKind().toString());
        if (node.getConstant() != null) {
            jsonNode.setName(node.getConstant().toString());
            jsonNode.setExpressionType("ConstantExpression");
        } else
            jsonNode.setName(node.getName());
        List<JsonNode> args = new ArrayList<JsonNode>();
        jsonNode.setArguments(args);
        if (node.getParameters() != null) {
            for (org.hl7.fhir.r4b.fhirpath.ExpressionNode arg : node.getParameters()) {
                args.add(From(arg));
            }
        }

        jsonNode.setReturnType(" ");
        if (node.getInner() != null) {
            JsonNode innerNode = From(node.getInner());
            innerNode.insertArgument(jsonNode);
            jsonNode = innerNode;
        }

        if (node.getOperation() != null) {
            JsonNode jsonNodeOp = new JsonNode();
            jsonNodeOp.setExpressionType("OperationExpression");
            jsonNodeOp.setName(node.getOperation().toString());
            List<JsonNode> args2 = new ArrayList<JsonNode>();
            jsonNodeOp.setArguments(args2);
            args2.add(jsonNode);
            args2.add(From(node.getOpNext()));
            jsonNodeOp.setReturnType(" ");
            return jsonNodeOp;
        }
        return jsonNode;
    }

    static public JsonNode From(org.hl7.fhir.r5.fhirpath.ExpressionNode node) {
        JsonNode jsonNode = new JsonNode();
        if (node.getKind() == org.hl7.fhir.r5.fhirpath.ExpressionNode.Kind.Name)
            jsonNode.setExpressionType("ChildExpression");
        else if (node.getKind() == org.hl7.fhir.r5.fhirpath.ExpressionNode.Kind.Function) {
            jsonNode.setExpressionType("FunctionExpression");
            jsonNode.setName(node.getFunction().toString());
        } else
            jsonNode.setExpressionType(node.getKind().toString());
        if (node.getConstant() != null) {
            jsonNode.setName(node.getConstant().toString());
            jsonNode.setExpressionType("ConstantExpression");
        } else
            jsonNode.setName(node.getName());
        List<JsonNode> args = new ArrayList<JsonNode>();
        jsonNode.setArguments(args);
        if (node.getParameters() != null) {
            for (org.hl7.fhir.r5.fhirpath.ExpressionNode arg : node.getParameters()) {
                args.add(From(arg));
            }
        }

        jsonNode.setReturnType(" ");
        if (node.getInner() != null) {
            JsonNode innerNode = From(node.getInner());
            innerNode.insertArgument(jsonNode);
            jsonNode = innerNode;
        }

        if (node.getOperation() != null) {
            JsonNode jsonNodeOp = new JsonNode();
            jsonNodeOp.setExpressionType("OperationExpression");
            jsonNodeOp.setName(node.getOperation().toString());
            List<JsonNode> args2 = new ArrayList<JsonNode>();
            jsonNodeOp.setArguments(args2);
            args2.add(jsonNode);
            args2.add(From(node.getOpNext()));
            jsonNodeOp.setReturnType(" ");
            return jsonNodeOp;
        }
        return jsonNode;
    }
}