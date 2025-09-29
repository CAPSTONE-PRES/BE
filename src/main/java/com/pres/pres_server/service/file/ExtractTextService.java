package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.ExtractedText;
import com.pres.pres_server.dto.ExtractedTextDto;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.ExtractedTextRepository;
import com.pres.pres_server.dto.file.SlideContentInfo;
import com.pres.pres_server.dto.file.PdfPageInfo;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExtractTextService {

    private final PresentationFileRepository presentationFileRepository;
    private final ExtractedTextRepository extractedTextRepository;
    private final TextValidationService textValidationService;

    // 현재 처리 중인 파일 정보를 저장하는 필드들
    private List<SlideContentInfo> currentSlideInfos;
    private List<PdfPageInfo> currentPdfPageInfos;

    // 텍스트 추출 및 DB 저장
    public ExtractedTextDto extractTextAndSave(MultipartFile file, Long fileId) {
        if (file == null) {
            throw new IllegalArgumentException("파일이 null입니다.");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("파일 이름이 없습니다.");
        }

        String fullText = "";
        List<String> slideTexts = new ArrayList<>();

        try {
            File tempFile = File.createTempFile("upload_", "_" + fileName);
            file.transferTo(tempFile);

            if (fileName.endsWith(".pdf")) {
                ExtractedTextDto result = extractPdfTextByPage(tempFile);
                fullText = result.getFullText();
                slideTexts = result.getSlideTexts();
            } else if (fileName.endsWith(".pptx")) {
                ExtractedTextDto result = extractPptTextBySlide(tempFile);
                fullText = result.getFullText();
                slideTexts = result.getSlideTexts();
                currentPdfPageInfos = null; // PPTX는 PDF 정보 없음
            } else {
                fullText = "지원하지 않는 파일 형식입니다.";
                slideTexts.add(fullText);
                // 지원하지 않는 파일 형식의 경우 검증 정보 초기화
                currentSlideInfos = null;
                currentPdfPageInfos = null;
            }
            tempFile.delete();
        } catch (IOException e) {
            throw new RuntimeException("텍스트 추출 실패: " + e.getMessage(), e);
        }

        // DB에 저장
        PresentationFile presentationFile = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + fileId));

        // ExtractedText 엔티티 생성 및 저장
        ExtractedText extractedText = new ExtractedText(presentationFile, fullText, slideTexts);
        extractedTextRepository.save(extractedText);

        // 텍스트 부족한 슬라이드 검증
        ExtractedTextDto result = validateSlideContent(new ExtractedTextDto(fullText, slideTexts));

        // 검증 결과를 ExtractedText에 업데이트
        extractedText
                .setIsSufficient(result.getInsufficientSlides() == null || result.getInsufficientSlides().isEmpty());
        extractedText.setInsufficientSlides(
                result.getInsufficientSlides() != null ? result.getInsufficientSlides().toString() : null);
        extractedText.setInsufficientMessage(result.getInsufficientMessage());
        extractedTextRepository.save(extractedText);

        return result;
    }

    // 저장된 파일 경로를 사용한 텍스트 추출 및 DB 저장 (테스트용)
    public ExtractedTextDto extractTextAndSaveFromStoredFile(Long fileId) {
        // DB에서 파일 정보 조회
        PresentationFile presentationFile = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + fileId));

        String fileName = presentationFile.getOriginalName();
        String filePath = presentationFile.getFilePath();

        if (fileName == null) {
            throw new IllegalArgumentException("파일 이름이 없습니다.");
        }

        String fullText = "";
        List<String> slideTexts = new ArrayList<>();

        try {
            File storedFile = new File(filePath);
            if (!storedFile.exists()) {
                throw new RuntimeException("저장된 파일을 찾을 수 없습니다: " + filePath);
            }

            if (fileName.endsWith(".pdf")) {
                ExtractedTextDto result = extractPdfTextByPage(storedFile);
                fullText = result.getFullText();
                slideTexts = result.getSlideTexts();
            } else if (fileName.endsWith(".pptx")) {
                ExtractedTextDto result = extractPptTextBySlide(storedFile);
                fullText = result.getFullText();
                slideTexts = result.getSlideTexts();
                currentPdfPageInfos = null; // PPTX는 PDF 정보 없음
            } else {
                fullText = "지원하지 않는 파일 형식입니다.";
                slideTexts.add(fullText);
                // 지원하지 않는 파일 형식의 경우 검증 정보 초기화
                currentSlideInfos = null;
                currentPdfPageInfos = null;
            }
        } catch (Exception e) {
            throw new RuntimeException("텍스트 추출 실패: " + e.getMessage(), e);
        }

        // ExtractedText 엔티티 생성 및 저장
        ExtractedText extractedText = new ExtractedText(presentationFile, fullText, slideTexts);
        extractedTextRepository.save(extractedText);

        // 텍스트 부족한 슬라이드 검증
        ExtractedTextDto result = validateSlideContent(new ExtractedTextDto(fullText, slideTexts));

        // 검증 결과를 ExtractedText에 업데이트
        extractedText
                .setIsSufficient(result.getInsufficientSlides() == null || result.getInsufficientSlides().isEmpty());
        extractedText.setInsufficientSlides(
                result.getInsufficientSlides() != null ? result.getInsufficientSlides().toString() : null);
        extractedText.setInsufficientMessage(result.getInsufficientMessage());
        extractedTextRepository.save(extractedText);

        return result;
    }

    // 기존 파일 ID로 슬라이드 텍스트 조회
    public List<String> getSlideTextsByFileId(Long fileId) {
        ExtractedText extractedText = extractedTextRepository.findByPresentationFile_FileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("추출된 텍스트를 찾을 수 없습니다: " + fileId));
        return extractedText.getSlideTexts();
    }

    // PDF 페이지별 텍스트 추출 및 전체 텍스트 반환
    private ExtractedTextDto extractPdfTextByPage(File file) throws IOException {
        List<String> slideTexts = new ArrayList<>();
        StringBuilder fullTextBuilder = new StringBuilder();

        try (PDDocument document = PDDocument.load(file)) {
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

                slideTexts.add(pageText.trim());
                fullTextBuilder.append("[페이지 ").append(i).append("]\n").append(pageText).append("\n\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("PDF 텍스트 추출 실패: " + e.getMessage());
        }

        return new ExtractedTextDto(fullTextBuilder.toString(), slideTexts);
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

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        String slideText = ((XSLFTextShape) shape).getText();
                        if (slideText != null && !slideText.isBlank()) {
                            slideSb.append(slideText).append("\n");
                        }
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
        ExtractedText extractedText = extractedTextRepository.findByPresentationFile_FileId(fileId)
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
        ExtractedText extractedText = extractedTextRepository.findByPresentationFile_FileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("추출된 텍스트를 찾을 수 없습니다: " + fileId));
        return extractedText.getFullText();
    }
}