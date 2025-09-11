package com.pres.pres_server.dto.FindId;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class FindIdResponse {
    private boolean success;
    private String message;
    private String maskedEmail;
}
