package com.acme.peoplemanagement.auth;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    /** Reaching this method means the shared login was accepted. */
    @GetMapping("/me")
    CurrentUserResponse currentUser(final Principal principal) {
        return new CurrentUserResponse(principal.getName());
    }
}
