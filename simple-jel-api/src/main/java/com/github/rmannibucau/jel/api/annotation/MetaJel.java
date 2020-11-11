package com.github.rmannibucau.jel.api.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * When an annotation is marked with this annotation it is considered as a Jel expression.
 */
@Retention(CLASS)
@Target({TYPE, METHOD, FIELD})
public @interface MetaJel {
    /**
     * @return the name of the generated evaluator for class annotations.
     */
    String evaluatorClassNamePattern() default "${class}$SimpleJelEvaluator";

    /**
     * @return the name of the generated evaluator for method annotations.
     */
    String evaluatorMethodNamePattern() default "${class}$${method}$SimpleJelEvaluator";

    /**
     * @return the annotation member containing the expression.
     */
    String expressionElementName() default "value";

    /**
     * @return the name of the context for the expression.
     */
    String contextVariableName() default "context";

    /**
     * @return some additional methods/helpers to add to evaluator impl.
     */
    String customMethods() default "";

    /**
     * @return context type for the evaluator.
     */
    String contextType() default "";

    /**
     * @return return type for the evaluator.
     */
    String returnType() default "";

    /**
     * @return list of annotations to put on the evaluator.
     */
    String[] evaluatorMarkers() default {};

    /**
     * @return list of forced imports to put on the evaluator.
     */
    String[] imports() default {};
}
