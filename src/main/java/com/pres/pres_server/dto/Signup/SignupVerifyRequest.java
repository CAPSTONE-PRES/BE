package com.pres.pres_server.dto.Signup;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignupVerifyRequest {
    private String email;
    private String authNum;
}
