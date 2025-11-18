package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.dto.file.CueAdvancedDto;
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
    @Transactional
    public void processSlide(PresentationFile file, CueSlideDto dto) {
        int slide = dto.getSlideNumber();
        final Long fileId = file.getFileId();

        // 1) BASIC 업서트 + 섹션 목록 추출
        Set<Integer> activeSections = upsertBasicCards(file, slide, dto.getBasic());

        // 2) ADVANCED 업서트 (BASIC 섹션과 동기화)
        upsertAdvancedCards(file, slide, dto.getAdvanced(), dto.getBasic(), activeSections);

    }

    // BASIC 모드 큐카드를 섹션별로 업서트하고, 활성 섹션 목록을 반환
    private Set<Integer> upsertBasicCards(PresentationFile file, int slide, List<CueBasicDto> basics) {
        Long fileId = file.getFileId();

        // 기존 BASIC 카드 로드
        List<CueCard> existingBasics = cueCardRepository
                .findByPresentationFile_FileIdAndSlideNumberOrderByModeAscSectionNumberAsc(fileId, slide)
                .stream()
                .filter(c -> c.getMode() == CueCard.Mode.BASIC)
                .toList();

        Map<Integer, CueCard> basicBySection = existingBasics.stream()
                .collect(Collectors.toMap(
                        c -> Optional.ofNullable(c.getSectionNumber()).orElse(0),
                        c -> c));

        // 들어온 BASIC 섹션 업서트
        Set<Integer> incomingSections = new HashSet<>();
        if (basics != null) {
            for (CueBasicDto b : basics) {
                if (b == null)
                    continue;
                if (b.getText() == null || b.getText().isBlank())
                    continue;

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

        // 요청에 없는 기존 BASIC 섹션 삭제
        for (CueCard old : existingBasics) {
            Integer sec = Optional.ofNullable(old.getSectionNumber()).orElse(0);
            if (!incomingSections.contains(sec)) {
                cueCardRepository.delete(old);
            }
        }

        return incomingSections;
    }

    private void upsertAdvancedCards(PresentationFile file, int slide,
            List<CueAdvancedDto> advanceds,
            List<CueBasicDto> basics,
            Set<Integer> targetSections) {
        Long fileId = file.getFileId();

        // 기존 ADVANCED 카드 로드
        List<CueCard> existingAdv = cueCardRepository
                .findByPresentationFile_FileIdAndSlideNumberOrderByModeAscSectionNumberAsc(fileId, slide)
                .stream()
                .filter(c -> c.getMode() == CueCard.Mode.ADVANCED)
                .toList();

        Map<Integer, CueCard> advBySection = existingAdv.stream()
                .collect(Collectors.toMap(
                        c -> Optional.ofNullable(c.getSectionNumber()).orElse(0),
                        c -> c));

        // 인입 ADVANCED를 섹션별로 맵 구성
        Map<Integer, String> incomingAdvTextBySec = new HashMap<>();
        if (advanceds != null) {
            for (CueAdvancedDto a : advanceds) {
                if (a == null)
                    continue;
                int sec = a.getSection();
                if (sec <= 0)
                    continue;
                incomingAdvTextBySec.put(sec, Optional.ofNullable(a.getText()).orElse("").trim());
            }
        }

        // BASIC 섹션별로 맵 구성 (폴백용)
        Map<Integer, String> basicTextBySec = new HashMap<>();
        if (basics != null) {
            for (CueBasicDto b : basics) {
                if (b == null)
                    continue;
                int sec = b.getSection();
                if (sec > 0 && b.getText() != null) {
                    basicTextBySec.put(sec, b.getText());
                }
            }
        }

        // BASIC 섹션 기준으로 ADVANCED 업서트
        for (Integer sec : targetSections) {
            String advText = incomingAdvTextBySec.getOrDefault(sec, "");

            // 빈 ADVANCED는 BASIC 첫 문장으로 폴백
            if (advText.isBlank()) {
                String baseText = basicTextBySec.getOrDefault(sec, "");
                advText = firstSentence(baseText);
                if (advText.isBlank()) {
                    advText = "(요약 없음)";
                }
            }

            CueCard adv = advBySection.getOrDefault(sec, new CueCard());
            adv.setPresentationFile(file);
            adv.setSlideNumber(slide);
            adv.setMode(CueCard.Mode.ADVANCED);
            adv.setSectionNumber(sec);
            adv.setSectionKeyword(" "); // BASIC 키워드 참조
            adv.setContent(advText);
            cueCardRepository.save(adv);
        }

        // 요청에 없는 기존 ADVANCED 섹션 삭제
        for (CueCard old : existingAdv) {
            Integer sec = Optional.ofNullable(old.getSectionNumber()).orElse(0);
            if (!targetSections.contains(sec)) {
                cueCardRepository.delete(old);
            }
        }
    }

    // ocr 인식 실패시 사용
    @Transactional
    public void persistInsufficient(PresentationFile file, int slideNum) {
        // BASIC 업서트
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

    /**
     * QR 정보만 반환 (대본 제외)
     */
    public Map<Integer, String> getQrInfoByFileId(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);
        }

        // ADVANCED 모드만 조회 (QR은 ADVANCED에만 있음)
        List<CueCard> qrCards = cueCardRepository
                .findByPresentationFile_FileIdAndModeOrderBySlideNumberAsc(fileId, CueCard.Mode.ADVANCED);

        return qrCards.stream()
                .filter(c -> c.getQrSlug() != null)
                .collect(Collectors.toMap(
                        CueCard::getSlideNumber,
                        CueCard::getQrSlug,
                        (existing, replacement) -> existing, // 중복 시 기존 값 유지
                        TreeMap::new // 슬라이드 번호 순 정렬
                ));
    }

    // qr과 대본을 같이 반환함. "/qr/{slug}"에서 사용
    @Transactional(readOnly = true)
    public CueSlideDto getSlideByQr(String slug) {
        CueCard qrCard = cueCardRepository.findByQrSlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("QR이 유효하지 않습니다: " + slug));

        Long fileId = qrCard.getPresentationFile().getFileId();
        int slide = qrCard.getSlideNumber();

        List<CueCard> cards = cueCardRepository
                .findByPresentationFile_FileIdAndSlideNumberOrderByModeAscSectionNumberAsc(fileId, slide);

        // BASIC map (정렬)
        List<CueBasicDto> basicDtos = cards.stream()
                .filter(c -> c.getMode() == CueCard.Mode.BASIC)
                .map(c -> {
                    CueBasicDto d = new CueBasicDto();
                    d.setCueId(c.getCueId());
                    d.setSection(Optional.ofNullable(c.getSectionNumber()).orElse(0));
                    d.setKeyword(Optional.ofNullable(c.getSectionKeyword()).orElse(""));
                    d.setText(Optional.ofNullable(c.getContent()).orElse(""));
                    return d;
                })
                .sorted(Comparator.comparingInt(CueBasicDto::getSection))
                .toList();

        Map<Integer, CueCard> advBySec = cards.stream()
                .filter(c -> c.getMode() == CueCard.Mode.ADVANCED)
                .collect(Collectors.toMap(
                        c -> Optional.ofNullable(c.getSectionNumber()).orElse(0),
                        c -> c,
                        (a, b) -> a));

        List<CueAdvancedDto> advancedDtos = new ArrayList<>();
        for (CueBasicDto b : basicDtos) {
            int sec = b.getSection();
            CueCard adv = advBySec.get(sec);

            CueAdvancedDto a = new CueAdvancedDto();
            a.setCueId(adv != null ? adv.getCueId() : null);
            a.setSection(sec);
            a.setKeyword(b.getKeyword()); // ✅ BASIC keyword 주입
            a.setText(adv != null ? Optional.ofNullable(adv.getContent()).orElse("") : "");
            advancedDtos.add(a);
        }

        // 이전/다음 슬라이드의 qrSlug 계산
        List<CueCard> advAll = cueCardRepository
                .findByPresentationFile_FileIdAndModeOrderBySlideNumberAsc(fileId, CueCard.Mode.ADVANCED);

        // (slideNumber -> qrSlug) ordered map
        List<java.util.Map.Entry<Integer, String>> slideSlugPairs = advAll.stream()
                .filter(c -> c.getQrSlug() != null && !c.getQrSlug().isBlank())
                .map(c -> Map.entry(c.getSlideNumber(), c.getQrSlug()))
                .distinct()
                .toList();

        String prev = null, next = null;
        for (int i = 0; i < slideSlugPairs.size(); i++) {
            if (slideSlugPairs.get(i).getKey() == slide) {
                if (i > 0)
                    prev = slideSlugPairs.get(i - 1).getValue();
                if (i < slideSlugPairs.size() - 1)
                    next = slideSlugPairs.get(i + 1).getValue();
                break;
            }
        }

        return CueSlideDto.builder()
                .slideNumber(slide)
                .basic(basicDtos)
                .advanced(advancedDtos)
                .qrSlug(qrCard.getQrSlug())
                .build();
    }

    // ===== 헬퍼 메서드 =====

    /**
     * 텍스트의 첫 문장 추출 (마침표 기준, 최대 80자)
     */
    private static String firstSentence(String s) {
        if (s == null)
            return "";
        String x = s.trim();
        int p1 = x.indexOf('.');
        int p2 = x.indexOf('。');
        int end = (p1 >= 0 && p2 >= 0) ? Math.min(p1, p2) : (p1 >= 0 ? p1 : p2);
        if (end >= 0)
            return x.substring(0, end + 1).trim();
        return x.length() > 80 ? x.substring(0, 80) + "…" : x;
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
