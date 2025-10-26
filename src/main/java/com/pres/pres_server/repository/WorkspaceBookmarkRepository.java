package com.pres.pres_server.repository;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.domain.WorkspaceBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceBookmarkRepository extends JpaRepository<WorkspaceBookmark, Long> {
    boolean existsByUserAndWorkspace(User user, WorkSpace workspace);
    void deleteByUserAndWorkspace(User user, WorkSpace workspace);

    // userId 기준으로 즐겨찾기된 워크스페이스 조회
    List<WorkspaceBookmark> findByUserId(Long userId);
}