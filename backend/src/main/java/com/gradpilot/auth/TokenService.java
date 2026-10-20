package com.gradpilot.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.gradpilot.user.User;

/**
 * Issues signed login tokens. The token subject is the user's id.
 */
@Service
public class TokenService {

    static final String ISSUER = "gradpilot";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this(encoder, properties, Clock.systemUTC());
    }

    TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.ttl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
