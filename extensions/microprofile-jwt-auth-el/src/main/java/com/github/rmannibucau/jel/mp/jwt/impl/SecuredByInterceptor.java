package com.github.rmannibucau.jel.mp.jwt.impl;

import com.github.rmannibucau.jel.mp.jwt.api.SecuredBy;
import org.eclipse.microprofile.jwt.JsonWebToken;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.ws.rs.ForbiddenException;
import java.io.Serializable;
import java.util.function.Function;

@SecuredBy("")
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE)
public class SecuredByInterceptor implements Serializable {
    @Inject
    private EvaluatorFinder evaluatorFinder;

    @Inject
    private JsonWebToken token;

    @AroundInvoke
    public Object securedBy(final InvocationContext context) throws Exception {
        final Function<JsonWebToken, Boolean> evaluator = evaluatorFinder.lookupFor(context);
        if (!evaluator.apply(token)) {
            throw new ForbiddenException();
        }
        return context.proceed();
    }
}
