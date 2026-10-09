package com.spring.eCommerce.security;

import com.spring.eCommerce.exception.CustomAccessDeniedHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;

@EnableWebSecurity
@EnableMethodSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    private final PasswordEncoder passwordEncoder;

    private final AuthFilter authFilter;

    private final JwtUnAuthResponse jwtUnAuthResponse;

    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authProvider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/configuration/**",
                                "/webjars/**"
                        ).permitAll()
                        // Public storefront pages and static assets (Thymeleaf).
                        .requestMatchers(
                                "/", "/shop", "/product/**", "/search",
                                "/css/**", "/js/**", "/images/**",
                                "/login", "/register", "/error"
                        ).permitAll()
                        // Public auth APIs (JWT).
                        .requestMatchers("/api/auth/login", "/api/auth/registerUser").permitAll()
                        // Public product/category browsing (read-only).
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                        // Provider callbacks are authenticated by provider signatures (e.g. Paymob HMAC), not JWT.
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/payments/*").permitAll()
                        // Customer browser redirect after checkout; read-only and HMAC-verified.
                        .requestMatchers(HttpMethod.GET, "/api/payments/return/*", "/payments/return/*").permitAll()
                        .requestMatchers("/api/auth/registerAdmin").hasAuthority("admin")
                        // Product/category writes are admin-only (server-side enforcement).
                        .requestMatchers(HttpMethod.POST, "/api/products/**", "/api/categories/**").hasAuthority("admin")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**", "/api/categories/**").hasAuthority("admin")
                        .requestMatchers(HttpMethod.PATCH, "/api/products/**", "/api/categories/**").hasAuthority("admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**", "/api/categories/**").hasAuthority("admin")
                        // Admin web area.
                        .requestMatchers("/admin/**").hasAuthority("admin")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtUnAuthResponse)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .csrf(csrf -> csrf
                        // REST + webhooks stay stateless; browser forms use standard POSTs.
                        .ignoringRequestMatchers("/api/**")
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public SecurityContextHolderAwareRequestFilter securityContextHolderAwareRequestFilter() {
        return new SecurityContextHolderAwareRequestFilter();
    }
}
