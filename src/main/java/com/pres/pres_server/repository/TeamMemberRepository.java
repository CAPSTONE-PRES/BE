package com.pres.pres_server.repository;

import com.pres.pres_server.domain.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    // userId 기준으로 팀 멤버 조회
    List<TeamMember> findByUser_Id(Long userId);
}
