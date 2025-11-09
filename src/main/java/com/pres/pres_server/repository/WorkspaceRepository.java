package com.pres.pres_server.repository;

import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.WorkSpace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceRepository extends JpaRepository<WorkSpace, Long> {
    // 제목순 정렬
    List<WorkSpace> findAllByOrderByWorkspaceNameAsc();

    // 워크스페이스 검색 결과
    List<WorkSpace> findByWorkspaceNameContainingIgnoreCase(String keyword);

}