package com.pres.pres_server.repository;

import com.pres.pres_server.domain.WorkSpace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceRepository extends JpaRepository<WorkSpace, Long> {
}