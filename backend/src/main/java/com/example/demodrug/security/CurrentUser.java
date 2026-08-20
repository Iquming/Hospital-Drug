package com.example.demodrug.security;

public record CurrentUser(
        Long id,
        String username,
        String displayName,
        String role,
        String department
) {
    public String operatorLabel() {
        String name = displayName == null || displayName.isBlank() ? username : displayName;
        return name + "(" + role + ")";
    }
}
