package com.pres.pres_server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pres.pres_server.domain.CueCard;

@Repository
public interface CueCardRepository extends JpaRepository<CueCard, Long> {

    // 특정 파일의 모든 큐카드 조회
    List<CueCard> findByPresentationFile_FileIdOrderBySlideNumber(Long fileId);

    // 특정 파일의 특정 슬라이드 큐카드 조회
    Optional<CueCard> findByPresentationFile_FileIdAndSlideNumber(Long fileId, int slideNumber);

    // 특정 파일의 큐카드 존재 여부 확인
    boolean existsByPresentationFile_FileId(Long fileId);

    // 특정 파일의 모든 큐카드 삭제
    void deleteByPresentationFile_FileId(Long fileId);
}