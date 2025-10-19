package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.dto.file.CueBasicDto;
import com.pres.pres_server.dto.file.CueSlideDto;
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

        // 1) ADVANCED 업서트 (항상 1건, sectionNumber = null)
        upsertAdvanced(file, slide, safeAdv(dto.getAdvanced()));

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
        final Long fileId = file.getFileId();

        // 기존 BASIC만 삭제
        List<CueCard> all = cueCardRepository
                .findByPresentationFile_FileIdAndSlideNumberOrderByModeAscSectionNumberAsc(fileId, slideNum);
        for (CueCard c : all) {
            if (c.getMode() == CueCard.Mode.BASIC) {
                cueCardRepository.delete(c);
            }
        }

        // ADVANCED 업서트
        upsertAdvanced(file, slideNum, "(내용 부족 – 요약 생략)");
    }

    private void upsertAdvanced(PresentationFile file, int slide, String advText) {
        CueCard adv = cueCardRepository
                .findFirstByPresentationFile_FileIdAndSlideNumberAndMode(file.getFileId(), slide, CueCard.Mode.ADVANCED)
                .orElseGet(CueCard::new);

        adv.setPresentationFile(file);
        adv.setSlideNumber(slide);
        adv.setMode(CueCard.Mode.ADVANCED);
        // ADVANCED는 sectionNumber를 0으로 고정
        adv.setSectionNumber(0);
        adv.setSectionKeyword(null);
        adv.setContent(advText);
        cueCardRepository.save(adv);
    }

    private static String safeAdv(String s) {
        if (s == null || s.isBlank()) return "(심화버전 없음)";
        return s.trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

}
