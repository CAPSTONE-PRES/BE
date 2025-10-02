package com.pres.pres_server.repository;

import com.pres.pres_server.domain.PresentationFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresentationFileRepository extends JpaRepository<PresentationFile, Long> {
}
