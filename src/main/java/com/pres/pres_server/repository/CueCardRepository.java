package com.pres.pres_server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.CueCard.Mode;
import com.pres.pres_server.domain.PresentationFile;

@Repository
public interface CueCardRepository extends JpaRepository<CueCard, Long> {

    // 특정 파일의 특정 슬라이드 큐카드 조회
    @Deprecated
    List<CueCard> findByPresentationFile_FileIdAndSlideNumber(Long fileId, int slideNumber);
    //List<CueCard> findByPresentationFile_FileIdAndSlideNumberOrderByModeAscSectionNumberAsc로 대체

    // 파일 전체 큐카드 조회 (슬라이드→모드→섹션 순)
    List<CueCard> findByPresentationFile_FileIdOrderBySlideNumberAscModeAscSectionNumberAsc(Long fileId);

    // 슬라이드 단위 (프런트 렌더 기본)
    List<CueCard> findByPresentationFile_FileIdAndSlideNumberOrderByModeAscSectionNumberAsc(Long fileId, Integer slideNumber);

    // PresentationFile 객체로 모든 큐카드 조회 (슬라이드 번호 순)
    List<CueCard> findByPresentationFileOrderBySlideNumberAscModeAscSectionNumberAsc(PresentationFile presentationFile);

    // 특정 아이템(업서트용 키): BASIC은 sectionNumber 필요, ADVANCED는 null
    Optional<CueCard> findByPresentationFile_FileIdAndSlideNumberAndModeAndSectionNumber(
            Long fileId, Integer slideNumber, Mode mode, Integer sectionNumber);

    // ADVANCED 단일 조회(섹션 없음)
    Optional<CueCard> findFirstByPresentationFile_FileIdAndSlideNumberAndMode(
            Long fileId, Integer slideNumber, Mode mode);

    // QR로 단건 조회 (딥링크/스캔)
    Optional<CueCard> findByQrSlug(String qrSlug);

    // 존재/카운트
    boolean existsByPresentationFile_FileId(Long fileId);
    long countByPresentationFile_FileIdAndSlideNumberAndMode(Long fileId, Integer slideNumber, Mode mode);

    // 삭제 동기화에서 사용
    void deleteByPresentationFile_FileId(Long fileId);
    void deleteByPresentationFile_FileIdAndSlideNumber(Long fileId, Integer slideNumber);
    void deleteByPresentationFile_FileIdAndSlideNumberAndModeAndSectionNumber(
            Long fileId, Integer slideNumber, Mode mode, Integer sectionNumber);

}