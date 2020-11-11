package com.github.rmannibucau.jel.mp.jwt.api;

import com.github.rmannibucau.jel.api.annotation.MetaJel;

import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@MetaJel(
        contextVariableName = "jwt",
        contextType = "org.eclipse.microprofile.jwt.JsonWebToken",
        returnType = "Boolean",
        evaluatorClassNamePattern = "${class}$MpJwtSecuredBy",
        evaluatorMethodNamePattern = "${class}_${method}$MpJwtSecuredBy",
        customMethods = "" +
                "    private boolean exists(final JsonWebToken token) {\n" +
                "        try {\n" +
                "            return !token.getClaimNames().isEmpty();\n" +
                "        } catch (final Exception e) {\n" +
                "            return false;\n" +
                "        }\n" +
                "    }" +
                "")
@InterceptorBinding
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface SecuredBy {
    /**
     * @return the expression
     */
    @Nonbinding
    String value();
}
