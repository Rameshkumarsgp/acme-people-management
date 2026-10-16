package com.acme.peoplemanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestClientTest {

    @Test
    void identifiesTheClientByItsRemoteAddress() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.2.0.9");

        assertThat(RequestClient.key(request)).isEqualTo("10.2.0.9");
    }

    @Test
    void ignoresForwardedAddressHeadersBecauseAClientCanForgeThem() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.2.0.9");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertThat(RequestClient.key(request)).isEqualTo("10.2.0.9");
    }

    @Test
    void carriesCredentialsWhenAnAuthorizationHeaderIsPresent() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic aHI6c2VjcmV0");

        assertThat(RequestClient.carriesCredentials(request)).isTrue();
    }

    @Test
    void carriesNoCredentialsWithoutAnAuthorizationHeader() {
        assertThat(RequestClient.carriesCredentials(new MockHttpServletRequest())).isFalse();
    }
}
