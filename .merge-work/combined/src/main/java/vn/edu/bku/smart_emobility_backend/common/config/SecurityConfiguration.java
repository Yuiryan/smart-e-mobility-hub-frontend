package vn.edu.bku.smart_emobility_backend.common.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import vn.edu.bku.smart_emobility_backend.common.security.BearerSessionFilter;
import vn.edu.bku.smart_emobility_backend.common.security.SecurityErrorWriter;
import vn.edu.bku.smart_emobility_backend.common.web.ApiErrors;

@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, BearerSessionFilter bearer,
            SecurityErrorWriter errors) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                // Authentication is supplied explicitly in Authorization, with no auth cookies.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errors.write(response, ApiErrors.response(
                                        HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN",
                                        "A valid Bearer token is required", List.of())))
                        .accessDeniedHandler((request, response, exception) ->
                                errors.write(response, ApiErrors.response(
                                        HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                                        "Access to this resource is denied", List.of()))))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password/recovery/request",
                                "/api/auth/password/recovery/verify", "/api/auth/password/recovery/reset").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/search/hubs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/vehicles/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/hubs", "/api/hubs/*/spaces").authenticated()
                        // The legacy operator controller performs its own role/session checks
                        // and preserves the established UNAUTHORIZED/FORBIDDEN error contract.
                        .requestMatchers(HttpMethod.GET, "/api/operator/network-report").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers("/api/operator/**").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.POST,
                                "/api/reservations", "/api/reservations/*/cancel", "/api/parking-reservations",
                                "/api/pickups", "/api/rides/*/return",
                                "/api/charging-requests").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/operations/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password/change").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsFilter corsFilter(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String origins,
            SecurityErrorWriter errors) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origins.split(","))
                .map(String::strip).filter(origin -> !origin.isEmpty()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Location", "WWW-Authenticate"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        var filter = new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) throws IOException {
                errors.write(((ServletServerHttpResponse) response).getServletResponse(), ApiErrors.response(
                        HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                        "Cross-origin request is not allowed", List.of()));
            }
        });
        return filter;
    }

    @Bean
    public FilterRegistrationBean<BearerSessionFilter> bearerFilterRegistration(
            BearerSessionFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
