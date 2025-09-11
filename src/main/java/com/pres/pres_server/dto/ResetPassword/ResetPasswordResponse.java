package com.pres.pres_server.dto.ResetPassword;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ResetPasswordResponse {
    private boolean success;
    private String message;
}
