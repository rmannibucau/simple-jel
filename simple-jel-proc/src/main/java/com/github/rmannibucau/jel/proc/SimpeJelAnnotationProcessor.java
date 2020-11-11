package com.github.rmannibucau.jel.proc;

import com.github.rmannibucau.jel.api.annotation.MetaJel;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

@SupportedAnnotationTypes("com.github.rmannibucau.jel.api.annotation.MetaJel")
public class SimpeJelAnnotationProcessor extends AbstractProcessor {
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
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        final var markers = roundEnv.getElementsAnnotatedWith(MetaJel.class);
        markers.forEach(marker -> {
            final var markerAnnotation = TypeElement.class.cast(marker);
            final var config = markerAnnotation.getAnnotation(MetaJel.class);
            final var expressionName = config.expressionElementName();
            final var contextType = config.contextType();
            final var returnType = config.returnType();
            final var evaluatorMarkers = config.evaluatorMarkers();
            final var expressionMarker = env.getElementUtils().getAllMembers(markerAnnotation).stream()
                    .filter(it -> it.getSimpleName().contentEquals(expressionName))
                    .findFirst()
                    .map(ExecutableElement.class::cast);
            if (expressionMarker.isEmpty()) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR, "no @MetaJel.Expression found for " + marker);
                return;
            }
            roundEnv.getElementsAnnotatedWith(markerAnnotation)
                    .forEach(element -> generate(
                            markerAnnotation, expressionMarker.orElseThrow(IllegalStateException::new),
                            contextType, returnType, evaluatorMarkers, config.imports(), element));
        });
        return false;
    }

    private void generate(final TypeElement marker, final ExecutableElement expressionMarker,
                          final String contextType, final String returnType, final String[] evaluatorMarkers,
                          final String[] customImports, final Element element) {
        final var config = element.getAnnotationMirrors().stream()
                .filter(it -> it.getAnnotationType().asElement() == marker)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Didn't find " + marker + " on " + element));
        final var expression = String.valueOf(config.getElementValues().get(expressionMarker).getValue());
        final var name = TypeElement.class.isInstance(element) ?
                toEvaluatorName(TypeElement.class.cast(element)) :
                toEvaluatorName(ExecutableElement.class.cast(element));
        final var lastDot = name.lastIndexOf('.');
        final var packageName = lastDot > 0 ? name.substring(0, lastDot) : "";
        final var className = lastDot > 0 ? name.substring(lastDot + 1) : name;
        final var returnSimpleType = returnType.isBlank() ? "Object" : returnType.substring(returnType.lastIndexOf('.') + 1);
        final var contextSimpleType = contextType.isBlank() ? "Object" : contextType.substring(contextType.lastIndexOf('.') + 1);
        final var annotations = Stream.of(evaluatorMarkers)
                .map(it -> '@' + it.substring(it.lastIndexOf('.') + 1))
                .collect(joining("\n", "\n", "\n")).trim();
        final var imports = Stream.concat(
                Stream.of("com.github.rmannibucau.jel.api.evaluator.Evaluator", contextType, returnType),
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
                        "public class " + className + " implements Evaluator<" + returnSimpleType + ", " + contextSimpleType + "> {\n" +
                        "    @Override\n" +
                        "    public " + returnSimpleType + " evaluate(final " + contextSimpleType + " context) {\n" +
                        "        " + expression + (expression.endsWith(";") ? "" : ";") + "\n" +
                        "    }\n" +
                        "}\n");
            }
        } catch (final IOException e) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
    }

    private String toEvaluatorName(final TypeElement element) {
        return TypeElement.class.cast(element).getQualifiedName().toString() + getEvaluatorSuffix();
    }

    private String toEvaluatorName(final ExecutableElement element) {
        return TypeElement.class.cast(element.getEnclosingElement()).getQualifiedName().toString() + "$" +
                element.getSimpleName().toString() +
                getEvaluatorSuffix();
    }

    private String getEvaluatorSuffix() {
        return "$SimpleJelEvaluator";
    }
}
