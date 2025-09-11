package com.pres.pres_server.dto.EmailAuth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmailAuthVerifyResponse {
    private boolean success;
    private String message;
}
