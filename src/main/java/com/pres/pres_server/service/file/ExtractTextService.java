package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.ExtractedText;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.ExtractedTextRepository;
import com.pres.pres_server.dto.file.SlideContentInfo;
import com.pres.pres_server.dto.file.ExtractedTextDto;
import com.pres.pres_server.dto.file.PdfPageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractTextService {

    private final PresentationFileRepository presentationFileRepository;
    private final ExtractedTextRepository extractedTextRepository;
    private final TextValidationService textValidationService;

    // 현재 처리 중인 파일 정보를 저장하는 필드들
    private List<SlideContentInfo> currentSlideInfos;
    private List<PdfPageInfo> currentPdfPageInfos;

    @Value("${ocr.tesseract.cmd:tesseract}")
    private String tesseractCmd;

    @Value("${ocr.tesseract.lang:kor+eng}")
    private String tesseractLang;

    // fileId로 텍스트 추출 및 DB 저장 (권장)
    public ExtractedTextDto extractTextAndSave(Long fileId) {

        PresentationFile presentationFile = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));

        String filePath = presentationFile.getFilePath();
        File file = new File(filePath);

        if (!file.exists()) {
            throw new IllegalArgumentException("파일이 존재하지 않습니다: " + filePath);
        }

        String fileName = presentationFile.getOriginalName();
        String fullText = "";
        List<String> slideTexts = new ArrayList<>();

        try {
            // 파일 확장자에 따른 처리
            if (fileName.toLowerCase().endsWith(".pdf")) {
                ExtractedTextDto result = extractPdfTextByPage(file);
                fullText = result.getFullText();
                slideTexts = result.getSlideTexts();
            } else if (fileName.toLowerCase().endsWith(".pptx")) {
                ExtractedTextDto result = extractPptTextBySlide(file);
                fullText = result.getFullText();
                slideTexts = result.getSlideTexts();
                currentPdfPageInfos = null; // PPTX는 PDF 정보 없음
            } else {
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
            }

            // **Upsert 로직: 기존 데이터가 있으면 업데이트, 없으면 새로 생성**
            ExtractedText extractedText = extractedTextRepository
                    .findByPresentationFileFileId(fileId)
                    .orElse(new ExtractedText(presentationFile, fullText, slideTexts));

            // 기존 엔티티인 경우 내용 업데이트
            extractedText.setFullText(fullText);
            extractedText.setSlideTexts(slideTexts);
            extractedTextRepository.save(extractedText);

            // 텍스트 부족한 슬라이드 검증
            ExtractedTextDto result = validateSlideContent(new ExtractedTextDto(fullText, slideTexts));

            // 검증 결과를 ExtractedText에 업데이트
            extractedText
                    .setIsSufficient(
                            result.getInsufficientSlides() == null || result.getInsufficientSlides().isEmpty());
            extractedText.setInsufficientSlides(
                    result.getInsufficientSlides() != null ? result.getInsufficientSlides().toString() : null);
            extractedText.setInsufficientMessage(result.getInsufficientMessage());
            extractedTextRepository.save(extractedText);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("텍스트 추출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    // 기존 파일 ID로 슬라이드 텍스트 조회
    public List<String> getSlideTextsByFileId(Long fileId) {
        ExtractedText extractedText = extractedTextRepository.findByPresentationFileFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("추출된 텍스트를 찾을 수 없습니다: " + fileId));
        return extractedText.getSlideTexts();
    }

    // PDF 페이지별 텍스트 추출 및 전체 텍스트 반환
    private ExtractedTextDto extractPdfTextByPage(File file) throws IOException {
        List<String> slideTexts = new ArrayList<>();
        StringBuilder fullTextBuilder = new StringBuilder();

        try (PDDocument document = PDDocument.load(file)) {
            // PDFRenderer 준비
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            // TextValidationService를 사용한 PDF 검증
            List<PdfPageInfo> pdfPageInfos = textValidationService.validatePdfContent(document);
            currentPdfPageInfos = pdfPageInfos; // 전역 변수에 저장

            // 텍스트 추출
            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = document.getNumberOfPages();
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);

                String extracted = pageText == null ? "" : pageText.trim();

                // 텍스트가 거의 없으면 OCR 폴백 시도
                if (extracted.length() < 10) {
                    try {
                        String ocr = tryOcrOnPdfPage(renderer, i - 1);
                        if (ocr != null && !ocr.isBlank()) {
                            log.info("PDF OCR 성공: page={} chars={}", i, ocr.length());
                            extracted = extracted + "\n" + ocr;
                        } else {
                            log.debug("PDF OCR 결과 없음: page={}", i);
                        }
                    } catch (Exception e) {
                        log.warn("PDF OCR 실패: page={}, reason={}", i, e.getMessage());
                    }
                }

                slideTexts.add(extracted);
                fullTextBuilder.append("[페이지 ").append(i).append("]\n").append(extracted).append("\n\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("PDF 텍스트 추출 실패: " + e.getMessage());
        }

        return new ExtractedTextDto(fullTextBuilder.toString(), slideTexts);
    }

    /**
     * PDFRenderer로 페이지 이미지를 렌더링하고 Tesseract로 OCR 수행
     * pageIndex는 0-based
     */
    private String tryOcrOnPdfPage(org.apache.pdfbox.rendering.PDFRenderer renderer, int pageIndex) throws Exception {
        // 렌더 해상도
        final int dpi = 150; // 필요시 상향 조정
        java.awt.image.BufferedImage bim = renderer.renderImageWithDPI(pageIndex, dpi);

        File tmp = File.createTempFile("pdf_page_ocr_", ".png");
        try {
            javax.imageio.ImageIO.write(bim, "png", tmp);

            List<String> cmd = new ArrayList<>();
            cmd.add(tesseractCmd == null || tesseractCmd.isBlank() ? "tesseract" : tesseractCmd);
            cmd.add(tmp.getAbsolutePath());
            cmd.add("stdout");
            if (tesseractLang != null && !tesseractLang.isBlank()) {
                cmd.add("-l");
                cmd.add(tesseractLang);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (java.io.InputStream is = p.getInputStream();
                    java.io.InputStreamReader isr = new java.io.InputStreamReader(is);
                    java.io.BufferedReader br = new java.io.BufferedReader(isr)) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append('\n');
                }
                int exit = p.waitFor();
                if (exit == 0) {
                    return out.toString().trim();
                } else {
                    log.warn("Tesseract 비정상 종료 (PDF): exit={} cmd={}", exit, cmd);
                    return null;
                }
            }
        } finally {
            try {
                tmp.delete();
            } catch (Exception ignore) {
            }
            if (bim != null) {
                bim.flush();
            }
        }
    }

    // PPT 슬라이드별 텍스트 추출 및 전체 텍스트 반환
    private ExtractedTextDto extractPptTextBySlide(File file) throws IOException {
        List<String> slideTexts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file);
                XMLSlideShow ppt = new XMLSlideShow(fis)) {

            // TextValidationService를 사용한 슬라이드 검증
            List<SlideContentInfo> slideContentInfos = textValidationService.validateSlideContent(ppt);
            currentSlideInfos = slideContentInfos; // 전역 변수에 저장

            int page = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                StringBuilder slideSb = new StringBuilder();
                slideSb.append("[페이지 ").append(page).append("]\n");

                boolean hasText = false;
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        String slideText = ((XSLFTextShape) shape).getText();
                        if (slideText != null && !slideText.isBlank()) {
                            slideSb.append(slideText).append("\n");
                            hasText = true;
                        }
                    }
                }

                // 폰트가 깨져 텍스트가 추출되지 않거나 이미지 중심의 슬라이드일 경우 OCR 폴백 시도
                if (!hasText) {
                    try {
                        String ocr = tryOcrOnSlideImage(ppt, slide);
                        if (ocr != null && !ocr.isBlank()) {
                            slideSb.append(ocr).append("\n");
                            log.info("OCR 성공: 페이지 {} - 추출 문자수={} ", page, ocr.length());
                        } else {
                            log.debug("OCR 결과 없음: 페이지 {}", page);
                        }
                    } catch (Exception e) {
                        log.warn("OCR 시도 중 예외 발생: page={}, reason={}", page, e.getMessage());
                    }
                }

                slideTexts.add(slideSb.toString().trim());
                sb.append(slideSb).append("\n");
                page++;
            }
        } catch (IOException e) {
            throw new RuntimeException("PPTX 텍스트 추출 실패: " + e.getMessage(), e);
        }

        return new ExtractedTextDto(sb.toString(), slideTexts);
    }

    /**
     * 해당 슬라이드를 이미징하여 Tesseract CLI로 OCR을 시도합니다.
     * - tesseractCmd (기본 'tesseract')가 PATH에 있어야 합니다.
     * - tesseractLang 설정으로 언어 지정 (예: 'kor+eng')
     */
    private String tryOcrOnSlideImage(XMLSlideShow ppt, XSLFSlide slide) throws Exception {
        // 페이지 크기
        java.awt.Dimension pg = ppt.getPageSize();
        final double scale = 2.0; // DPI 보정 (72 -> 144)
        final int w = (int) Math.round(pg.getWidth() * scale);
        final int h = (int) Math.round(pg.getHeight() * scale);

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, w, h);
            g2.scale(scale, scale);
            slide.draw(g2);
        } finally {
            g2.dispose();
        }

        // 임시 파일로 저장
        File tmp = File.createTempFile("slide_ocr_", ".png");
        try {
            javax.imageio.ImageIO.write(img, "png", tmp);

            // tesseract 실행: tesseract tmp.png stdout -l kor+eng
            List<String> cmd = new ArrayList<>();
            cmd.add(tesseractCmd == null || tesseractCmd.isBlank() ? "tesseract" : tesseractCmd);
            cmd.add(tmp.getAbsolutePath());
            cmd.add("stdout");
            if (tesseractLang != null && !tesseractLang.isBlank()) {
                cmd.add("-l");
                cmd.add(tesseractLang);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (java.io.InputStream is = p.getInputStream();
                    java.io.InputStreamReader isr = new java.io.InputStreamReader(is);
                    java.io.BufferedReader br = new java.io.BufferedReader(isr)) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append('\n');
                }
                int exit = p.waitFor();
                if (exit == 0) {
                    return out.toString().trim();
                } else {
                    log.warn("Tesseract 비정상 종료: exit={} cmd={}", exit, cmd);
                    return null;
                }
            }
        } finally {
            try {
                tmp.delete();
            } catch (Exception ignore) {
            }
            // 명시적으로 이미지 메모리 해제
            img.flush();
        }
    }

    // 검증 결과를 ExtractedTextDto에 매핑
    private ExtractedTextDto validateSlideContent(ExtractedTextDto extractedText) {
        List<Integer> insufficientSlides = new ArrayList<>();
        StringBuilder messageBuilder = new StringBuilder();

        // 지원하지 않는 파일 형식의 경우 검증 건너뛰기
        if (currentPdfPageInfos == null && currentSlideInfos == null) {
            return extractedText;
        }

        // PDF 파일인 경우
        if (currentPdfPageInfos != null) {
            for (int i = 0; i < currentPdfPageInfos.size(); i++) {
                PdfPageInfo pageInfo = currentPdfPageInfos.get(i);
                if (!pageInfo.isHasContent()) {
                    insufficientSlides.add(i + 1);
                    if (messageBuilder.length() > 0) {
                        messageBuilder.append(", ");
                    }
                    messageBuilder.append("페이지 ").append(i + 1).append(": 텍스트 부족");
                }
            }
        }

        // PPTX 파일인 경우
        if (currentSlideInfos != null) {
            for (int i = 0; i < currentSlideInfos.size(); i++) {
                SlideContentInfo slideInfo = currentSlideInfos.get(i);
                if (!slideInfo.isHasContent()) {
                    insufficientSlides.add(i + 1);
                    if (messageBuilder.length() > 0) {
                        messageBuilder.append(", ");
                    }

                    String reason = "내용 부족";
                    if (slideInfo.isOcrFailed()) {
                        reason = "OCR 실패";
                    } else if (slideInfo.isImageDominant() && slideInfo.getTextLength() < 15) {
                        reason = "이미지 위주 슬라이드";
                    } else if (slideInfo.isInsufficientText()) {
                        reason = "텍스트 부족";
                    }

                    messageBuilder.append("슬라이드 ").append(i + 1).append(": ").append(reason);
                }
            }
        }

        // 검증 결과를 DTO에 설정
        if (!insufficientSlides.isEmpty()) {
            extractedText.setInsufficientSlides(insufficientSlides);
            extractedText.setInsufficientMessage("텍스트가 부족한 페이지가 있습니다. (" + messageBuilder.toString() + ")");
        }

        return extractedText;
    }

    // 현재 PDF 페이지 정보 조회 (디버깅 목적)
    public List<PdfPageInfo> getCurrentPdfPageInfos() {
        return currentPdfPageInfos;
    }

    // 현재 슬라이드 정보 조회 (디버깅 목적)
    public List<SlideContentInfo> getCurrentSlideInfos() {
        return currentSlideInfos;
    }

    // 파일 ID로 ExtractedText 전체 조회
    public ExtractedTextDto getExtractedTextByFileId(Long fileId) {
        ExtractedText extractedText = extractedTextRepository.findByPresentationFileFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("추출된 텍스트를 찾을 수 없습니다: " + fileId));

        ExtractedTextDto dto = new ExtractedTextDto(extractedText.getFullText(), extractedText.getSlideTexts());

        // 검증 결과 정보도 포함
        if (extractedText.getInsufficientSlides() != null) {
            // JSON 문자열을 파싱해서 List<Integer>로 변환 (간단하게 처리)
            String insufficientSlidesStr = extractedText.getInsufficientSlides();
            List<Integer> insufficientSlides = new ArrayList<>();
            // "[1, 3, 5]" 형태의 문자열을 파싱
            if (insufficientSlidesStr.contains(",")) {
                String[] parts = insufficientSlidesStr.replace("[", "").replace("]", "").split(",");
                for (String part : parts) {
                    try {
                        insufficientSlides.add(Integer.parseInt(part.trim()));
                    } catch (NumberFormatException e) {
                        // 무시
                    }
                }
            }
            dto.setInsufficientSlides(insufficientSlides);
            dto.setInsufficientMessage(extractedText.getInsufficientMessage());
        }

        return dto;
    }

    // 파일 ID로 전체 텍스트만 조회
    public String getFullTextByFileId(Long fileId) {
        ExtractedText extractedText = extractedTextRepository.findByPresentationFileFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("추출된 텍스트를 찾을 수 없습니다: " + fileId));
        return extractedText.getFullText();
    }

    /**
     * 사용자가 특정 슬라이드의 텍스트를 덮어쓰기(수정) 합니다.
     * 수정 후에는 ExtractedText의 fullText 및 insufficientSlides 등을 재계산하여 저장하고 결과 DTO를
     * 반환합니다.
     */
    public ExtractedTextDto updateSlideText(Long fileId, Integer pageNumber, String newText) {
        ExtractedText extractedText = extractedTextRepository.findByPresentationFileFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("추출된 텍스트를 찾을 수 없습니다: " + fileId));

        List<String> slideTexts = extractedText.getSlideTexts();
        if (pageNumber == null || pageNumber < 1 || pageNumber > slideTexts.size()) {
            throw new IllegalArgumentException("잘못된 페이지 번호입니다: " + pageNumber);
        }

        // 덮어쓰기
        slideTexts.set(pageNumber - 1, newText == null ? "" : newText);
        extractedText.setSlideTexts(slideTexts);

        // fullText 재조합
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slideTexts.size(); i++) {
            sb.append("[페이지 ").append(i + 1).append("]\n");
            sb.append(slideTexts.get(i) == null ? "" : slideTexts.get(i)).append("\n\n");
        }
        extractedText.setFullText(sb.toString());

        // 간단한 재검증: 텍스트 길이 기준(MIN_TEXT_LENGTH = 30) 사용
        List<Integer> insufficientSlides = new ArrayList<>();
        for (int i = 0; i < slideTexts.size(); i++) {
            String t = slideTexts.get(i) == null ? "" : slideTexts.get(i).trim();
            if (t.length() < 30) {
                insufficientSlides.add(i + 1);
            }
        }

        if (insufficientSlides.isEmpty()) {
            extractedText.setIsSufficient(true);
            extractedText.setInsufficientSlides(null);
            extractedText.setInsufficientMessage(null);
        } else {
            extractedText.setIsSufficient(false);
            extractedText.setInsufficientSlides(insufficientSlides.toString());
            StringBuilder msg = new StringBuilder();
            for (int idx = 0; idx < insufficientSlides.size(); idx++) {
                if (idx > 0)
                    msg.append(", ");
                msg.append("슬라이드 ").append(insufficientSlides.get(idx)).append(": 텍스트 부족");
            }
            extractedText.setInsufficientMessage("텍스트가 부족한 페이지가 있습니다. (" + msg.toString() + ")");
        }

        extractedTextRepository.save(extractedText);

        // 반환 DTO 생성
        ExtractedTextDto dto = new ExtractedTextDto(extractedText.getFullText(), extractedText.getSlideTexts());
        if (extractedText.getInsufficientSlides() != null) {
            // 간단 파싱
            String insufficientSlidesStr = extractedText.getInsufficientSlides();
            List<Integer> list = new ArrayList<>();
            if (insufficientSlidesStr.contains(",")) {
                String[] parts = insufficientSlidesStr.replace("[", "").replace("]", "").split(",");
                for (String part : parts) {
                    try {
                        list.add(Integer.parseInt(part.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            dto.setInsufficientSlides(list);
            dto.setInsufficientMessage(extractedText.getInsufficientMessage());
        }

        return dto;
    }
}