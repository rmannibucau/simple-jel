package com.github.rmannibucau.jel.proc;

import com.github.rmannibucau.jel.api.annotation.MetaJel;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.FileObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.testing.compile.Compilation.Status.SUCCESS;
import static com.google.testing.compile.Compiler.javac;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleJelAnnotationProcessorTest {
    @Test
    void generate() throws Exception {
        validateGeneration(javac()
                .withProcessors(new SimpleJelAnnotationProcessor())
                .compile(List.of(
                        JavaFileObjects.forSourceLines(
                                "Dummy",
                                "package com.github.rmannibucau.jel.proc;\n" +
                                        "\n" +
                                        "@Evaluable(\"return 1 + 1\")\n" +
                                        "public class Dummy {\n" +
                                        "  @Evaluable(\"return 1 + 2\")\n" +
                                        "  public void evalMethod(String it) {}\n" +
                                        "}\n"),
                        JavaFileObjects.forSourceLines(
                                "Evaluable",
                                "package com.github.rmannibucau.jel.proc;\n" +
                                        "\n" +
                                        "import com.github.rmannibucau.jel.api.annotation.MetaJel;\n" +
                                        "import java.lang.annotation.Retention;\n" +
                                        "import java.lang.annotation.Target;\n" +
                                        "import static java.lang.annotation.ElementType.METHOD;\n" +
                                        "import static java.lang.annotation.ElementType.TYPE;\n" +
                                        "import static java.lang.annotation.RetentionPolicy.CLASS;\n" +
                                        "\n" +
                                        "@MetaJel\n" +
                                        "@Retention(CLASS)\n" +
                                        "@Target({TYPE, METHOD})\n" +
                                        "public @interface Evaluable {\n" +
                                        "    String value();\n" +
                                        "}\n"))));
    }

    @Test
    void apiDefinedOutsideCompilationUnit() throws Exception {
        validateGeneration(javac()
                .withClasspath(List.of(
                        Paths.get("target/test-classes").toFile(),
                        Paths.get("../simple-jel-api/target/classes").toFile()))
                .withProcessors(new SimpleJelAnnotationProcessor())
                .compile(List.of(
                        JavaFileObjects.forSourceLines(
                                "Dummy",
                                "package com.github.rmannibucau.jel.proc;\n" +
                                        "\n" +
                                        "import com.github.rmannibucau.jel.proc.SimpleJelAnnotationProcessorTest;\n" +
                                        "\n" +
                                        "@SimpleJelAnnotationProcessorTest.Evaluable(\"return 1 + 1\")\n" +
                                        "public class Dummy {\n" +
                                        "  @SimpleJelAnnotationProcessorTest.Evaluable(\"return 1 + 2\")\n" +
                                        "  public void evalMethod(String it) {}\n" +
                                        "}\n"))));
    }

    private void validateGeneration(final Compilation compilation) throws Exception {
        assertEquals(SUCCESS, compilation.status(), () ->
                Stream.of(
                        compilation.notes().stream(),
                        compilation.diagnostics().stream(),
                        compilation.warnings().stream(),
                        compilation.errors().stream()
                ).flatMap(identity()).map(Object::toString).distinct().collect(joining("\n")));

        final var generatedFiles = compilation.generatedFiles().stream()
                .filter(it -> it.getKind() == SOURCE)
                .collect(toList());
        assertEquals(2, generatedFiles.size());

        final var contents = generatedFiles.stream().collect(toMap(FileObject::getName, it -> {
            try (final BufferedReader reader = new BufferedReader(it.openReader(false))) {
                return reader.lines().collect(joining("\n"));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }));
        assertEquals("package com.github.rmannibucau.jel.proc;\n" +
                "\n" +
                "import java.util.function.Function;\n" +
                "\n" +
                "\n" +
                "public class Dummy$evalMethod$SimpleJelEvaluator implements Function<Object, Object> {\n" +
                "    @Override\n" +
                "    public Object apply(final Object context) {\n" +
                "        return 1 + 2;\n" +
                "    }\n" +
                "}", contents.get("/SOURCE_OUTPUT/com/github/rmannibucau/jel/proc/Dummy$evalMethod$SimpleJelEvaluator.java"),
                contents::toString);
        assertEquals("package com.github.rmannibucau.jel.proc;\n" +
                "\n" +
                        "import java.util.function.Function;\n" +
                "\n" +
                "\n" +
                "public class Dummy$SimpleJelEvaluator implements Function<Object, Object> {\n" +
                "    @Override\n" +
                "    public Object apply(final Object context) {\n" +
                "        return 1 + 1;\n" +
                "    }\n" +
                "}", contents.get("/SOURCE_OUTPUT/com/github/rmannibucau/jel/proc/Dummy$SimpleJelEvaluator.java"),
                contents::toString);
        final ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                final Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass != null) {
                    if (resolve) {
                        resolveClass(loadedClass);
                        return loadedClass;
                    }
                }
                synchronized (this) {
                    final var found = compilation.generatedFiles().stream()
                            .filter(it -> it.getKind() == CLASS && it.getName().equals("/CLASS_OUTPUT/" + name.replace('.', '/') + ".class"))
                            .findFirst();
                    if (found.isPresent()) {
                        final var object = found.orElseThrow(IllegalStateException::new);
                        final var out = new ByteArrayOutputStream();
                        try (final InputStream stream = object.openInputStream()) {
                            int read;
                            final var buffer = new byte[512];
                            while ((read = stream.read(buffer)) >= 0) {
                                out.write(buffer, 0, read);
                            }
                        } catch (final IOException e) {
                            throw new ClassNotFoundException(e.getMessage(), e);
                        }
                        final var bytecode = out.toByteArray();
                        final Class<?> defined = super.defineClass(name, bytecode, 0, bytecode.length);
                        if (resolve) {
                            resolveClass(defined);
                        }
                        return defined;
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
        final Function<Object, Object> e1 = Function.class.cast(loader.loadClass("com.github.rmannibucau.jel.proc.Dummy$SimpleJelEvaluator")
                .getConstructor()
                .newInstance());
        assertEquals(2, e1.apply(null));
        final Function<Object, Object> e2 = Function.class.cast(loader.loadClass("com.github.rmannibucau.jel.proc.Dummy$evalMethod$SimpleJelEvaluator")
                .getConstructor()
                .newInstance());
        assertEquals(3, e2.apply(null));
    }

    @MetaJel
    @Retention(RetentionPolicy.CLASS)
    @Target({TYPE, METHOD})
    public @interface Evaluable {
        String value();
    }
}
