package com.ridematching.engine.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String role;
    private Long id;        // ← added
    private String name;    // ← added
}