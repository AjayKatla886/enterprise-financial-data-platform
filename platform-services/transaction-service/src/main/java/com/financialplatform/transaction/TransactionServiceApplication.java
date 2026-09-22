package com.financialplatform.transaction;

import com.financialplatform.common.web.CorrelationIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@Import(CorrelationIdFilter.class)
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                TransactionServiceApplication.class,
                args
        );
    }
}