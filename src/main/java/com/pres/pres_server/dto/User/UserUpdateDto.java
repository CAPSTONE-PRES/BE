package com.pres.pres_server.dto.User;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class UserUpdateDto {
    private String email;
    private String username;
    private String password;
}
