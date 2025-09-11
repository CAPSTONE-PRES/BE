package com.pres.pres_server.dto.Signup;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SignupVerifyResponse {
    private boolean success;
    private String message;
}
