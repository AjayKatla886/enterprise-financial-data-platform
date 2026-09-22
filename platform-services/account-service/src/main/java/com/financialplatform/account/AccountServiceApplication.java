package com.financialplatform.account;

import com.financialplatform.common.web.CorrelationIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(CorrelationIdFilter.class)
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                AccountServiceApplication.class,
                args
        );
    }
}