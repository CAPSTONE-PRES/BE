package com.pres.pres_server.repository;

import com.pres.pres_server.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    // workspace ID 리스트 기준으로 프로젝트 조회, due_date 오름차순
    List<Project> findByWorkspace_WorkspaceIdInOrderByDueDateAsc(List<Long> workspaceIds);
}
