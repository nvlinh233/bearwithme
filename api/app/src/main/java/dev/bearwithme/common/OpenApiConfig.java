package dev.bearwithme.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Adds the "Authorize" button to Swagger UI: paste the accessToken from /api/auth/login. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "BearWithMe API", version = "v1"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig {

    static final String BEARER = "bearer";
}
