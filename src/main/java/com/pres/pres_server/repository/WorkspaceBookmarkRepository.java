package com.pres.pres_server.repository;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import com.pres.pres_server.domain.WorkspaceBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceBookmarkRepository extends JpaRepository<WorkspaceBookmark, Long> {
    boolean existsByUserAndWorkspace(User user, WorkSpace workspace);
    void deleteByUserAndWorkspace(User user, WorkSpace workspace);
}