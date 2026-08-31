package com.hospital.pharmacy.security;

import com.hospital.pharmacy.dao.SysUserDao;
import com.hospital.pharmacy.entity.SysUser;
import com.hospital.pharmacy.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private TokenService tokenService;

    @Resource
    private SysUserDao sysUserDao;

    @Resource
    private AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            TokenService.TokenPayload payload = tokenService.parse(header.substring(7));
            if (payload != null && !tokenService.isRevoked(header.substring(7))) {
                SysUser user = sysUserDao.findById(payload.userId());
                if (user != null && "ENABLED".equals(user.getStatus())) {
                    CurrentUser currentUser = authService.toCurrentUser(user);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            currentUser,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
