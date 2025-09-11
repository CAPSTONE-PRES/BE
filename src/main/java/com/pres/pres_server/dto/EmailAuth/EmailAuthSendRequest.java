package com.pres.pres_server.dto.EmailAuth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailAuthSendRequest {
    @NotBlank
    @Email
    private String email;
}
