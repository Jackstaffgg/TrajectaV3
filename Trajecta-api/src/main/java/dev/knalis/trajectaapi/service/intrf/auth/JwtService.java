package dev.knalis.trajectaapi.service.intrf.auth;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.function.Function;

/**
 * JWT utility service for token creation and validation.
 */
public interface JwtService {
    /** Extracts username (subject) from token. */
    String extractUsername(String token);

    /** Extracts a specific claim via provided resolver. */
    <T> T extractClaim(String token, Function<Claims, T> claimsResolver);

    /** Generates token for user details with optional built-in claims. */
    String generateToken(UserDetails userDetails);

    /** Generates token for user details with custom extra claims. */
    String generateToken(Map<String, Object> extraClaims, UserDetails userDetails);

    /** Validates token against target user details and expiration. */
    boolean isTokenValid(String token, UserDetails userDetails);
}


