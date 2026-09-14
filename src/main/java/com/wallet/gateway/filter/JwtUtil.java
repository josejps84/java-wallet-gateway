package com.wallet.gateway.filter;

import com.wallet.gateway.core.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    private final SecretKey secretKey;

    // Inyecta la clave secreta desde tu archivo application.yml
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // JJWT requiere una llave con un tamaño seguro (mínimo 256 bits / 32 caracteres)
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Valida el token extrayendo susClaims (propiedades).
     * Si la firma es incorrecta o está expirado, lanzará una excepción automáticamente.
     */
    public Result<Claims, AuthenticationResult> validateToken(String token) {
        try {
            return Result.success(Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (Exception e) {
            log.error("error authenticating user with token {}", e.getMessage());
            return Result.failure(AuthenticationResult.NOT_AUTHORIZED);
        }
    }

    /**
     * Opcional: Verifica manualmente si el token ha expirado
     * (Aunque parseSignedClaims ya lanza una excepción si expiró)
     */
    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
