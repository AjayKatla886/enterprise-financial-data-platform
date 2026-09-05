package com.financialplatform.customer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(
                Map.of(
                        "service", "customer-service",
                        "status", "UP",
                        "message", "Customer Service is running"
                )
        );
    }
}