package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.ExtractedText;
import com.pres.pres_server.dto.ExtractedTextDto;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.ExtractedTextRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExtractTextServiceTest {
    @Mock
    private PresentationFileRepository presentationFileRepository;

    @Mock
    private ExtractedTextRepository extractedTextRepository;

    @Mock
    private TextValidationService textValidationService;

    @InjectMocks
    private ExtractTextService extractTextService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("지원하지 않는 파일 형식 테스트")
    void testExtractTextAndSave_unsupported() {
        // given
        String fileName = "test.txt";
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName, "text/plain", "test content".getBytes(StandardCharsets.UTF_8));

        PresentationFile presFile = new PresentationFile();
        ExtractedText savedExtractedText = new ExtractedText();

        when(presentationFileRepository.findById(1L)).thenReturn(Optional.of(presFile));
        when(extractedTextRepository.save(any(ExtractedText.class))).thenReturn(savedExtractedText);

        // when
        ExtractedTextDto result = extractTextService.extractTextAndSave(multipartFile, 1L);

        // then
        assertNotNull(result);
        assertEquals("지원하지 않는 파일 형식입니다.", result.getFullText());
        assertEquals(1, result.getSlideTexts().size());
        verify(extractedTextRepository, times(2)).save(any(ExtractedText.class));
    }

    @Test
    @DisplayName("슬라이드별 텍스트 조회 테스트")
    void testGetSlideTexts() {
        // given
        List<String> slides = Arrays.asList("slide1", "slide2");
        ExtractedText extractedText = new ExtractedText();
        extractedText.setSlideTexts(slides);

        when(extractedTextRepository.findByPresentationFile_FileId(1L))
                .thenReturn(Optional.of(extractedText));

        // when
        List<String> result = extractTextService.getSlideTextsByFileId(1L);

        // then
        assertEquals(slides, result);
        verify(extractedTextRepository).findByPresentationFile_FileId(1L);
    }

    @Test
    @DisplayName("null 파일로 텍스트 추출 시 예외 발생 테스트")
    void testExtractTextAndSave_nullFile() {
        // when & then
        assertThrows(IllegalArgumentException.class, () -> extractTextService.extractTextAndSave(null, 1L));
    }

    @Test
    @DisplayName("존재하지 않는 파일 ID로 텍스트 추출 시 예외 발생 테스트")
    void testExtractTextAndSave_fileNotFound() {
        // given
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());
        when(presentationFileRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThrows(RuntimeException.class, () -> extractTextService.extractTextAndSave(multipartFile, 999L));
    }

    @Test
    @DisplayName("전체 텍스트 조회 테스트")
    void testGetFullTextByFileId() {
        // given
        String fullText = "전체 텍스트 내용";
        ExtractedText extractedText = new ExtractedText();
        extractedText.setFullText(fullText);

        when(extractedTextRepository.findByPresentationFile_FileId(1L))
                .thenReturn(Optional.of(extractedText));

        // when
        String result = extractTextService.getFullTextByFileId(1L);

        // then
        assertEquals(fullText, result);
        verify(extractedTextRepository).findByPresentationFile_FileId(1L);
    }

    @Test
    @DisplayName("파일 이름이 null인 경우 예외 발생 테스트")
    void testExtractTextAndSave_nullFileName() {
        // given
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", null, "application/pdf", "content".getBytes());

        // when & then
        assertThrows(RuntimeException.class, () -> extractTextService.extractTextAndSave(multipartFile, 1L));
    }

    @Test
    @DisplayName("ExtractedText 전체 조회 테스트 - 검증 결과가 없는 경우")
    void testGetExtractedTextByFileId_noValidationResult() {
        // given
        String fullText = "전체 텍스트";
        List<String> slideTexts = Arrays.asList("슬라이드1", "슬라이드2");

        ExtractedText extractedText = new ExtractedText();
        extractedText.setFullText(fullText);
        extractedText.setSlideTexts(slideTexts);
        extractedText.setInsufficientSlides(null);
        extractedText.setInsufficientMessage(null);

        when(extractedTextRepository.findByPresentationFile_FileId(1L))
                .thenReturn(Optional.of(extractedText));

        // when
        ExtractedTextDto result = extractTextService.getExtractedTextByFileId(1L);

        // then
        assertEquals(fullText, result.getFullText());
        assertEquals(slideTexts, result.getSlideTexts());
        assertNull(result.getInsufficientSlides());
        assertNull(result.getInsufficientMessage());
        verify(extractedTextRepository).findByPresentationFile_FileId(1L);
    }

    @Test
    @DisplayName("ExtractedText 전체 조회 테스트 - 검증 결과가 있는 경우")
    void testGetExtractedTextByFileId_withValidationResult() {
        // given
        String fullText = "전체 텍스트";
        List<String> slideTexts = Arrays.asList("슬라이드1", "슬라이드2");
        String insufficientSlidesStr = "[1, 3]";
        String insufficientMessage = "텍스트가 부족한 페이지가 있습니다.";

        ExtractedText extractedText = new ExtractedText();
        extractedText.setFullText(fullText);
        extractedText.setSlideTexts(slideTexts);
        extractedText.setInsufficientSlides(insufficientSlidesStr);
        extractedText.setInsufficientMessage(insufficientMessage);

        when(extractedTextRepository.findByPresentationFile_FileId(1L))
                .thenReturn(Optional.of(extractedText));

        // when
        ExtractedTextDto result = extractTextService.getExtractedTextByFileId(1L);

        // then
        assertEquals(fullText, result.getFullText());
        assertEquals(slideTexts, result.getSlideTexts());
        assertEquals(Arrays.asList(1, 3), result.getInsufficientSlides());
        assertEquals(insufficientMessage, result.getInsufficientMessage());
        verify(extractedTextRepository).findByPresentationFile_FileId(1L);
    }

    @Test
    @DisplayName("ExtractedText 전체 조회 시 파일이 존재하지 않는 경우")
    void testGetExtractedTextByFileId_fileNotFound() {
        // given
        when(extractedTextRepository.findByPresentationFile_FileId(999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(IllegalArgumentException.class, () -> extractTextService.getExtractedTextByFileId(999L));
    }

    @Test
    @DisplayName("존재하지 않는 파일 ID로 전체 텍스트 조회 시 예외 발생 테스트")
    void testGetFullTextByFileId_fileNotFound() {
        // given
        when(extractedTextRepository.findByPresentationFile_FileId(999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(IllegalArgumentException.class, () -> extractTextService.getFullTextByFileId(999L));
    }

    @Test
    @DisplayName("존재하지 않는 파일 ID로 슬라이드 텍스트 조회 시 예외 발생 테스트")
    void testGetSlideTextsByFileId_fileNotFound() {
        // given
        when(extractedTextRepository.findByPresentationFile_FileId(999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(IllegalArgumentException.class, () -> extractTextService.getSlideTextsByFileId(999L));
    }

    @Test
    @DisplayName("슬라이드 텍스트가 null인 경우 테스트")
    void testGetSlideTextsByFileId_nullSlideTexts() {
        // given
        ExtractedText extractedText = new ExtractedText();
        extractedText.setSlideTexts(null);

        when(extractedTextRepository.findByPresentationFile_FileId(1L))
                .thenReturn(Optional.of(extractedText));

        // when
        List<String> result = extractTextService.getSlideTextsByFileId(1L);

        // then
        assertNull(result);
        verify(extractedTextRepository).findByPresentationFile_FileId(1L);
    }

    @Test
    @DisplayName("현재 PDF 페이지 정보 조회 테스트")
    void testGetCurrentPdfPageInfos() {
        // when
        List<com.pres.pres_server.dto.file.PdfPageInfo> result = extractTextService.getCurrentPdfPageInfos();

        // then
        // 초기 상태에서는 null이어야 함
        assertNull(result);
    }

    @Test
    @DisplayName("현재 슬라이드 정보 조회 테스트")
    void testGetCurrentSlideInfos() {
        // when
        List<com.pres.pres_server.dto.file.SlideContentInfo> result = extractTextService.getCurrentSlideInfos();

        // then
        // 초기 상태에서는 null이어야 함
        assertNull(result);
    }

    @Test
    @DisplayName("빈 문자열 insufficientSlides 파싱 테스트")
    void testGetExtractedTextByFileId_emptyInsufficientSlides() {
        // given
        ExtractedText extractedText = new ExtractedText();
        extractedText.setFullText("테스트");
        extractedText.setSlideTexts(Arrays.asList("슬라이드1"));
        extractedText.setInsufficientSlides("[]"); // 빈 배열

        when(extractedTextRepository.findByPresentationFile_FileId(1L))
                .thenReturn(Optional.of(extractedText));

        // when
        ExtractedTextDto result = extractTextService.getExtractedTextByFileId(1L);

        // then
        assertNotNull(result.getInsufficientSlides());
        assertTrue(result.getInsufficientSlides().isEmpty());
    }
}