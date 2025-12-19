package com.ecommerce.mediaservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Extract permissions from Auth0 JWT
        Object permissions = jwt.getClaim("permissions");

        if (permissions instanceof List<?>) {
            return ((List<?>) permissions).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(permission -> new SimpleGrantedAuthority("SCOPE_" + permission))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
