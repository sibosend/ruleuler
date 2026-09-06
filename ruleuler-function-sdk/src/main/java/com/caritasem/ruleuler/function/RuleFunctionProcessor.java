package com.caritasem.ruleuler.function;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("com.caritasem.ruleuler.function.RuleFunction")
@SupportedOptions({"functionPackage", "functionVersion"})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class RuleFunctionProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(RuleFunction.class);
        if (elements.isEmpty()) {
            return false;
        }
        String functionPackage = processingEnv.getOptions().get("functionPackage");
        String functionVersion = processingEnv.getOptions().get("functionVersion");
        if (functionPackage == null || functionPackage.isBlank()
                || functionVersion == null || functionVersion.isBlank()) {
            error("必须配置 -AfunctionPackage 和 -AfunctionVersion，禁止默认值");
            return false;
        }
        try {
            List<FunctionAlGenerator.BeanDecl> beans = new ArrayList<>();
            for (Element el : elements) {
                if (el.getKind() != ElementKind.CLASS) {
                    error("@RuleFunction 只能标在类上: " + el);
                    return false;
                }
                beans.add(readBean((TypeElement) el));
            }
            writeOutputs(functionPackage, functionVersion, beans);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        } catch (IOException e) {
            error("写函数包资源失败: " + e.getMessage());
        }
        return false;
    }

    private FunctionAlGenerator.BeanDecl readBean(TypeElement type) {
        RuleFunction ann = type.getAnnotation(RuleFunction.class);
        List<FunctionAlGenerator.MethodDecl> methods = new ArrayList<>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            RuleMethod methodAnn = enclosed.getAnnotation(RuleMethod.class);
            if (methodAnn == null) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosed;
            if (method.getModifiers().contains(Modifier.STATIC)) {
                throw new IllegalArgumentException("函数方法不能是 static: " + method.getSimpleName());
            }
            String where = type.getSimpleName() + "." + method.getSimpleName();
            FunctionAlGenerator.assertReturnType(method.getReturnType().toString(), where);
            List<FunctionAlGenerator.Param> params = new ArrayList<>();
            for (VariableElement pe : method.getParameters()) {
                RuleParam paramAnn = pe.getAnnotation(RuleParam.class);
                if (paramAnn == null) {
                    throw new IllegalArgumentException(where + " 参数缺少 @RuleParam");
                }
                FunctionAlGenerator.assertParamType(pe.asType().toString(), where);
                params.add(new FunctionAlGenerator.Param(
                        paramAnn.value(), FunctionAlGenerator.toDatatype(pe.asType().toString())));
            }
            methods.add(new FunctionAlGenerator.MethodDecl(
                    methodAnn.label(),
                    method.getSimpleName().toString(),
                    FunctionAlGenerator.toDatatype(method.getReturnType().toString()),
                    params));
        }
        if (methods.isEmpty()) {
            throw new IllegalArgumentException(type.getQualifiedName() + " 没有 @RuleMethod");
        }
        return new FunctionAlGenerator.BeanDecl(
                ann.bean(), ann.label(), type.getQualifiedName().toString(), methods);
    }

    private void writeOutputs(String functionPackage, String functionVersion,
                              List<FunctionAlGenerator.BeanDecl> beans) throws IOException {
        String xml = FunctionAlGenerator.xml(functionPackage, functionVersion, beans);
        writeResource("META-INF/ruleuler/functions.al.xml", xml.getBytes(StandardCharsets.UTF_8));
        writeResource("META-INF/ruleuler/" + functionPackage + ".version",
                functionVersion.getBytes(StandardCharsets.UTF_8));

        String className = "RuleulerFunctionAutoConfiguration";
        String pkg = "ruleuler.functions.generated";
        String java = FunctionAlGenerator.autoConfigJava(pkg, className, beans);
        try (Writer w = processingEnv.getFiler().createSourceFile(pkg + "." + className).openWriter()) {
            w.write(java);
        }
        writeResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
                (pkg + "." + className + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private void writeResource(String path, byte[] bytes) throws IOException {
        FileObject fo = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", path);
        try (OutputStream os = fo.openOutputStream()) {
            os.write(bytes);
        }
    }

    private void error(String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
    }
}
