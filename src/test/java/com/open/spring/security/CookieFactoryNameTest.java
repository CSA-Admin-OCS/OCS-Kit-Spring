package com.open.spring.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The JWT cookie name is configurable, for deployments that share a parent domain. */
class CookieFactoryNameTest {

    @Test
    void defaultsToTheStandardName() {
        assertEquals("jwt_java_spring", new CookieFactory().jwtCookie("t").getName());
    }

    @Test
    void setAndDeleteCookiesUseTheConfiguredName() {
        CookieFactory factory = new CookieFactory();
        ReflectionTestUtils.setField(factory, "jwtCookieName", "kit_jwt_java_spring");
        assertEquals("kit_jwt_java_spring", factory.jwtCookie("t").getName());
        assertEquals("kit_jwt_java_spring", factory.expiredJwtCookie().getName());
        assertEquals("kit_jwt_java_spring", factory.expiredJwtHostOnlyCookie().getName());
        assertEquals("kit_jwt_java_spring", factory.expiredJwtLegacyLocalhostCookie().getName());
    }
}
