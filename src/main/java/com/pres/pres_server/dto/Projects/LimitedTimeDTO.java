package com.pres.pres_server.dto.Projects;

import lombok.AllArgsConstructor;
import lombok.*;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimitedTimeDTO {
    private int minute;
    private int second;
}