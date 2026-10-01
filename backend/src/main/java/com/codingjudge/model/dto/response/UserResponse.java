package com.codingjudge.model.dto.response;

import com.codingjudge.model.entity.User;

public class UserResponse {

    private Long id;
    private String email;
    private String username;
    private String fullName;
    private String role;

    public static UserResponse from(User user) {
        UserResponse response = new UserResponse();
        response.id = user.getId();
        response.email = user.getEmail();
        response.username = user.getUsername();
        response.fullName = user.getFullName();
        response.role = user.getRole().name();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role;
    }
}
