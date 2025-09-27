package com.pres.pres_server.dto.Workspace;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
public class WorkspaceMemberDTO {
    private Long memberNumber;
    private String memberName;
    private String memberProfileUrl;
}