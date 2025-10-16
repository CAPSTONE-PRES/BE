package com.pres.pres_server.repository;

import com.pres.pres_server.domain.TeamMember;
import com.pres.pres_server.domain.WorkSpace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    // userId 기준으로 팀 멤버 조회
    List<TeamMember> findByUser_Id(Long userId);

    // 워크스페이스 ID와 유저 ID로 멤버 존재 여부 확인
    boolean existsByWorkspace_WorkspaceIdAndUser_Id(Long workspaceId, Long userId);

    List<TeamMember> findByWorkspace_WorkspaceId(Long workspaceId);

    void deleteByWorkspace(WorkSpace workspace);

}
