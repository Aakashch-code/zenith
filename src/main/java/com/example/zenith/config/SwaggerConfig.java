package com.example.zenith.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Zenith Financial Ledger API")
                        .version("1.0.0")
                        .description("""
                                Financial Ledger & Audit System built with Spring Boot.

                                Features:
                                - Account Management
                                - Deposit & Withdrawal
                                - Fund Transfer
                                - Double-Entry Ledger
                                - Transaction Reversal
                                - Transfer Fee & GST
                                - JWT Authentication
                                - Audit Logging
                                - Fraud Detection
                                - Scheduled Interest
                                """)
                        .contact(new Contact()
                                .name("Aakash")
                        )
                )
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(SECURITY_SCHEME_NAME)
                )
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .name(SECURITY_SCHEME_NAME)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Enter JWT token as: Bearer <token>"
                                                )
                                )
                );
    }
}