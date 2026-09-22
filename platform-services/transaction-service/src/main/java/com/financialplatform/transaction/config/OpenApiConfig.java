package com.financialplatform.transaction.config;

import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer correlationIdHeaderCustomizer() {

        return openApi -> {

            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths()
                    .values()
                    .stream()
                    .flatMap(pathItem ->
                            pathItem.readOperations().stream()
                    )
                    .forEach(operation -> {

                        boolean headerAlreadyExists =
                                operation.getParameters() != null
                                        && operation.getParameters()
                                        .stream()
                                        .anyMatch(parameter ->
                                                "X-Correlation-ID"
                                                        .equalsIgnoreCase(
                                                                parameter.getName()
                                                        )
                                        );

                        if (!headerAlreadyExists) {
                            operation.addParametersItem(
                                    new Parameter()
                                            .in("header")
                                            .name("X-Correlation-ID")
                                            .required(false)
                                            .description(
                                                    "Optional request correlation ID. "
                                                            + "A UUID is generated when omitted."
                                            )
                                            .example("day20-test-001")
                            );
                        }
                    });
        };
    }
}