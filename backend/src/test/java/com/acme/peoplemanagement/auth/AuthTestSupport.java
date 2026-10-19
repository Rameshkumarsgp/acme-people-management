package com.acme.peoplemanagement.auth;

import org.springframework.context.annotation.Import;

/** Lets web slice tests in other packages switch on the real shared login without opening up SecurityConfig. */
@Import(SecurityConfig.class)
public class AuthTestSupport {
}
