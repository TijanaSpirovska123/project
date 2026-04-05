package com.example.marketing.configuration;

import com.example.marketing.auth.JwtAuthenticationFilter;
import com.example.marketing.oauth.handler.OAuth2LoginSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.util.*;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthFilter;
  private final AuthenticationProvider authenticationProvider;
  private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

  @Value("${app.cors.allowed-origins}")
  private String allowedOrigins;

  // ⬇️ Put your Login-for-Business configuration ID in application.yml/properties
  @Value("${facebook.login.config-id:}")
  private String facebookLoginConfigId;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                 ClientRegistrationRepository clientRegistrationRepository) throws Exception {

    DefaultOAuth2AuthorizationRequestResolver defaultResolver =
            new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");

    OAuth2AuthorizationRequestResolver resolver = new OAuth2AuthorizationRequestResolver() {
      @Override
      public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest original = defaultResolver.resolve(request);
        return customize(request, original);
      }

      @Override
      public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest original = defaultResolver.resolve(request, clientRegistrationId);
        return customize(request, original, clientRegistrationId);
      }

      private OAuth2AuthorizationRequest customize(HttpServletRequest request,
                                                   OAuth2AuthorizationRequest original) {
        if (original == null) return null;
        String uri = request.getRequestURI();
        boolean isFacebook = uri != null && uri.startsWith("/oauth2/authorization/")
                && "facebook".equalsIgnoreCase(uri.substring("/oauth2/authorization/".length()));
        return addConfigIdIfNeeded(original, isFacebook);
      }

      private OAuth2AuthorizationRequest customize(HttpServletRequest request,
                                                   OAuth2AuthorizationRequest original,
                                                   String clientRegistrationId) {
        if (original == null) return null;
        boolean isFacebook = "facebook".equalsIgnoreCase(clientRegistrationId);
        return addConfigIdIfNeeded(original, isFacebook);
      }

      private OAuth2AuthorizationRequest addConfigIdIfNeeded(OAuth2AuthorizationRequest original,
                                                             boolean isFacebook) {
        if (isFacebook && facebookLoginConfigId != null && !facebookLoginConfigId.trim().isEmpty()) {
          Map<String, Object> extra = new HashMap<>(original.getAdditionalParameters());
          extra.put("config_id", facebookLoginConfigId);
          return OAuth2AuthorizationRequest.from(original)
                  .additionalParameters(extra)
                  .build();
        }
        return original;
      }
    };

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

                            // Meta OAuth callback — public, state param ties it to local user

                            "/api/oauth/meta/callback",

                            // Swagger/OpenAPI endpoints
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

            )
            .logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    .addLogoutHandler((request, response, authentication) -> {
                      SecurityContextHolder.clearContext();
                      CookieUtil.clearCookie(response, "JSESSIONID", "/");
                      CookieUtil.clearCookie(response, "JSESSIONID", "/api");
                      CookieUtil.clearOauthState(response);
                    })
                    .logoutSuccessHandler((request, response, authentication) -> response.setStatus(200))
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
            );



    return http.build();
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
