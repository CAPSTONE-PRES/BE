package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.dto.file.CueAdvancedDto;
import com.pres.pres_server.dto.file.CueBasicDto;
import com.pres.pres_server.dto.file.CueSlideDto;
import com.pres.pres_server.dto.file.QrInfoDto;
import com.pres.pres_server.repository.CueCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CueSlideService {

    private final CueCardRepository cueCardRepository;

    /**
     * 슬라이드 단위 저장 (업서트):
     * - ADVANCED 1건: 있으면 업데이트, 없으면 생성 (sectionNumber = null)
     * - BASIC 여러 건: 섹션 번호별 업서트, 요청에 없는 기존 섹션은 삭제
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSlide(PresentationFile file, CueSlideDto dto) {
        int slide = dto.getSlideNumber();
        final Long fileId = file.getFileId();

        // 1) ADVANCED 업서트
        upsertAdvanced(file, slide, extractAdvTextOrFallback(dto.getAdvanced()));

        // 2) BASIC 업서트 준비: 기존 BASIC 전부 로드 → 섹션 맵
        List<CueCard> existingBasics = cueCardRepository
                .findByPresentationFile_FileIdAndSlideNumberOrderByModeAscSectionNumberAsc(fileId, slide)
                .stream()
                .filter(c -> c.getMode() == CueCard.Mode.BASIC)
                .toList();

        Map<Integer, CueCard> basicBySection = existingBasics.stream()
                .collect(Collectors.toMap(
                        c -> Optional.ofNullable(c.getSectionNumber()).orElse(0),
                        c -> c
                ));

        // 2-1) 들어온 섹션들 업서트
        Set<Integer> incomingSections = new HashSet<>();
        if (dto.getBasic() != null) {
            for (CueBasicDto b : dto.getBasic()) {
                if (b == null) continue;
                if (b.getText() == null || b.getText().isBlank()) continue;

                int sec = b.getSection();
                if (sec <= 0) {
                    throw new IllegalArgumentException("BASIC section 번호는 1 이상이어야 합니다.");
                }
                incomingSections.add(sec);

                CueCard basic = basicBySection.getOrDefault(sec, new CueCard());
                basic.setPresentationFile(file);
                basic.setSlideNumber(slide);
                basic.setMode(CueCard.Mode.BASIC);
                basic.setSectionNumber(sec);
                basic.setSectionKeyword(blankToNull(b.getKeyword()));
                basic.setContent(b.getText().trim());
                cueCardRepository.save(basic);
            }
        }
        // 2-2) 요청에 없는 기존 BASIC 섹션 삭제(동기화)
        for (CueCard old : existingBasics) {
            Integer sec = Optional.ofNullable(old.getSectionNumber()).orElse(0);
            if (!incomingSections.contains(sec)) {
                cueCardRepository.delete(old);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistInsufficient (PresentationFile file,int slideNum){

        upsertBasic(file, slideNum, " ");

        // ADVANCED 업서트
        upsertAdvanced(file, slideNum, " ");
    }

    private void upsertBasic(PresentationFile file, int slide, String bText) {
        CueCard basic = cueCardRepository
                .findFirstByPresentationFile_FileIdAndSlideNumberAndMode(file.getFileId(), slide,
                        CueCard.Mode.BASIC)
                .orElseGet(CueCard::new);

        basic.setPresentationFile(file);
        basic.setSlideNumber(slide);
        basic.setSectionNumber(1);
        basic.setSectionKeyword(" ");
        basic.setMode(CueCard.Mode.BASIC);
        basic.setContent(bText);
        cueCardRepository.save(basic);

    }

    private void upsertAdvanced(PresentationFile file, int slide, String advText) {
        CueCard adv = cueCardRepository
                .findFirstByPresentationFile_FileIdAndSlideNumberAndMode(file.getFileId(), slide, CueCard.Mode.ADVANCED)
                .orElseGet(CueCard::new);

        adv.setPresentationFile(file);
        adv.setSlideNumber(slide);
        adv.setMode(CueCard.Mode.ADVANCED);
        adv.setSectionNumber(1);
        adv.setSectionKeyword(" ");
        adv.setContent(advText);
        cueCardRepository.save(adv);
    }

    //qr 정보만을 반환함, 대본은 반환하지 않음
    public Map<Integer, QrInfoDto> getQrInfoByFileId(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);
        }

        // ADVANCED 모드만 조회 (QR은 ADVANCED에만 있음)
        List<CueCard> qrCards = cueCardRepository
                .findByPresentationFile_FileIdAndModeOrderBySlideNumberAsc(fileId, CueCard.Mode.ADVANCED);

        return qrCards.stream()
                .filter(c -> c.getQrSlug() != null && c.getQrUrl() != null)
                .collect(Collectors.toMap(
                        CueCard::getSlideNumber,
                        c -> new QrInfoDto(c.getQrSlug(), c.getQrUrl()),
                        (existing, replacement) -> existing,  // 중복 시 기존 값 유지
                        TreeMap::new  // 슬라이드 번호 순 정렬
                ));
    }

    //qr과 대본을 같이 반환함. "/qr/{slug}"에서 사용
    @Transactional(readOnly = true)
    public CueSlideDto getSlideByQr(String slug) {
        CueCard qrCard = cueCardRepository.findByQrSlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("QR이 유효하지 않습니다: " + slug));

        Long fileId = qrCard.getPresentationFile().getFileId();
        int slide = qrCard.getSlideNumber();

        // 같은 슬라이드의 모든 큐카드 조회 (BASIC + ADVANCED)
        List<CueCard> cards = cueCardRepository
                .findByPresentationFile_FileIdAndSlideNumberOrderByModeAscSectionNumberAsc(fileId, slide);

        // BASIC 전용 리스트 변환
        List<CueBasicDto> basicDtos = cards.stream()
                .filter(c -> c.getMode() == CueCard.Mode.BASIC)
                .map(c -> {
                    CueBasicDto dto = new CueBasicDto();
                    dto.setSection(Optional.ofNullable(c.getSectionNumber()).orElse(0));
                    dto.setKeyword(Optional.ofNullable(c.getSectionKeyword()).orElse(""));
                    dto.setText(Optional.ofNullable(c.getContent()).orElse(""));
                    return dto;
                })
                .toList();

        // ADVANCED 텍스트 합치기
        String advancedText = cards.stream()
                .filter(c -> c.getMode() == CueCard.Mode.ADVANCED)
                .map(c -> Optional.ofNullable(c.getContent()).orElse(""))
                .findFirst()
                .orElse("");

        List<CueAdvancedDto> advancedDtos = new ArrayList<>();
        if(!basicDtos.isEmpty()) {
            CueBasicDto first = basicDtos.get(0);
            CueAdvancedDto advDto = new CueAdvancedDto();
            advDto.setSection(first.getSection());
            advDto.setKeyword(first.getKeyword());
            advDto.setText(advancedText);
            advancedDtos.add(advDto);
        } else{
            CueAdvancedDto advDto = new CueAdvancedDto();
            advDto.setSection(1);
            advDto.setKeyword("요약");
            advDto.setText(advancedText);
            advancedDtos.add(advDto);
        }


        return CueSlideDto.builder()
                .slideNumber(slide)
                .basic(basicDtos)
                .advanced(advancedDtos)
                .qrSlug(qrCard.getQrSlug()) //null 가능
                .qrUrl(qrCard.getQrUrl()) //null 가능
                .build();
    }


    private static String extractAdvTextOrFallback(List<CueAdvancedDto> advList) {
        if (advList == null || advList.isEmpty()) {
            return "(심화버전 없음)";
        }
        CueAdvancedDto first = advList.get(0);
        if (first == null) {
            return "(심화버전 없음)";
        }
        String t = first.getText();
        if (t == null || t.isBlank()) {
            return "(심화버전 없음)";
        }
        return t.trim();
    }


    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

}
