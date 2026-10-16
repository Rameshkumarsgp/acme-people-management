package com.acme.peoplemanagement.auth;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shared HR login: HTTP Basic, no session and no cookie. Every endpoint needs the login except health.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfig {

    private static final int MINIMUM_BCRYPT_STRENGTH = 4;

    @Bean
    LoginAttemptThrottle loginAttemptThrottle(final Clock clock, final AuthProperties properties) {
        return new LoginAttemptThrottle(clock, properties.maxFailedAttempts(), properties.lockoutWindow());
    }

    @Bean
    ProblemResponseWriter problemResponseWriter(final JsonMapper jsonMapper) {
        return new ProblemResponseWriter(jsonMapper);
    }

    /**
     * The cheapest BCrypt cost. Basic authentication verifies the password on every request, and the
     * password is already held in plain text by {@link AuthProperties}, so a higher cost would only add
     * latency to every call without protecting anything.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(MINIMUM_BCRYPT_STRENGTH);
    }

    /**
     * Development shortcut: the one HR account comes from environment variables. A production system
     * would hand login to an identity provider instead of keeping its own accounts, and would read any
     * credentials from a secrets vault that controls access and rotates them. See docs/tradeoffs.md.
     */
    @Bean
    UserDetailsService userDetailsService(final AuthProperties properties, final PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(User.withUsername(properties.username())
                .password(passwordEncoder.encode(properties.password()))
                .roles("HR")
                .build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            final HttpSecurity http, final LoginAttemptThrottle throttle, final ProblemResponseWriter problemWriter)
            throws Exception {
        final UnauthorizedEntryPoint entryPoint = new UnauthorizedEntryPoint(throttle, problemWriter);
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .addFilterBefore(new ThrottleFilter(throttle, problemWriter), BasicAuthenticationFilter.class);
        return http.build();
    }
}
