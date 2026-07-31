package com.dSystems.demo.Security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * THE JWT TICKET MACHINE (TICKET GENERATOR & VALIDATOR).
 * 
 * Think of this class as the "admission ticket printing office". 
 * 1. When a user logs in, this machine prints a "ticket" (a JWT token) containing their username.
 * 2. It stamps/signs the ticket with a secret cryptographic seal (using a private secret key).
 * 3. When the user shows this ticket later, this class verifies that the signature is genuine
 *    and that the ticket hasn't expired.
 * 
 * JWT (JSON Web Token) is a standard format for these tickets. It looks like a long string of letters
 * separated by dots (e.g., `header.payload.signature`).
 */
@Component
public class JWTHelper {
    
    // The secret password/key phrase used to sign the tokens. Loaded from configuration settings.
    @Value("${app.jwt.secret}")
    private String secret;

    // How many seconds a token remains valid before it expires and the user has to log in again.
    @Value("${app.jwt.expiration-seconds}")
    private long jwtTokenValidity;

    // The actual cryptographic key generated from our secret password text.
    private SecretKey secretKey;

    /**
     * Initialization step.
     * - @PostConstruct: Tells Spring to run this automatically as soon as the helper is created.
     * It converts our secret configuration text into a secure cryptographic key structure.
     */
    @PostConstruct
    void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads a ticket and extracts the username (subject) printed on it.
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * Reads a ticket and extracts the expiration timestamp.
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * A helper method to extract specific pieces of information (claims) from a ticket.
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Unwraps the token and reads all data written inside.
     * It uses our cryptographic key to verify the token's signature. If someone tried to forge/modify
     * the token, this method will detect it and throw an error.
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    /**
     * Checks if a ticket's expiration time has already passed.
     * @return true if expired, false if still valid.
     */
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * Generates a new admission ticket for a user who just successfully logged in.
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, userDetails.getUsername());
    }

    /**
     * Assembles and signs the token.
     * 1. Sets the username (subject).
     * 2. Sets the creation date (issuedAt).
     * 3. Sets the expiration date (creation time + validity duration).
     * 4. Signs the ticket using our private cryptographic key to make it tamper-proof.
     * 5. Compacts it into a single URL-safe string.
     */
    private String doGenerateToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtTokenValidity * 1000))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Verifies if a presented ticket is valid.
     * @return true if the ticket username matches the database user, and the ticket is NOT expired.
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
