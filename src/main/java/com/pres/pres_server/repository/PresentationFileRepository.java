package com.pres.pres_server.repository;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PresentationFileRepository extends JpaRepository<PresentationFile, Long> {

    // 프로젝트에 연결된 발표 파일 조회
    Optional<PresentationFile> findByProject(Project project);

    // 프로젝트에 연결된 발표 파일 조회 (ExtractedText와 slideTexts까지 함께 fetch join)
    @Query("SELECT pf FROM PresentationFile pf " +
            "LEFT JOIN FETCH pf.extractedText et " +
            "LEFT JOIN FETCH et.slideTexts " +
            "WHERE pf.project = :project")
    Optional<PresentationFile> findByProjectWithExtractedText(@Param("project") Project project);
}
