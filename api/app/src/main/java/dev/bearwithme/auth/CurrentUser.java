package dev.bearwithme.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Public API of the auth module: the id of the user behind the current request. */
@Component
public class CurrentUser {

    public long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaim(TokenService.USER_ID_CLAIM) instanceof Number id) {
            return id.longValue();
        }
        throw new IllegalStateException("No authenticated user in the security context");
    }
}
