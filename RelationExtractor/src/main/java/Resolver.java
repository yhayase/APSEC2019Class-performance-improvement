import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

public class Resolver {
    private List<GraphEdge> edges;

    public List<GraphEdge> getEdges() {
        return edges;
    }

    private boolean doNameResolve;
    private int resolveSuccess;
    private int resolveFailed;

    public int getResolveSuccess() {
        return resolveSuccess;
    }

    public int getResolveFailed() {
        return resolveFailed;
    }

    Resolver() {
        doNameResolve = true;
        resolveSuccess = 0;
        resolveFailed = 0;

        edges = new LinkedList<>();
    }

    Resolver(boolean doNameResolve) {
        this();
        this.doNameResolve = doNameResolve;
    }

    public boolean isParam1inParam2(String part, String str) {
        if (str.matches(".*" + part + ".*")) {
            return true;
        } else {
            return false;
        }
    }

    public void execute(CompilationUnit compilationUnit) {

        compilationUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(classOrInterfaceDeclaration -> {
            if (classOrInterfaceDeclaration.isInterface())
                return;

            String resolvedDeclarationClassName = resolveClass(classOrInterfaceDeclaration);
            GraphNode classNode = new GraphNode(resolvedDeclarationClassName, "class");

            ArrayList<String> tmpArray;

            // 継承クラスの処理
            tmpArray = new ArrayList<>();
            resolveExtendedClass(classOrInterfaceDeclaration, tmpArray);
            for (String extendedClassName : tmpArray) {
                GraphNode extendedClassNode = new GraphNode(extendedClassName, "class");
                edges.add(new GraphEdge(classNode, extendedClassNode, "extends"));
            }

            classOrInterfaceDeclaration.findAll(FieldDeclaration.class).forEach(fieldDeclaration -> {
                String fieldName = resolvedDeclarationClassName + "."
                        + fieldDeclaration.getVariable(0).getNameAsString();
                GraphNode fieldNode = new GraphNode(fieldName, "field");

                edges.add(new GraphEdge(classNode, fieldNode, "has"));

                ArrayList<String> strings;
                strings = new ArrayList<>();
                resolveFieldType(fieldDeclaration, strings);
                for (String fieldTypeName : strings) {
                    GraphNode fieldTypeNode = new GraphNode(fieldTypeName, "class");
                    edges.add(new GraphEdge(fieldNode, fieldTypeNode, "type"));
                }

                strings = new ArrayList<>();
                resolveNewInField(fieldDeclaration, strings);
                for (String instantiatedTypeName : strings) {
                    GraphNode instantiatedTypeNode = new GraphNode(instantiatedTypeName, "class");
                    edges.add(new GraphEdge(fieldNode, instantiatedTypeNode, "new"));
                }
            });

            classOrInterfaceDeclaration.findAll(MethodDeclaration.class).forEach(methodDeclaration -> {
                String declarationMethodName = resolvedDeclarationClassName + "." + methodDeclaration.getNameAsString();
                GraphNode declarationMethodNode = new GraphNode(declarationMethodName, "method");
                edges.add(new GraphEdge(classNode, declarationMethodNode, "has"));

                ArrayList<String> strings;
                strings = new ArrayList<>();
                resolveMethodCall(methodDeclaration, strings, resolvedDeclarationClassName);
                for (String calleeName : strings) {
                    GraphNode calleeNode = new GraphNode(calleeName, "method");
                    edges.add(new GraphEdge(declarationMethodNode, calleeNode, "calls"));
                }

                strings = new ArrayList<>();
                resolveNewInMethod(methodDeclaration, strings);
                for (String instantiatedTypeName : strings) {
                    GraphNode instantiatedTypeNode = new GraphNode(instantiatedTypeName, "class");
                    edges.add(new GraphEdge(declarationMethodNode, instantiatedTypeNode, "new"));
                }

                strings = new ArrayList<>();
                resolveFieldAccess(methodDeclaration, strings);
                for (String fieldName : strings) {
                    GraphNode fieldNode = new GraphNode(fieldName, "field");
                    edges.add(new GraphEdge(declarationMethodNode, fieldNode, "accesses"));
                }

                strings = new ArrayList<>();
                resolveReturnType(methodDeclaration, strings);
                for (String returnTypeName : strings) {
                    GraphNode returnTypeNode = new GraphNode(returnTypeName, "class");
                    edges.add(new GraphEdge(declarationMethodNode, returnTypeNode, "returns"));
                }
            });
        });
    }

    private String resolveClass(ClassOrInterfaceDeclaration classDeclaration) {
        String name = classDeclaration.getNameAsString();
        if (!doNameResolve)
            return name;
        try {
            name = classDeclaration.resolve().getQualifiedName();
            // System.out.print("\r Resolve Success : " + name);
            resolveSuccess++;
            return name;
        } catch (Throwable t) {
            // System.out.print("\r Resolve Failed : " + name + " @ ");

            resolveFailed++;
            return name;
        }
    }

    private void resolveExtendedClass(ClassOrInterfaceDeclaration classDeclaration, ArrayList<String> arrayList) {
        ClassOrInterfaceType parent;
        if (classDeclaration.getExtendedTypes().size() != 0) {
            parent = classDeclaration.getExtendedTypes(0);
            try {
                parent = classDeclaration.getExtendedTypes(0).asClassOrInterfaceType();
            } catch (Throwable t) {
            }
        } else {
            return;
        }

        String name = parent.getNameAsString();
        if (doNameResolve) {
            try {
                name = parent.resolve().getQualifiedName();
                resolveSuccess++;
            } catch (Throwable t) {
                resolveFailed++;
            }
            arrayList.add(name);
        } else {
            arrayList.add(name);
        }
    }

    private void resolveFieldType(FieldDeclaration fieldDeclaration, ArrayList<String> arrayList) {
        String name = fieldDeclaration.getVariable(0).getTypeAsString();
        if (doNameResolve) {
            try {
                name = fieldDeclaration.getVariable(0).getType().resolve().describe();// .asClassOrInterfaceType().resolve().describe();
                resolveSuccess++;
            } catch (IllegalStateException i) {
                resolveSuccess++;
            } catch (UnsupportedOperationException t) {
                if (name.equals("void")) {
                    resolveSuccess++;
                } else {
                    resolveFailed++;
                }
            } catch (Throwable t) {
                resolveFailed++;
            }
            arrayList.add(name);
        } else {
            arrayList.add(name);
        }
    }

    private void resolveNewInField(FieldDeclaration fieldDeclaration, ArrayList<String> arrayList) {
        if (!fieldDeclaration.isInitializerDeclaration())
            return;
        fieldDeclaration.findAll(ObjectCreationExpr.class).forEach(objectCreationExpr -> {
            String name = objectCreationExpr.getTypeAsString();
            if (!doNameResolve) {
                arrayList.add(name);
            } else {
                try {
                    name = objectCreationExpr.getType().resolve().describe() + "." + name;
                    resolveSuccess++;
                } catch (Exception e) {
                    resolveFailed++;
                }
                arrayList.add(name);
            }
        });
    }

    private void resolveMethodCall(MethodDeclaration methodDeclaration, ArrayList<String> arrayList,
            String resolvedDeclarationClassName) {
        methodDeclaration.findAll(MethodCallExpr.class).forEach(callee -> {
            String name = callee.getNameAsString();

            if (doNameResolve) {
                if (!callee.getScope().isPresent()) {
                    arrayList.add(resolvedDeclarationClassName + "." + name);
                    resolveSuccess++;
                    return;
                }
                try {
                    name = callee.getScope().get().calculateResolvedType().describe() + "." + name;
                    resolveSuccess++;
                } catch (Throwable t) {
                    resolveFailed++;
                }
                arrayList.add(name);
            } else {
                arrayList.add(name);
            }
        });
    }

    private void resolveNewInMethod(MethodDeclaration methodDeclaration, ArrayList<String> arrayList) {
        methodDeclaration.findAll(ObjectCreationExpr.class).forEach(objectCreationExpr -> {
            String name = objectCreationExpr.getTypeAsString();

            if (doNameResolve) {
                try {
                    name = objectCreationExpr.getType().resolve().describe() + "." + name;
                    resolveSuccess++;
                } catch (Throwable t) {
                    resolveFailed++;
                }
                arrayList.add(name);
            } else {
                arrayList.add(name);
            }
        });
    }

    private void resolveFieldAccess(MethodDeclaration declarationMethod, ArrayList<String> arrayList) {
        declarationMethod.findAll(FieldAccessExpr.class).forEach(accessedField -> {
            if (accessedField.getParentNode().isPresent()) {
                if (accessedField.getParentNode().get() instanceof FieldAccessExpr)
                    return;
            }
            String name = accessedField.getNameAsString();
            if (doNameResolve) {
                try {
                    name = accessedField.getScope().calculateResolvedType().describe() + "." + name;
                    resolveSuccess++;
                } catch (Throwable t) {
                    Expression scope = accessedField.getScope();
                    if (scope != null) {
                        if (isParam1inParam2("java", scope.toString())) {
                            name = scope.toString() + "." + name;
                            resolveSuccess++;
                        }
                    } else
                        resolveFailed++;
                }
                arrayList.add(name);
            } else {
                arrayList.add(name);
            }
        });
    }

    private void resolveReturnType(MethodDeclaration declarationMethod, ArrayList<String> arrayList) {
        String name = declarationMethod.getType().asString();
        if (doNameResolve) {
            try {
                name = declarationMethod.getType().resolve().describe();
                resolveSuccess++;
            } catch (UnsupportedOperationException t) {
                if (isParam1inParam2("PrimitiveTypeUsage", t.toString())) {
                    resolveSuccess++;
                } else if (name.equals("String")) {
                    name = "java.lang.String";
                    resolveSuccess++;
                } else if (name.equals("void")) {
                    resolveSuccess++;
                } else {
                    resolveFailed++;
                }
            } catch (Throwable t) {
                resolveFailed++;
            }
            arrayList.add(name);
        } else {
            arrayList.add(name);
        }
    }
}

record GraphNode(String name, String type) {
}

record GraphEdge(GraphNode source, GraphNode target, String type) {
}