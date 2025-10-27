package com.pres.pres_server.repository;

import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.VisitLog;
import com.pres.pres_server.domain.WorkSpace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VisitLogRepository extends JpaRepository<VisitLog, Long> {

    // 중복 데이터 방지를 위한 조회 (upsert용)
    Optional<VisitLog> findByUserAndWorkspaceAndProject(User user, WorkSpace workspace, Project project);

    // 특정 사용자의 방문 로그를 '최근 방문순'으로 정렬해서 전부 가져오기
    List<VisitLog> findByUserOrderByVisitedAtDesc(User user);

    // 특정 사용자가 방문한 프로젝트 기록, 최근 순
    List<VisitLog> findByUserAndProjectIsNotNullOrderByVisitedAtDesc(User user);

    // 특정 사용자 + 특정 워크스페이스 조합의 가장 최근 방문 로그 가져오기
    Optional<VisitLog> findTopByUserAndWorkspaceOrderByVisitedAtDesc(User user, WorkSpace workspace);

    // 프로젝트 단위로도 조회 가능 (선택)
    Optional<VisitLog> findTopByUserAndProjectOrderByVisitedAtDesc(User user, Project project);

    // 워크스페이스 ID 기준으로 최근 방문 순 정렬도 자주 씀
    List<VisitLog> findByUserAndWorkspaceIsNotNullOrderByVisitedAtDesc(User user);

    // 워크스페이스만 방문한 기록 찾기 (project=null 케이스용)
    Optional<VisitLog> findByUserAndWorkspaceAndProjectIsNull(User user, WorkSpace workspace);

    // 특정 워크스페이스 내에서, 특정 유저가 방문한 프로젝트 목록을 최근 방문순으로 조회
    List<VisitLog> findByWorkspaceAndUserOrderByVisitedAtDesc(WorkSpace workspace, User user);

    void deleteByWorkspace(WorkSpace workspace);

}

