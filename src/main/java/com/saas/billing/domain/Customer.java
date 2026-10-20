package com.saas.billing.domain;

public record Customer(String email, String status) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
