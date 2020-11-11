package com.github.rmannibucau.jel.mp.jwt.impl;

import org.eclipse.microprofile.jwt.JsonWebToken;

import javax.enterprise.context.ApplicationScoped;
import javax.interceptor.InvocationContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@ApplicationScoped
public class EvaluatorFinder {
    private final Map<Key, Function<JsonWebToken, Boolean>> cache = new ConcurrentHashMap<>();

    public Function<JsonWebToken, Boolean> lookupFor(final InvocationContext context) {
        final var key = new Key(context.getTarget().getClass(), context.getMethod());
        return cache.computeIfAbsent(key, k -> {
            final var evaluatorName = k.toEvaluatorName();
            try {
                return Thread.currentThread()
                        .getContextClassLoader()
                        .loadClass(evaluatorName)
                        .asSubclass(Function.class)
                        .getConstructor()
                        .newInstance();
            } catch (final InvocationTargetException ite) {
                throw new IllegalStateException(ite.getTargetException());
            } catch (final Exception e) {
                throw new IllegalStateException("Didn't find " + evaluatorName);
            }
        });
    }

    private static class Key {
        private final Class<?> clazz;
        private final Method method;
        private final int hash;

        private Key(final Class<?> clazz, final Method method) {
            this.clazz = clazz;
            this.method = method;
            this.hash = Objects.hash(clazz, method);
        }

        private String toEvaluatorName() {
            return clazz.getName() + (method == null ? "" : ("_" + method.getName())) + "$MpJwtSecuredBy";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key key = Key.class.cast(o);
            return clazz == key.clazz && Objects.equals(method, key.method);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
