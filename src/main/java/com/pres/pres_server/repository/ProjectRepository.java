package com.pres.pres_server.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pres.pres_server.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // WorkSpace ID 리스트로 프로젝트 조회 (due_date 오름차순)
    List<Project> findByWorkspaceId_WorkspaceIdInOrderByDueDateAsc(List<Long> workspaceIds);
}
