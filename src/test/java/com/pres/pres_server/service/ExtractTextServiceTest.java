package com.pres.pres_server.service;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.dto.ExtractedTextDto;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.service.file.ExtractTextService;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
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

    @InjectMocks
    private ExtractTextService extractTextService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("PDF 파일 텍스트 추출 및 저장 테스트")
    void testExtractTextAndSave_pdf() throws Exception {
        // given
        String fileName = "test.pdf";
        File tempPdf = File.createTempFile("test", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(tempPdf);
        }
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName, "application/pdf", new FileInputStream(tempPdf));
        PresentationFile presFile = new PresentationFile();
        when(presentationFileRepository.findById(any(Long.class))).thenReturn(Optional.of(presFile));
        when(presentationFileRepository.save(any(PresentationFile.class))).thenReturn(presFile);

        // when
        ExtractedTextDto result = extractTextService.extractTextAndSave(multipartFile, 1L);

        // then
        assertNotNull(result);
        assertTrue(result.getFullText().contains("페이지"));
        assertFalse(result.getSlideTexts().isEmpty());
        verify(presentationFileRepository).save(any(PresentationFile.class));
        tempPdf.delete();
    }

    @Test
    @DisplayName("PPTX 파일 텍스트 추출 및 저장 테스트")
    void testExtractTextAndSave_pptx() throws Exception {
        // given
        String fileName = "test.pptx";
        File tempPptx = File.createTempFile("test", ".pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.createSlide();
            try (FileOutputStream out = new FileOutputStream(tempPptx)) {
                ppt.write(out);
            }
        }
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName, "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                new FileInputStream(tempPptx));
        PresentationFile presFile = new PresentationFile();
        when(presentationFileRepository.findById(any(Long.class))).thenReturn(Optional.of(presFile));
        when(presentationFileRepository.save(any(PresentationFile.class))).thenReturn(presFile);

        // when
        ExtractedTextDto result = extractTextService.extractTextAndSave(multipartFile, 1L);

        // then
        assertNotNull(result);
        assertTrue(result.getFullText().contains("페이지"));
        assertFalse(result.getSlideTexts().isEmpty());
        verify(presentationFileRepository).save(any(PresentationFile.class));
        tempPptx.delete();
    }

    @Test
    @DisplayName("지원하지 않는 파일 형식 테스트")
    void testExtractTextAndSave_unsupported() {
        // given
        String fileName = "test.txt";
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName, "text/plain", "test content".getBytes(StandardCharsets.UTF_8));
        PresentationFile presFile = new PresentationFile();
        when(presentationFileRepository.findById(any(Long.class))).thenReturn(Optional.of(presFile));
        when(presentationFileRepository.save(any(PresentationFile.class))).thenReturn(presFile);

        // when
        ExtractedTextDto result = extractTextService.extractTextAndSave(multipartFile, 1L);

        // then
        assertNotNull(result);
        assertEquals("지원하지 않는 파일 형식입니다.", result.getFullText());
        assertEquals(1, result.getSlideTexts().size());
        verify(presentationFileRepository).save(any(PresentationFile.class));
    }

    @Test
    @DisplayName("슬라이드별 텍스트 조회 테스트")
    void testGetSlideTexts() {
        // given
        List<String> slides = Arrays.asList("slide1", "slide2");
        PresentationFile presFile = new PresentationFile();
        presFile.setSlideTexts(slides);
        when(presentationFileRepository.findById(any(Long.class))).thenReturn(Optional.of(presFile));

        // when
        List<String> result = extractTextService.getSlideTextsByFileId(1L);

        // then
        assertEquals(slides, result);
    }
}
