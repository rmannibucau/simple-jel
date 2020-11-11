package com.github.rmannibucau.jel.proc;

import com.github.rmannibucau.jel.api.annotation.MetaJel;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class SimpleJelAnnotationProcessor extends AbstractProcessor {
    private ProcessingEnvironment env;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.env = processingEnv;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        roundEnv.getRootElements().stream()
                .flatMap(it -> Stream.concat( // add nested classes too
                        Stream.of(it),
                        it.getEnclosedElements().stream()))
                .filter(TypeElement.class::isInstance)
                .map(TypeElement.class::cast)
                .distinct()
                .flatMap(it -> Stream.concat( // add methods (if annotation is never on a class)
                        Stream.of(it),
                        env.getElementUtils().getAllMembers(it).stream()))
                .flatMap(it -> it.getAnnotationMirrors().stream())
                .flatMap(this::flatten)
                .map(AnnotationMirror::getAnnotationType)
                .distinct()
                .filter(it -> env.getElementUtils()
                        .getAllAnnotationMirrors(it.asElement()).stream()
                        .anyMatch(this::findMeta))
                .forEach(marker -> generateForMarker(roundEnv, marker));
        return false;
    }

    private boolean findMeta(final AnnotationMirror e) {
        return TypeElement.class.cast(e.getAnnotationType().asElement())
                .getQualifiedName().contentEquals(MetaJel.class.getName());
    }

    private Stream<AnnotationMirror> flatten(final AnnotationMirror am) {
        return Stream.concat(Stream.of(am), am.getAnnotationType().getAnnotationMirrors().stream());
    }

    private void generateForMarker(final RoundEnvironment roundEnv, final DeclaredType markerAnnotation) {
        final var config = env.getElementUtils()
                .getAllAnnotationMirrors(markerAnnotation.asElement()).stream()
                .filter(this::findMeta)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        final var configs = config.getElementValues().entrySet().stream()
                .collect(toMap(e -> e.getKey().getSimpleName().toString(), e -> e.getValue().getValue()));
        final var expressionName = ofNullable(configs.get("expressionElementName"))
                .map(String::valueOf).orElse("value");
        final var customMethods = ofNullable(configs.get("customMethods"))
                .map(String::valueOf).orElse("");
        final var contextName = ofNullable(configs.get("contextVariableName"))
                .map(String::valueOf).orElse("context");
        final var contextType = ofNullable(configs.get("contextType")).map(String::valueOf).orElse("");
        final var returnType = ofNullable(configs.get("returnType")).map(String::valueOf).orElse("");
        final var evaluatorClassNamePattern = ofNullable(configs.get("evaluatorClassNamePattern"))
                .map(String::valueOf).orElse("${class}$SimpleJelEvaluator");
        final var evaluatorMethodNamePattern = ofNullable(configs.get("evaluatorMethodNamePattern"))
                .map(String::valueOf).orElse("${class}$${method}$SimpleJelEvaluator");
        final var evaluatorMarkers = ofNullable(configs.get("evaluatorMarkers"))
                .map(String[].class::cast).orElseGet(() -> new String[0]);
        final var imports = ofNullable(configs.get("imports")).map(String[].class::cast).orElseGet(() -> new String[0]);

        final var annotation = TypeElement.class.cast(markerAnnotation.asElement());
        final var expressionMarker = env.getElementUtils().getAllMembers(annotation).stream()
                .filter(it -> it.getSimpleName().contentEquals(expressionName))
                .findFirst()
                .map(ExecutableElement.class::cast);
        if (expressionMarker.isEmpty()) {
            env.getMessager().printMessage(
                    Diagnostic.Kind.ERROR, "no @MetaJel.Expression found for " + markerAnnotation);
            return;
        }

        roundEnv.getElementsAnnotatedWith(annotation)
                .forEach(element -> generate(
                        annotation, expressionMarker.orElseThrow(IllegalStateException::new),
                        contextType, returnType, evaluatorMarkers, imports, element,
                        evaluatorClassNamePattern, evaluatorMethodNamePattern, contextName, customMethods));
    }

    private void generate(final TypeElement marker, final ExecutableElement expressionMarker,
                          final String contextType, final String returnType, final String[] evaluatorMarkers,
                          final String[] customImports, final Element element,
                          final String classPatternName, final String methodPatternName,
                          final String contextName, final String customMethods) {
        final var config = element.getAnnotationMirrors().stream()
                .filter(it -> it.getAnnotationType().asElement() == marker)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Didn't find " + marker + " on " + element));
        final var expression = String.valueOf(config.getElementValues().get(expressionMarker).getValue());
        final var name = TypeElement.class.isInstance(element) ?
                new Substitutor(key -> {
                    if ("class" .equals(key)) {
                        return className(element);
                    }
                    throw new IllegalArgumentException(key);
                }).replace(classPatternName) :
                new Substitutor(key -> {
                    switch (key) {
                        case "class":
                            return className(ExecutableElement.class.cast(element).getEnclosingElement());
                        case "method":
                            return ExecutableElement.class.cast(element).getSimpleName().toString();
                        default:
                            throw new IllegalArgumentException(key);
                    }
                }).replace(methodPatternName);
        final var lastDot = name.lastIndexOf('.');
        final var packageName = lastDot > 0 ? name.substring(0, lastDot) : "";
        final var className = lastDot > 0 ? name.substring(lastDot + 1) : name;
        final var returnSimpleType = returnType.isBlank() ? "Object" : returnType.substring(returnType.lastIndexOf('.') + 1);
        final var contextSimpleType = contextType.isBlank() ? "Object" : contextType.substring(contextType.lastIndexOf('.') + 1);
        final var annotations = Stream.of(evaluatorMarkers)
                .map(it -> '@' + it.substring(it.lastIndexOf('.') + 1))
                .collect(joining("\n", "\n", "\n")).trim();
        final var imports = Stream.concat(
                Stream.of("java.util.function.Function", contextType, returnType),
                Stream.concat(Stream.of(customImports), Stream.of(evaluatorMarkers)))
                .filter(it -> !it.isBlank() && it.contains("."))
                .sorted()
                .map(it -> "import " + it + ";\n")
                .collect(joining("\n"));
        try {
            final var sourceFile = env.getFiler().createSourceFile(name);
            try (final var writer = sourceFile.openWriter()) {
                writer.write("" +
                        (!packageName.isBlank() ? "package " + packageName + ";\n\n" : "") +
                        (imports.isBlank() ? "" : (imports + "\n\n")) +
                        (annotations.isBlank() ? "" : (annotations + "\n")) +
                        "public class " + className + " implements Function<" + contextSimpleType + ", " + returnSimpleType + "> {\n" +
                        "    @Override\n" +
                        "    public " + returnSimpleType + " apply(final " + contextSimpleType + " " + contextName + ") {\n" +
                        "        " + expression + (expression.endsWith(";") ? "" : ";") + "\n" +
                        "    }\n" +
                        (customMethods.isBlank() ? "" : ("\n" + customMethods + "\n")) +
                        "}\n");
            }
        } catch (final IOException e) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
    }

    private String className(final Element element) {
        final var elts = Stream.iterate(
                element,
                it -> it != null && !PackageElement.class.isInstance(it),
                Element::getEnclosingElement)
                .collect(toList());
        Collections.reverse(elts);
        return TypeElement.class.cast(elts.iterator().next()).getQualifiedName().toString() +
                (elts.size() == 1 ? "" : elts.stream()
                        .skip(1)
                        .map(it -> it.getSimpleName().toString()).collect(joining("$", "$", "")));
    }
}
