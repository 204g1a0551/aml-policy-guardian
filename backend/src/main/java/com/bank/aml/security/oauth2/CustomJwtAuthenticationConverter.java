package com.bank.aml.security.oauth2;

import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.tenant.TenantContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }

        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = jwt.getClaimAsString("tenantId");
        }
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
        } else {
            tenantId = TenantContext.getTenantId();
        }

        UUID userId;
        String userIdClaim = jwt.getClaimAsString("userId");
        if (userIdClaim == null) {
            userIdClaim = jwt.getClaimAsString("user_id");
        }
        if (userIdClaim != null) {
            try {
                userId = UUID.fromString(userIdClaim);
            } catch (Exception e) {
                userId = UUID.nameUUIDFromBytes(username.getBytes());
            }
        } else {
            userId = UUID.nameUUIDFromBytes(username.getBytes());
        }

        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. Read top-level roles claim
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            for (String r : roles) {
                String auth = r.startsWith("ROLE_") ? r : "ROLE_" + r;
                authorities.add(new SimpleGrantedAuthority(auth));
            }
        }

        // 2. Read realm_access.roles (Keycloak convention)
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String roleStr) {
                    String auth = roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr;
                    authorities.add(new SimpleGrantedAuthority(auth));
                }
            }
        }

        // Default to COMPLIANCE_ANALYST if no roles present
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_COMPLIANCE_ANALYST"));
            authorities.add(new SimpleGrantedAuthority("ROLE_ANALYST"));
        }

        SecurityUserPrincipal principal = new SecurityUserPrincipal(
            userId,
            username,
            "PROTECTED",
            true,
            tenantId,
            authorities
        );

        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}
