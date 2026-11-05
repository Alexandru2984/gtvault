package gtshop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    @Value(value="${jwt.secret}")
    private String jwtSecret;
    @Value(value="${jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = this.jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            keyBytes = paddedKey;
        }
        return Keys.hmacShaKeyFor((byte[])keyBytes);
    }

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails)authentication.getPrincipal();
        return this.generateToken(userDetails.getUsername());
    }

    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + this.jwtExpiration);
        return Jwts.builder().subject(username).issuedAt(now).expiration(expiryDate).signWith((Key)this.getSigningKey()).compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = (Claims)Jwts.parser().verifyWith(this.getSigningKey()).build().parseSignedClaims((CharSequence)token).getPayload();
        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(this.getSigningKey()).build().parseSignedClaims((CharSequence)token);
            return true;
        }
        catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}

