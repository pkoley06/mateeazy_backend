package com.matcher.matchmate_backend.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    @Getter
    private final long expirationMs;
    private final String issuer;

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value ("${jwt.expiration-ms}") long expirationMs,
        @Value("${jwt.issuer}") String issuer
    ){
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.issuer = issuer;
        
    }

    /**
     * Generates a stateless JWT token containing the user's internal ID and Firebase UID.
     */
    public String generateToken(UUID userId, String firebaseUid){
        Date now = new Date();

        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("firebaseUid", firebaseUid)
                .claim("role", "ROLE_USER")
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();

    }

    /**
     * Generates a stateless JWT for an admin console user. Carries "aud": "admin" so this
     * token can never be accepted by the mobile-user JwtAuthenticationFilter, and vice versa.
     */
    public String generateAdminToken(Long adminId, String username){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim("username", username)
                .claim("role", "ROLE_ADMIN")
                .audience().add("admin").and()
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    public boolean isAdminToken(String token){
        Claims claims = getClaimsFromToken(token);
        return claims.getAudience() != null && claims.getAudience().contains("admin");
    }

    public Long getAdminIdFromToken(String token){
        Claims claims = getClaimsFromToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getUsernameFromToken(String token){
        Claims claims = getClaimsFromToken(token);
        return claims.get("username", String.class);
    }

    private Claims getClaimsFromToken(String token){
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts internal User ID from token subject.
     */
    public UUID getUserIdFromToken(String token){
        Claims claims = getClaimsFromToken(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extracts Firebase UID claim.
     */
    public String getFirebaseUidFromToken(String token){
        Claims claims = getClaimsFromToken(token);
        return claims.get("firebaseUid", String.class);
    }

    /**
     * Validates whether the token signature is authentic and not expired.
     */
    public boolean validateToken(String token){
        try{
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch(JwtException | IllegalArgumentException e){
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }


}
