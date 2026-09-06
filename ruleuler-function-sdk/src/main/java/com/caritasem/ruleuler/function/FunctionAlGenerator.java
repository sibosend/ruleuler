package com.caritasem.ruleuler.function;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class FunctionAlGenerator {

    public static final Pattern PACKAGE_NAME = Pattern.compile("[a-zA-Z0-9_-]+");

    public static final Set<String> PARAM_TYPES = Set.of(
            "String", "Integer", "Long", "Double", "BigDecimal", "Boolean", "Date", "List", "Map");

    public static final Set<String> RETURN_TYPES = new LinkedHashSet<>();
    static {
        RETURN_TYPES.addAll(PARAM_TYPES);
        RETURN_TYPES.add("void");
        RETURN_TYPES.add("Object");
    }

    private FunctionAlGenerator() {}

    public record Param(String label, String datatype) {}

    public record MethodDecl(String label, String methodName, String returnDatatype, List<Param> params) {}

    public record BeanDecl(String beanId, String label, String className, List<MethodDecl> methods) {}

    public static String toDatatype(String qualifiedType) {
        String raw = stripGenerics(qualifiedType);
        int lastDot = raw.lastIndexOf('.');
        String simple = lastDot < 0 ? raw : raw.substring(lastDot + 1);
        return switch (raw) {
            case "int" -> "Integer";
            case "long" -> "Long";
            case "double" -> "Double";
            case "boolean" -> "Boolean";
            case "void" -> "void";
            case "java.util.Date" -> "Date";
            case "java.util.List" -> "List";
            case "java.util.Map" -> "Map";
            case "java.math.BigDecimal" -> "BigDecimal";
            case "java.lang.String" -> "String";
            case "java.lang.Integer" -> "Integer";
            case "java.lang.Long" -> "Long";
            case "java.lang.Double" -> "Double";
            case "java.lang.Boolean" -> "Boolean";
            case "java.lang.Object" -> "Object";
            default -> simple;
        };
    }

    public static void assertParamType(String qualifiedType, String where) {
        String dt = toDatatype(qualifiedType);
        if (!PARAM_TYPES.contains(dt)) {
            throw new IllegalArgumentException(where + " 参数类型非法: " + qualifiedType);
        }
        if ("Date".equals(dt) && !"java.util.Date".equals(stripGenerics(qualifiedType))) {
            throw new IllegalArgumentException(where + " Date 必须是 java.util.Date");
        }
    }

    public static void assertReturnType(String qualifiedType, String where) {
        String dt = toDatatype(qualifiedType);
        if (!RETURN_TYPES.contains(dt)) {
            throw new IllegalArgumentException(where + " 返回类型非法: " + qualifiedType);
        }
    }

    public static String xml(String functionPackage, String functionVersion, List<BeanDecl> beans) {
        if (!PACKAGE_NAME.matcher(functionPackage).matches()) {
            throw new IllegalArgumentException("function-package 只许 [a-zA-Z0-9_-]+");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<action-library function-package=\"")
                .append(escape(functionPackage))
                .append("\" function-version=\"")
                .append(escape(functionVersion))
                .append("\">\n");
        for (BeanDecl bean : beans) {
            sb.append("  <spring-bean id=\"")
                    .append(escape(bean.beanId()))
                    .append("\" name=\"")
                    .append(escape(bean.label()))
                    .append("\">\n");
            Set<String> names = new LinkedHashSet<>();
            for (MethodDecl method : bean.methods()) {
                if (!names.add(method.methodName())) {
                    throw new IllegalArgumentException("同名方法禁止: " + method.methodName());
                }
                sb.append("    <method name=\"")
                        .append(escape(method.label()))
                        .append("\" method-name=\"")
                        .append(escape(method.methodName()))
                        .append("\">\n");
                for (Param param : method.params()) {
                    sb.append("      <parameter name=\"")
                            .append(escape(param.label()))
                            .append("\" type=\"")
                            .append(escape(param.datatype()))
                            .append("\"/>\n");
                }
                sb.append("    </method>\n");
            }
            sb.append("  </spring-bean>\n");
        }
        sb.append("</action-library>\n");
        return sb.toString();
    }

    public static String autoConfigJava(String packageName, String className, List<BeanDecl> beans) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import org.springframework.context.annotation.Bean;\n");
        sb.append("import org.springframework.context.annotation.Configuration;\n\n");
        sb.append("@Configuration(proxyBeanMethods = false)\n");
        sb.append("public class ").append(className).append(" {\n");
        int i = 0;
        for (BeanDecl bean : beans) {
            i++;
            String method = "fn" + i + "_" + sanitize(bean.beanId());
            sb.append("    @Bean(\"").append(escape(bean.beanId())).append("\")\n");
            sb.append("    public ").append(bean.className()).append(" ").append(method).append("() {\n");
            sb.append("        return new ").append(bean.className()).append("();\n");
            sb.append("    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String stripGenerics(String type) {
        int lt = type.indexOf('<');
        return lt < 0 ? type : type.substring(0, lt);
    }

    private static String sanitize(String beanId) {
        return beanId.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static List<BeanDecl> copyBeans(List<BeanDecl> beans) {
        List<BeanDecl> copy = new ArrayList<>();
        for (BeanDecl b : beans) {
            copy.add(new BeanDecl(b.beanId(), b.label(), b.className(), List.copyOf(b.methods())));
        }
        return copy;
    }
}
