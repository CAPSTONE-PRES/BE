package com.pres.pres_server.dto.Workspace;

import lombok.*;
import java.util.List;

@Getter
@Setter
public class TeamMemberEditRequest {
    private List<String> emails; // 새로 덮어씌울 멤버 이메일 리스트
}
