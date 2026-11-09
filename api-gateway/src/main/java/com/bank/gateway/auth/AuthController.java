package com.bank.gateway.auth;

import com.bank.gateway.config.AuthProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo identity endpoints. There is no user database in this slice: two
 * configured users (customer and bank) demonstrate role separation while the
 * gateway issues signed JWTs for the portals.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, String tokenType, String role,
                                String accountId, String displayName, String expiresAt) {
    }

    private final AuthProperties properties;
    private final JwtService jwtService;

    public AuthController(AuthProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<?>> login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return Mono.just(error(HttpStatus.BAD_REQUEST, "username and password are required"));
        }
        return Mono.just(properties.getUsers().stream()
                .filter(u -> u.username().equals(request.username())
                        && u.password().equals(request.password()))
                .findFirst()
                .<ResponseEntity<?>>map(user -> {
                    JwtService.Token token = jwtService.mint(user);
                    return ResponseEntity.ok(new LoginResponse(token.token(), "Bearer",
                            token.role(), token.accountId(), token.displayName(),
                            token.expiresAt().toString()));
                })
                .orElseGet(() -> error(HttpStatus.UNAUTHORIZED, "Invalid username or password")));
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<?>> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Mono.just(error(HttpStatus.UNAUTHORIZED, "Missing bearer token"));
        }
        try {
            JwtService.Claims claims = jwtService.verify(authorization.substring(7));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("username", claims.username());
            body.put("role", claims.role());
            body.put("accountId", claims.accountId());
            body.put("displayName", claims.displayName());
            body.put("expiresAt", claims.expiresAt().toString());
            body.put("authEnabled", properties.isEnabled());
            return Mono.just(ResponseEntity.ok(body));
        } catch (JwtService.UnauthorizedException e) {
            return Mono.just(error(HttpStatus.UNAUTHORIZED, e.getMessage()));
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
