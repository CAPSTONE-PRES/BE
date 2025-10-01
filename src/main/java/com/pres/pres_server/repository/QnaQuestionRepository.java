package com.pres.pres_server.repository;

import com.pres.pres_server.domain.QnaQuestion;
import com.pres.pres_server.domain.PresentationFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QnaQuestionRepository extends JpaRepository<QnaQuestion, Long> {

    // PresentationFile로 질문 찾기
    List<QnaQuestion> findByPresentationFile(PresentationFile presentationFile);

    // fileId로 질문 찾기
    List<QnaQuestion> findByPresentationFile_FileId(Long fileId);

    // 특정 파일의 활성 상태 질문만 찾기
    @Query("SELECT q FROM QnaQuestion q WHERE q.presentationFile.fileId = :fileId AND q.status = 'ACTIVE'")
    List<QnaQuestion> findActiveByFileId(@Param("fileId") Long fileId);

    // AI 생성 질문만 찾기
    List<QnaQuestion> findByOrigin(String origin);

    // 특정 모델로 생성된 질문 찾기
    List<QnaQuestion> findByModel(String model);
}