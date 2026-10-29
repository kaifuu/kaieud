package com.edu.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${edu.jwt.secret}")
    private String secret;

    @Value("${edu.jwt.expire-hours:24}")
    private long expireHours;

    public String createToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireHours * 3600_000L))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /** 解析token，失败返回null */
    public Long parseToken(String token) {
        try {
            Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
            if (claims.getExpiration().before(new Date())) return null;
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}
