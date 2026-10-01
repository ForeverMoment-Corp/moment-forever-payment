package com.forvmom.MomentForeverPayment.config;

import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${server.servlet.context-path:/api/payment}")
    private String contextPath;

    // ✅ Group Payment APIs (Admin & Public)
    @Bean
    public GroupedOpenApi paymentPublicApi() {
        return GroupedOpenApi.builder()
                .group("1-payment-public")
                .pathsToMatch("/**")
                .displayName("Payment Public API")
                .build();
    }

    @Bean
    public GroupedOpenApi paymentAdminApi() {
        return GroupedOpenApi.builder()
                .group("2-payment-admin")
                .pathsToMatch("/**")
                .displayName("Payment Admin API")
                .build();
    }

    @Bean
    public GroupedOpenApi paymentOutboxApi() {
        return GroupedOpenApi.builder()
                .group("3-payment-outbox")
                .pathsToMatch("/**")
                .displayName("Payment Outbox Monitoring API")
                .build();
    }

    // ✅ Main OpenAPI Configuration
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                // Set the server URL with context path
                .addServersItem(new Server().url("/api/payment"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, createSecurityScheme()));
                       // .addSecuritySchemes("apiKey", createApiKeyScheme()));
    }

    private SecurityScheme createSecurityScheme() {
        return new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .description("Enter JWT token (without 'Bearer' prefix)");
    }

    private SecurityScheme createApiKeyScheme() {
        return new SecurityScheme()
                .name("X-API-Key")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .description("API Key for service-to-service communication");
    }

    private Info apiInfo() {
        return new Info()
                .title("MomentForever Payment API")
                .description("""
                    Payment Service API Documentation
                    
                    **Event-Driven Architecture:**
                    - Consumes: `payment-requested` events from Kafka
                    - Produces: `payment-processed` or `payment-failed` events
                    
                    **REST Endpoints (for admin/monitoring only):**
                    - Health checks
                    - Outbox monitoring
                    - Manual retry of dead letters
                    - Payment status queries
                    
                    **Core Flow:**
                    1. Payment requests arrive via Kafka (not REST)
                    2. Payment is processed (simulated with configurable failure rate)
                    3. Result is published back to Kafka
                    4. Outbox pattern ensures reliability
                    
                    **Base URL:** `/api/payment`
                    """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("Payment Service Team")
                        .email("payment@forvmom.com")
                        .url("https://forvmom.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"))
                .termsOfService("https://forvmom.com/terms");
    }
}