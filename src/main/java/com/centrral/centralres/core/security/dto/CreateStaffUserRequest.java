package com.centrral.centralres.core.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateStaffUserRequest {

    @NotBlank
    @Size(max = 30)
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private String phone;

    @NotBlank
    @Size(min = 12, max = 128)
    private String password;

    @NotBlank
    private String role;
}
