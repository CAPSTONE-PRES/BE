package com.pres.pres_server.dto.User;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class UserUpdateDto {
    private String email;
    private String username;
    private String password;
    private String profileUrl;
}
