package com.bibliotheque.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

import com.bibliotheque.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Chaîne de sécurité (ADR-004, ARCH-socle §6) : session serveur, CSRF par cookie, autorisations par
 * famille d'URL, erreurs 401/403 au format ProblemDetail. Toute URL non déclarée est refusée.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                            SecurityContextRepository securityContextRepository) throws Exception {
        ProblemDetailWriter writer = new ProblemDetailWriter(objectMapper);

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .requestCache(RequestCacheConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                writer.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED,
                                        "Votre session a expiré ou vous n'êtes pas connecté."))
                        .accessDeniedHandler((request, response, ex) -> {
                            if (ex instanceof CsrfException) {
                                writer.write(response, HttpStatus.FORBIDDEN, ErrorCode.CSRF_TOKEN_INVALID,
                                        "Le jeton de sécurité est absent ou invalide. Rechargez la page.");
                            } else {
                                writer.write(response, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                                        "Vous n'avez pas les droits nécessaires pour effectuer cette action.");
                            }
                        }))
                .addFilterBefore(new PasswordChangeRequiredFilter(writer), AuthorizationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        // Authentification (ARCH-authentification §6)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/api/v1/staff-accounts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/password-reset")
                        .hasAnyRole("LIBRARIAN", "ADMIN")
                        // Catalogue (ARCH-catalogue §6) : lecture publique, gestion réservée au personnel
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                        .requestMatchers("/api/v1/books/**", "/api/v1/authors/**", "/api/v1/categories/**",
                                "/api/v1/copies/**").hasAnyRole("LIBRARIAN", "ADMIN")
                        // Adhérents (ARCH-adherents §6) : gestion par le personnel, espace de l'adhérent connecté
                        .requestMatchers("/api/v1/members/**").hasAnyRole("LIBRARIAN", "ADMIN")
                        .requestMatchers("/api/v1/me/**").hasRole("MEMBER")
                        // Circulation (ARCH-circulation §6) : la remise gracieuse est réservée à l'administrateur
                        .requestMatchers(HttpMethod.POST, "/api/v1/fines/*/waiver").hasRole("ADMIN")
                        .requestMatchers("/api/v1/loans/**", "/api/v1/returns", "/api/v1/reservations/**",
                                "/api/v1/payments", "/api/v1/fines/**").hasAnyRole("LIBRARIAN", "ADMIN")
                        // Pilotage (ARCH-pilotage §5)
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard").hasAnyRole("LIBRARIAN", "ADMIN")
                        .requestMatchers("/api/v1/statistics/**", "/api/v1/settings/**", "/api/v1/audit-entries/**")
                        .hasRole("ADMIN")
                        // Exploitation : sondes (non relayées par nginx) et documentation (profil dev seulement)
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** BCrypt par défaut, empreintes préfixées {@code {bcrypt}} (ADR-004 §6). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
