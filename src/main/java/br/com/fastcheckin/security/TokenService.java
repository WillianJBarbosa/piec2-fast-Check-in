package br.com.fastcheckin.security;

import br.com.fastcheckin.domain.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class TokenService {

    public record IssuedToken(String accessToken, long expiresInSeconds) {
    }

    private final JwtEncoder jwtEncoder;
    private final Duration timeToLive;

    public TokenService(JwtEncoder jwtEncoder, @Value("${app.security.jwt-ttl-minutes:60}") long ttlMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.timeToLive = Duration.ofMinutes(ttlMinutes);
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("fast-check-in")
                .issuedAt(now)
                .expiresAt(now.plus(timeToLive))
                .subject(user.getUsername())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, timeToLive.toSeconds());
    }
}
