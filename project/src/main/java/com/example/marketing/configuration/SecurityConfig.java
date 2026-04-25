package com.example.marketing.configuration;

import com.example.marketing.auth.JwtAuthenticationFilter;
import com.example.marketing.oauth.handler.OAuth2LoginFailureHandler;
import com.example.marketing.oauth.handler.OAuth2LoginSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${facebook.login.config-id:}")
    private String facebookLoginConfigId;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ClientRegistrationRepository clientRegistrationRepository)
            throws Exception {

        OAuth2AuthorizationRequestResolver resolver =
                buildAuthorizationRequestResolver(clientRegistrationRepository);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/user/request-password-reset",
                                "/api/user/reset-password",
                                "/api/auth/logout",
                                "/api/oauth/meta/callback",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(a -> a.authorizationRequestResolver(resolver))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            SecurityContextHolder.clearContext();
                            CookieUtil.clearCookie(response, "JSESSIONID", "/");
                            CookieUtil.clearCookie(response, "JSESSIONID", "/api");
                            CookieUtil.clearOauthState(response);
                        })
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(200))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                );

        return http.build();
    }

    /**
     * Builds a resolver that can inject provider-specific extra parameters into
     * the authorization request. Currently adds {@code config_id} for Facebook
     * (Login for Business). To customize another provider, add its registrationId
     * to the switch block in {@link #extraParamsFor}.
     */
    private OAuth2AuthorizationRequestResolver buildAuthorizationRequestResolver(
            ClientRegistrationRepository repo) {

        DefaultOAuth2AuthorizationRequestResolver base =
                new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                OAuth2AuthorizationRequest original = base.resolve(request);
                if (original == null) return null;
                String regId = extractRegistrationId(request.getRequestURI());
                return withExtraParams(original, regId);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request,
                                                      String clientRegistrationId) {
                OAuth2AuthorizationRequest original = base.resolve(request, clientRegistrationId);
                if (original == null) return null;
                return withExtraParams(original, clientRegistrationId);
            }

            private String extractRegistrationId(String uri) {
                String prefix = "/oauth2/authorization/";
                if (uri != null && uri.startsWith(prefix)) return uri.substring(prefix.length());
                return "";
            }

            private OAuth2AuthorizationRequest withExtraParams(OAuth2AuthorizationRequest original,
                                                               String registrationId) {
                Map<String, Object> extra = extraParamsFor(registrationId);
                if (extra.isEmpty()) return original;
                Map<String, Object> merged = new HashMap<>(original.getAdditionalParameters());
                merged.putAll(extra);
                return OAuth2AuthorizationRequest.from(original)
                        .additionalParameters(merged)
                        .build();
            }
        };
    }

    /**
     * Returns any extra query parameters required by a specific OAuth2 provider.
     * Add a new case here when a new platform requires non-standard authorization params.
     */
    private Map<String, Object> extraParamsFor(String registrationId) {
        return switch (registrationId == null ? "" : registrationId.toLowerCase()) {
            case "facebook" -> {
                if (facebookLoginConfigId != null && !facebookLoginConfigId.isBlank()) {
                    yield Map.of("config_id", facebookLoginConfigId);
                }
                yield Map.of();
            }
            // Add additional providers here, e.g.:
            // case "tiktok" -> Map.of("...param...", "...value...");
            default -> Map.of();
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
