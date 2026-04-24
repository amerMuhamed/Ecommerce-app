package com.spring.eCommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenUtils {
    private static String TOKEN_SECRET;
    private static Long ACCESS_TOKEN_VALIDITY;
    private static Long REFRESH_TOKEN_VALIDITY;

    @Value("${auth.secret}")
    private String tokenSecret;

    @Value("${auth.access.expiration}")
    private Long accessTokenValidity;

    @Value("${auth.refresh.expiration}")
    private Long refreshTokenValidity;

    public static String generateToken(final String username, final String tokenId, boolean isRefresh) {
        return Jwts.builder()
                .setSubject(username)
                .setId(tokenId)
                .setIssuedAt(new Date())
                .setIssuer("app-service")
                .setExpiration(calculateTokenExpirationDate(isRefresh))
                .claim("created", Calendar.getInstance().getTime())
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TOKEN_SECRET)), SignatureAlgorithm.HS512)
                .compact();
    }

    private static Date calculateTokenExpirationDate(boolean isRefresh) {
        return new Date(System.currentTimeMillis() + (isRefresh ? REFRESH_TOKEN_VALIDITY : ACCESS_TOKEN_VALIDITY));
    }

    @PostConstruct
    public void init() {
        TOKEN_SECRET = tokenSecret;
        ACCESS_TOKEN_VALIDITY = accessTokenValidity;
        REFRESH_TOKEN_VALIDITY = refreshTokenValidity;
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(TOKEN_SECRET)
                .parseClaimsJws(token)
                .getBody();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, AppUserDetail user) {
        String username = getUsernameFromToken(token);
        return (username.equals(user.getUsername()) && !isTokenExpired(token));
    }

    public boolean isTokenExpired(String token) {
        Date expirationDate = getClaims(token).getExpiration();
        return expirationDate.before(new Date());
    }

    public String getTokenId(String token) {
        return getClaims(token).getId();
    }
    public boolean validateToken(String token) {
        // هذه الطريقة سترمي الاستثناءات، وسيتم التقاطها تلقائيًا في GlobalHandling
        Jwts.parser()
                .setSigningKey(TOKEN_SECRET)
                .parseClaimsJws(token);
        return true;
    }

//
//    public boolean validateToken(String token, HttpServletRequest request) {
//        {
//            try {
//                Jwts.parser().setSigningKey(TOKEN_SECRET).parseClaimsJws(token);
//                return true;
//            } catch (SignatureException e) {
//                log.info("Invalid JWT signature");
//                throw  new SecurityException("Invalid JWT signature");
//            }
//            catch (IllegalArgumentException e) {
//                log.info("Jwt claims string is empty");
////                throw new SecurityException("Jwt claims string is empty");
//            }
//       catch (ExpiredJwtException e) {
//            log.info("JWT token is expired");
//            request.setAttribute("ExpiredJwtException", e.getMessage());
////    throw new SecurityException("JWT token is expired");
//        }
//
//            catch (Exception e) {
//                log.info("Invalid JWT token");
//                request.setAttribute("ExpiredJwtException", e.getMessage());
//            }
//              return false;
//            }
//        }
    }
