package com.ridematching.engine.auth;

import com.ridematching.engine.entity.User;
import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private User.Role role;       // RIDER or DRIVER
    private String phone;
    private String vehicleType;   // null for riders
}