package com.pres.pres_server.service.file;

import org.apache.poi.xslf.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import com.pres.pres_server.dto.file.SlideContentInfo;
import com.pres.pres_server.dto.file.PdfPageInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class TextValidationService {

    private static final int MIN_TEXT_LENGTH = 30;

    /**
     * PPTX 파일의 슬라이드별 텍스트 품질 검증
     */
    public List<SlideContentInfo> validateSlideContent(XMLSlideShow ppt) throws IOException {
        List<SlideContentInfo> slideInfoList = new ArrayList<>();
        List<XSLFSlide> slides = ppt.getSlides();

        for (int i = 0; i < slides.size(); i++) {
            XSLFSlide slide = slides.get(i);
            SlideContentInfo slideInfo = checkSlideTextQuality(slide, i + 1);
            slideInfoList.add(slideInfo);
        }

        return slideInfoList;
    }

    /**
     * PDF 파일의 페이지별 텍스트 품질 검증
     */
    public List<PdfPageInfo> validatePdfContent(PDDocument document) throws IOException {
        List<PdfPageInfo> pageInfoList = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper();
        int totalPages = document.getNumberOfPages();

        for (int i = 1; i <= totalPages; i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String pageText = stripper.getText(document).trim();

            PdfPageInfo pageInfo = new PdfPageInfo();
            pageInfo.setPageNumber(i);
            pageInfo.setTextLength(pageText.length());
            pageInfo.setHasContent(pageText.length() >= MIN_TEXT_LENGTH);
            pageInfo.setExtractedText(pageText);

            pageInfoList.add(pageInfo);
        }

        return pageInfoList;
    }

    /**
     * 개별 슬라이드의 텍스트 품질 검사
     */
    private SlideContentInfo checkSlideTextQuality(XSLFSlide slide, int slideNumber) {
        SlideContentInfo info = new SlideContentInfo();
        info.setSlideNumber(slideNumber);

        StringBuilder slideText = new StringBuilder();
        int textShapeCount = 0;
        int imageCount = 0;
        int tableCount = 0;
        int chartCount = 0;

        // 슬라이드의 모든 shape 분석
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String text = textShape.getText();
                if (text != null && !text.trim().isEmpty()) {
                    slideText.append(text.trim()).append(" ");
                    textShapeCount++;
                }
            } else if (shape instanceof XSLFPictureShape) {
                imageCount++;
            } else if (shape instanceof XSLFTable) {
                XSLFTable table = (XSLFTable) shape;
                String tableText = extractTableText(table);
                if (!tableText.isEmpty()) {
                    slideText.append(tableText).append(" ");
                    tableCount++;
                }
            } else if (shape instanceof XSLFGraphicFrame) {
                // 차트나 기타 그래픽 요소
                chartCount++;
            }
        }

        String finalText = slideText.toString().trim();
        info.setExtractedText(finalText);
        info.setTextLength(finalText.length());
        info.setTextShapeCount(textShapeCount);
        info.setImageCount(imageCount);
        info.setTableCount(tableCount);
        info.setChartCount(chartCount);

        // 텍스트 품질 판단
        info.setOcrFailed(isOcrFailed(finalText));
        info.setImageDominant(isImageDominant(textShapeCount, imageCount, chartCount));
        info.setInsufficientText(isInsufficientText(finalText));
        info.setHasContent(hasValidContent(finalText, textShapeCount, imageCount, chartCount));

        return info;
    }

    /**
     * 테이블에서 텍스트 추출
     */
    private String extractTableText(XSLFTable table) {
        StringBuilder tableText = new StringBuilder();
        for (XSLFTableRow row : table.getRows()) {
            for (XSLFTableCell cell : row.getCells()) {
                String cellText = cell.getText();
                if (cellText != null && !cellText.trim().isEmpty()) {
                    tableText.append(cellText.trim()).append(" ");
                }
            }
        }
        return tableText.toString().trim();
    }

    /**
     * OCR 실패 검출 - 의미없는 문자나 특수문자만 있는 경우
     */
    private boolean isOcrFailed(String text) {
        if (text.length() < 5)
            return true;

        // 특수문자나 숫자만 있는 경우
        String cleanText = text.replaceAll("[\\s\\p{Punct}\\d]", "");
        return cleanText.length() < text.length() * 0.3;
    }

    /**
     * 이미지 위주 슬라이드 검출
     */
    private boolean isImageDominant(int textShapeCount, int imageCount, int chartCount) {
        int visualElements = imageCount + chartCount;
        return visualElements > 0 && textShapeCount <= 1 && visualElements >= 2;
    }

    /**
     * 텍스트 부족 검출
     */
    private boolean isInsufficientText(String text) {
        return text.length() < MIN_TEXT_LENGTH;
    }

    /**
     * 유효한 컨텐츠 여부 종합 판단
     */
    private boolean hasValidContent(String text, int textShapeCount, int imageCount, int chartCount) {
        // OCR 실패한 경우
        if (isOcrFailed(text)) {
            return false;
        }

        // 텍스트가 충분한 경우
        if (text.length() >= MIN_TEXT_LENGTH) {
            return true;
        }

        // 텍스트는 적지만 구조적 요소가 있는 경우 (테이블, 차트 등)
        if (textShapeCount > 1 || chartCount > 0) {
            return text.length() >= 10; // 최소한의 텍스트만 있어도 OK
        }

        return false;
    }

    /**
     * PPTX 전체 파일의 텍스트 충분성 검증
     */
    public boolean isPptxTextSufficient(List<SlideContentInfo> slideInfoList) {
        if (slideInfoList.isEmpty())
            return false;

        long validSlides = slideInfoList.stream()
                .mapToLong(slide -> slide.isHasContent() ? 1 : 0)
                .sum();

        // 전체 슬라이드의 60% 이상이 유효한 컨텐츠를 가져야 함
        return (double) validSlides / slideInfoList.size() >= 0.6;
    }

    /**
     * PDF 전체 파일의 텍스트 충분성 검증
     */
    public boolean isPdfTextSufficient(List<PdfPageInfo> pageInfoList) {
        if (pageInfoList.isEmpty())
            return false;

        long validPages = pageInfoList.stream()
                .mapToLong(page -> page.isHasContent() ? 1 : 0)
                .sum();

        // 전체 페이지의 70% 이상이 유효한 컨텐츠를 가져야 함
        return (double) validPages / pageInfoList.size() >= 0.7;
    }
}