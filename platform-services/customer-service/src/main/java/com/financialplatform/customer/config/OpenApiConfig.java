package com.financialplatform.customer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customerServiceOpenAPI() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title("Customer Service API")
                                .description(
                                        "REST APIs for customer profile management, " +
                                                "customer search, lifecycle management, " +
                                                "and customer address history."
                                )
                                .version("1.0.0")
                                .contact(
                                        new Contact()
                                                .name("Enterprise Financial Data Platform")
                                )
                );
    }
}