package com.wallet.gateway.filter;

import com.wallet.gateway.core.Result;
import com.wallet.gateway.core.UserType;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Set;

@Component
@Slf4j
public class AuthenticationFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private final JwtUtil jwtUtil;

    // Lista de rutas que saltarán la validación de JWT
    private static final Set<String> OPEN_API_ENDPOINTS = Set.of(
            "/api/users/login",
            "/api/users/register",
            "/api/accounts/new"
    );

    // Lista de rutas que saltarán la validación de JWT
    private static final Set<String> ADMIN_USER_ENDPOINTS = Set.of(
//            "/api/accounts"
    );

    // Constructor para inyección de dependencias
    public AuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        try {
            log.info("Authentication filter invoked for path: {}", request.path());
            String path = request.path();

            // Java 21+: Comprobar si la ruta actual está en la lista blancaBasicDeserializerFactory
            boolean isSecured = switch (path) {
                case String p when OPEN_API_ENDPOINTS.stream().anyMatch(p::contains) -> false;
                default -> true;
            };

            if (isSecured) {
                ServerResponse UNAUTHORIZED = isAuthorized(request, path);
                if (UNAUTHORIZED != null) {
                    log.info("Unauthorized access attempt to path: {}", path);
                    return UNAUTHORIZED;
                }
            }

            log.info("Access granted to path: {}", path);
            // Si es una ruta libre o el token es válido, continúa al siguiente paso
            return next.handle(request);
        }
        catch (Exception e) {
            log.error("Error during authentication filter processing: {}", e.getMessage(), e);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error during authentication processing.");
        }
    }

    private @Nullable ServerResponse isAuthorized(ServerRequest request, String path) {
        HttpServletRequest servletRequest = request.servletRequest();
        String authHeader = servletRequest.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.error("Missing or invalid Authorization header for path: {}", path);
            return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                    .body("Falta el token o la cabecera Authorization es inválida");
        }

        String token = authHeader.substring(7);
        Result<Claims,AuthenticationResult> result = jwtUtil.validateToken(token);

        if (result instanceof Result.Failure<Claims, AuthenticationResult>(AuthenticationResult error)) {
            log.error("Invalid token for path: {}. Error: {}", path, error);
            // Token inválido, puedes manejar el error según sea necesario
            return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                    .body("Token no autorizado, modificado o expirado: " + error);
        } else if (result instanceof Result.Success<Claims, AuthenticationResult>(Claims claims)) {
            // Token válido, puedes extraer información del usuario si lo deseas
            String userType = claims.get("userType", String.class);

            if (!UserType.ADMIN.name().equals(userType) && ADMIN_USER_ENDPOINTS.stream().anyMatch(path::contains)) {
                log.error("User type {} is not authorized to access path: {}", userType, path);
                return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                        .body("Token not authorized, modified or expired.");
            }
        }
        return null;
    }
}