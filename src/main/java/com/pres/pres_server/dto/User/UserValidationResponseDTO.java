package com.pres.pres_server.dto.User;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserValidationResponseDTO {
    private String message;
    private String name;
    private Long userId;
    private String email;
    private String profileUrl;
}