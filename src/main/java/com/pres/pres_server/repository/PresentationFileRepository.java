
package com.pres.pres_server.repository;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface PresentationFileRepository extends JpaRepository<PresentationFile, Long> {

    // projectId로 발표 파일 조회
    Optional<PresentationFile> findByProject_ProjectId(Long projectId);

    // 프로젝트에 연결된 발표 파일 조회
    Optional<PresentationFile> findByProject(Project project);

    // 프로젝트에 연결된 발표 파일 조회 (ExtractedText와 slideTexts까지 함께 fetch join)
    @Query("SELECT DISTINCT pf FROM PresentationFile pf " +
            "LEFT JOIN FETCH pf.extractedText et " +
            "LEFT JOIN FETCH et.slideTexts " +
            "WHERE pf.project = :project")
    Optional<PresentationFile> findByProjectWithExtractedText(@Param("project") Project project);

    // 프로젝트의 메인(추가자료가 아닌) 파일 조회 - fetch join으로 extractedText 포함
    @Query("SELECT DISTINCT pf FROM PresentationFile pf " +
            "LEFT JOIN FETCH pf.extractedText et " +
            "LEFT JOIN FETCH et.slideTexts " +
            "WHERE pf.project = :project AND pf.additional = false")
    Optional<PresentationFile> findMainByProjectWithExtractedText(@Param("project") Project project);

    // 모두 조회용 (필요시 서비스에서 선택 로직 사용)
    List<PresentationFile> findAllByProject_ProjectIdAndAdditionalFalseOrderByUploadedAtDesc(Long projectId);

    List<PresentationFile> findAllByProject_ProjectIdAndAdditionalTrueOrderByUploadedAtDesc(Long projectId);

    List<PresentationFile> findAllByProjectAndAdditionalFalse(Project project);
}
