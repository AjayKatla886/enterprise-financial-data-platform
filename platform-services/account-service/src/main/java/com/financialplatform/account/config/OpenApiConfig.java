package com.financialplatform.account.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI accountServiceOpenAPI() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title("Account Service API")
                                .description(
                                        "REST APIs for account creation, " +
                                                "account retrieval, customer-account lookup, " +
                                                "account status management, and account closure."
                                )
                                .version("1.0.0")
                                .contact(
                                        new Contact()
                                                .name("Enterprise Financial Data Platform")
                                )
                );
    }
}