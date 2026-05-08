package com.dayz.aeroverse.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置后端接口文档的 OpenAPI 元信息和鉴权声明。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@Configuration
public class OpenApiConfiguration {
    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI aeroverseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AeroVerse Navigator API")
                        .description("AeroVerse Navigator backend API documentation")
                        .version("0.0.1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
