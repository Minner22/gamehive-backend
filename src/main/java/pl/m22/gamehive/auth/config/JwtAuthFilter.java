package pl.m22.gamehive.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.TokenBlacklistService;
import pl.m22.gamehive.auth.jwt.service.UserAuthState;
import pl.m22.gamehive.auth.jwt.service.UserAuthStateProvider;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.common.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;
    private final UserAuthStateProvider userAuthStateProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            jwtService.validateToken(jwt, JwtTokenType.ACCESS);

            String jti = jwtService.extractJtiFromToken(jwt);
            if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
                log.debug("Access token blacklisted for request [{}]", request.getRequestURI());
                // Blacklist (i pozostałe nieprawidłowe tokeny) przepuszczamy do authenticationEntryPoint
                // (generyczne 401 ACCESS_DENIED). Własne kody piszemy tylko dla disabled/epoch niżej.
                filterChain.doFilter(request, response);
                return;
            }

            final String email = jwtService.extractEmailFromToken(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserAuthState authState = userAuthStateProvider.getAuthState(email);

                if (!authState.enabled()) {
                    log.debug("User [{}] is disabled, rejecting access token for request [{}]", email, request.getRequestURI());

                    writeUnauthorized(response, ErrorCode.ACCOUNT_DISABLED);

                    return;
                }

                Instant issuedAt = jwtService.extractIssuedAtFromToken(jwt);
                Long invalidAfter = authState.invalidAfter();

                // iat jest w sekundach (ucięte), invalidAfter w ms — token wydany przed unieważnieniem ma
                // zawsze mniejszy iat. Porównanie ostre (<): token z tej samej sekundy też odrzucamy.
                if (invalidAfter != null && issuedAt != null && issuedAt.toEpochMilli() < invalidAfter) {
                    log.debug("User [{}] token has been revoked, rejecting access for request [{}]", email, request.getRequestURI());

                    writeUnauthorized(response, ErrorCode.TOKEN_REVOKED);

                    return;
                }

                // principal = email (String), nie UserDetails — downstream używa tylko authentication.getName().
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        authState.authorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            log.debug("JWT validation failed for request [{}]: {}", request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/refresh") ||
                path.startsWith("/api/v1/auth/login") ||
                path.startsWith("/api/v1/auth/register");
    }

    private void writeUnauthorized(HttpServletResponse response, ErrorCode errorCode) {
        try {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter()
                    .write(objectMapper.writeValueAsString(new ApiError(errorCode.name(), errorCode.getDefaultMessage())));
        }  catch (IOException e) {
            log.warn("Failed to write unauthorized response: {}", e.getMessage());
        }
    }
}
