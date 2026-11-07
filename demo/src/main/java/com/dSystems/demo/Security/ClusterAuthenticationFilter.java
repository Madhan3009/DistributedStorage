package com.dSystems.demo.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates service-to-service requests between the coordinator and storage nodes using a
 * shared secret sent in the {@value #HEADER_NAME} header. Applies only to cluster paths, so the
 * secret cannot be used to access user-facing APIs.
 */
@Component
public class ClusterAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Cluster-Token";

    private static final int MIN_SECRET_LENGTH = 32;

    private final byte[] clusterSecret;

    public ClusterAuthenticationFilter(@Value("${app.cluster.secret}") String clusterSecret) {
        if (clusterSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.cluster.secret must be at least " + MIN_SECRET_LENGTH + " characters long");
        }
        this.clusterSecret = clusterSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(path.startsWith("/internal/")
                || path.equals("/nodes/register")
                || path.equals("/nodes/heartbeat"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER_NAME);
        // Constant-time comparison, so response timing does not reveal the secret.
        if (token != null && MessageDigest.isEqual(clusterSecret, token.getBytes(StandardCharsets.UTF_8))) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "cluster-node", null, List.of(new SimpleGrantedAuthority("ROLE_CLUSTER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
