package com.liongirls.gradebattle.global.config;

import com.liongirls.gradebattle.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return loginId -> {
            var user = users.findByLoginId(loginId)
                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
            return User.withUsername(user.getUserId().toString())
                    .password(user.getPasswordHash()).roles("USER").build();
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository repository) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(repository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/users/csrf").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/users/signup", "/api/users/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                writeResponse(response, 401, false, "USER_401", "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, e) ->
                                writeResponse(response, 403, false, "COMMON_403", "접근 권한 또는 CSRF 토큰을 확인해 주세요.")))
                .logout(logout -> logout
                        .logoutUrl("/api/users/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                writeResponse(response, 200, true, "COMMON_200", "로그아웃되었습니다.")))
                .build();
    }

    private static void writeResponse(HttpServletResponse response, int status, boolean success,
                                      String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"isSuccess\":" + success + ",\"code\":\"" + code
                + "\",\"message\":\"" + message + "\",\"result\":null}");
    }
}
