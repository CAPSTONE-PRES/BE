package com.pres.pres_server.repository;

import java.util.List;

import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // WorkSpace ID 리스트로 프로젝트 조회 (due_date 오름차순)
    List<Project> findByWorkspaceId_WorkspaceIdInOrderByDueDateAsc(List<Long> workspaceIds);

    List<Project> findAllByOrderByTitleAsc();

    // 워크스페이스 ID로 프로젝트 전체 조회
    List<Project> findByWorkspaceId_WorkspaceId(Long workspaceId);

    List<Project> findByWorkspaceId_WorkspaceIdIn(List<WorkSpace> workspaces);

    // WorkSpace 객체 리스트로 프로젝트 조회
    List<Project> findByWorkspaceIdIn(List<WorkSpace> workspaces);

}
