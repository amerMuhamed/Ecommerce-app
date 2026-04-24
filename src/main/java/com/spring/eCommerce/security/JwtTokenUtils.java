package com.spring.eCommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenUtils {

    private static final String ISSUER = "app-service";
    private static final String TOKEN_TYPE = "tokenType";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    @Value("${auth.secret}")
    private String tokenSecret;

    @Value("${auth.access.expiration}")
    private Long accessTokenValidity;

    @Value("${auth.refresh.expiration}")
    private Long refreshTokenValidity;

    private Key signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(tokenSecret));
    }

    public String generateToken(String username, String tokenId, boolean isRefresh) {
        Date now = new Date();
        Date expiration = new Date(
                System.currentTimeMillis() + (isRefresh ? refreshTokenValidity : accessTokenValidity)
        );

        return Jwts.builder()
                .setSubject(username)
                .setId(tokenId)
                .setIssuedAt(now)
                .setIssuer(ISSUER)
                .setExpiration(expiration)
                .claim("created", now)
                .claim(TOKEN_TYPE, isRefresh ? REFRESH : ACCESS)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(signingKey)
                .parseClaimsJws(token)
                .getBody();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getTokenId(String token) {
        return getClaims(token).getId();
    }

    public boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    public boolean isAccessToken(String token) {
        return ACCESS.equals(getClaims(token).get(TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH.equals(getClaims(token).get(TOKEN_TYPE, String.class));
    }

    public boolean isTokenValid(String token, AppUserDetail user) {
        String username = getUsernameFromToken(token);
        return username.equals(user.getUsername())
                && !isTokenExpired(token)
                && isAccessToken(token);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}