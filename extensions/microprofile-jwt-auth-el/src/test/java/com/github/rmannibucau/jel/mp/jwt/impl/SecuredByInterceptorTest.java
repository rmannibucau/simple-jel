package com.github.rmannibucau.jel.mp.jwt.impl;

import com.github.rmannibucau.jel.mp.jwt.api.SecuredBy;
import org.apache.geronimo.microprofile.impl.jwtauth.cdi.GeronimoJwtAuthExtension;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit5.MeecrowaveConfig;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Set;

import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MeecrowaveConfig(
        scanningPackageIncludes = "com.github.rmannibucau.jel.mp.jwt.impl.SecuredByInterceptorTest",
        scanningExcludes = "jacorb")
class SecuredByInterceptorTest {
    private Client client;
    private Invocation.Builder target;

    @Inject
    private Meecrowave.Builder config;

    @BeforeEach
    void init() {
        client = ClientBuilder.newClient();
        target = client
                .target("http://localhost:" + config.getHttpPort())
                .path("SecuredByInterceptorTest")
                .request(TEXT_PLAIN);
    }

    @AfterEach
    void destroy() {
        client.close();
    }

    @Test
    void securedOk() {
        assertEquals("was called property", target
                .header("Authorization", "Bearer ok")
                .get(String.class));
    }

    @Test
    void securedKo() {
        assertThrows(ForbiddenException.class, () -> target.get(String.class));
        assertThrows(ForbiddenException.class, () -> target.header("Authorization", "Bearer wrong").get(String.class));
    }

    @WebFilter("/SecuredByInterceptorTest")
    public static class JwtMock implements Filter {
        @Inject
        private GeronimoJwtAuthExtension extension;

        @Override
        public void doFilter(final ServletRequest request, final ServletResponse response,
                             final FilterChain chain) throws ServletException, IOException {
            if (ofNullable(HttpServletRequest.class.cast(request).getHeader("Authorization"))
                    .map("Bearer ok"::equals)
                    .orElse(false)) {
                // mock jwt token for the test - avoid to set it up properly which is not the goal here
                extension.execute(
                        () -> JsonWebToken.class.cast(Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                                new Class<?>[]{JsonWebToken.class},
                                (proxy, method, args) -> Set.of(Claims.sub.name()))),
                        () -> chain.doFilter(request, response));
            } else {
                chain.doFilter(request, response);
            }
        }
    }

    @ApplicationScoped
    @Path("SecuredByInterceptorTest")
    public static class Endpoint {
        @GET
        @Produces(TEXT_PLAIN)
        @SecuredBy("return exists(jwt)")
        public String call() {
            return "was called property";
        }
    }
}
