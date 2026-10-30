package com.ledgercore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.shared.web.ProblemHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

    private static final String READ = "SCOPE_ledger.read";
    private static final String WRITE = "SCOPE_ledger.write";

    @Bean
    SecurityFilterChain api(HttpSecurity http, ObjectMapper json) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/transfers/*/approve", "/v1/transfers/*/reject",
                    "/v1/transfers/*/reverse", "/v1/accounts/*/deposits", "/v1/fraud/**").hasRole("OPS")
                .requestMatchers("/v1/audit/**", "/v1/accounts/*/integrity").hasRole("OPS")
                .requestMatchers(HttpMethod.GET, "/v1/**").hasAuthority(READ)
                .requestMatchers(HttpMethod.POST, "/v1/**").hasAuthority(WRITE)
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter()))
                .authenticationEntryPoint((request, response, e) ->
                    writeProblem(response, json, HttpStatus.UNAUTHORIZED, "unauthenticated", "A valid bearer token is required"))
                .accessDeniedHandler((request, response, e) ->
                    writeProblem(response, json, HttpStatus.FORBIDDEN, "forbidden", "Token lacks the required scope or role")))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    writeProblem(response, json, HttpStatus.UNAUTHORIZED, "unauthenticated", "A valid bearer token is required"))
                .accessDeniedHandler((request, response, e) ->
                    writeProblem(response, json, HttpStatus.FORBIDDEN, "forbidden", "Token lacks the required scope or role")));
        return http.build();
    }

    /**
     * Scopes come from the standard {@code scope} claim; back-office users additionally carry
     * {@code roles: ["ops"]}, which becomes ROLE_OPS.
     */
    static JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            roles(jwt).forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
            return authorities;
        });
        return converter;
    }

    private static List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper json, HttpStatus status, String code,
                                     String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ProblemHandler.problem(status, code, detail));
    }
}
