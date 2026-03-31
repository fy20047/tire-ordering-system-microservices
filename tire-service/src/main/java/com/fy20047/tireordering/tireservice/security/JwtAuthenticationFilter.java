package com.fy20047.tireordering.tireservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// 這個檔案用途：
// 在每次請求中解析 Authorization Bearer token，建立 Spring Security 登入上下文。
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 這段欄位用途：呼叫 JWT 驗章服務解析 claims。
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    // 這段方法用途：攔截請求並建立 Authentication，供後續權限規則使用。
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtService.parseToken(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            if (username != null && role != null) {
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
