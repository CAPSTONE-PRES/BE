package com.pres.pres_server.dto.EmailAuth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailAuthVerifyRequest {
    @NotBlank
    @Email
    private String email;
    @NotBlank
    private String authCode;
}
