package com.bank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Demo identity configuration for the gateway-issued JWT (see docs/ADR-005). */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** Disable only for local scripts that predate auth. */
    private boolean enabled = true;
    private String secret = "ddbs-demo-secret-change-me-please-32b";
    private String issuer = "ddbs-api-gateway";
    private long ttlMinutes = 480;
    private List<User> users = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public record User(String username, String password, String role,
                       String accountId, String displayName) {
    }
}
