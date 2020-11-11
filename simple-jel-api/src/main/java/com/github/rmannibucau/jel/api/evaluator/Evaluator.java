package com.github.rmannibucau.jel.api.evaluator;

/**
 * All generated classes will implement this interface.
 *
 * @param <R> returned type, generally {@link Object} but enables a framework to cast it through a wrapper.
 * @param <C> evaluation context type, generally {@link Object} but enables a framework to cast it through a wrapper.
 */
public interface Evaluator<R, C> {
    R evaluate(C context);
}
