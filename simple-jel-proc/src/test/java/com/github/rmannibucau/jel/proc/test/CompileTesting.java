package com.github.rmannibucau.jel.proc.test;

import com.github.rmannibucau.jel.proc.SimpeJelAnnotationProcessor;
import com.google.testing.compile.JavaFileObjects;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static com.google.testing.compile.Compilation.Status.SUCCESS;
import static com.google.testing.compile.Compiler.javac;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

class Impl implements InvocationInterceptor {
    @Override
    public void interceptTestMethod(final Invocation<Void> invocation,
                                    final ReflectiveInvocationContext<Method> invocationContext,
                                    final ExtensionContext extensionContext) throws Throwable {
        final var evaluatingProcessor = new EvaluatingProcessor(invocation);
        final var compilation = javac()
                .withProcessors(evaluatingProcessor, new SimpeJelAnnotationProcessor())
                .compile(JavaFileObjects.forSourceLines(
                        "Dummy", "final class Dummy {}"));
        checkState(compilation.status().equals(SUCCESS), compilation);
        evaluatingProcessor.throwIfStatementThrew();
    }

    @RequiredArgsConstructor
    private static class EvaluatingProcessor extends AbstractProcessor {
        private final Invocation<Void> invocation;
        private Throwable thrown;

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public boolean process(final Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (roundEnv.processingOver()) {
                try {
                    invocation.proceed();
                } catch (final Throwable e) {
                    thrown = e;
                }
            }
            return false;
        }

        void throwIfStatementThrew() throws Throwable {
            if (thrown != null) {
                throw thrown;
            }
        }
    }
}

@Target(METHOD)
@Retention(RUNTIME)
@ExtendWith(Impl.class)
public @interface CompileTesting {
}
