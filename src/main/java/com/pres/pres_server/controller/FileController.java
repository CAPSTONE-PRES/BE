
package com.pres.pres_server.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;

import com.pres.pres_server.dto.CueCardDto;
import com.pres.pres_server.dto.ExtractedTextDto;
import com.pres.pres_server.dto.FileUploadDto;
import com.pres.pres_server.service.file.ExtractTextService;
import com.pres.pres_server.service.file.GenerateCueService;
import com.pres.pres_server.service.file.GenerateQnaService;
import com.pres.pres_server.service.file.PresentationFileService;
import com.pres.pres_server.dto.QnaDto;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "File Controller", description = "파일 관련 API")
@RestController
@RequestMapping("/api/files")
public class FileController {
    private final PresentationFileService presentationFileService;
    private final ExtractTextService extractTextService;
    private final GenerateCueService generateCueService;
    private final GenerateQnaService generateQnaService;

    public FileController(PresentationFileService presentationFileService,
            ExtractTextService extractTextService,
            GenerateCueService generateCueService,
            GenerateQnaService generateQnaService) {
        this.presentationFileService = presentationFileService;
        this.extractTextService = extractTextService;
        this.generateCueService = generateCueService;
        this.generateQnaService = generateQnaService;
    }

    @Operation(summary = "presentation file 업로드", description = "presentation file을 업로드하고, 파일 ID와 URL을 반환합니다.")
    @PostMapping(value = "/upload", consumes = { "multipart/form-data" })
    public ResponseEntity<FileUploadDto> uploadFile(@RequestPart("file") MultipartFile file,
            @RequestParam("uploaderId") Long uploaderId,
            @RequestParam("projectId") Long projectId) {
        // 파일 업로드 및 db저장
        FileUploadDto result = presentationFileService.uploadAndSave(file, uploaderId, projectId);
        // 성공 메세지와 결과 반환
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "presentation file 삭제", description = "파일 ID로 파일을 삭제합니다.")
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long fileId) {
        presentationFileService.deleteFile(fileId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "파일에서 텍스트 추출", description = "업로드된 파일에서 텍스트를 추출하고 DB에 저장합니다.")
    @PostMapping("/extract-text/{fileId}")
    public ResponseEntity<ExtractedTextDto> extractTextFromFile(@RequestParam("file") MultipartFile file,
            @PathVariable Long fileId) {
        // 텍스트 추출 및 DB 저장
        ExtractedTextDto extractedText = extractTextService.extractTextAndSave(file, fileId);
        return ResponseEntity.ok(extractedText);
    }

    // 추출한 텍스트를 기반으로 큐카드 생성
    @PostMapping("/generate-cue/{fileId}")
    public ResponseEntity<CueCardDto> generateCue(@PathVariable Long fileId) {
        // 큐카드 생성을 담당하는 서비스 호출 (fileId만 전달)
        CueCardDto cueCard = generateCueService.generateCueCards(fileId);
        return ResponseEntity.ok(cueCard);
    }

    // 추출한 텍스트를 기반으로 예상 질문 및 적절한 답변 생성
    @Operation(summary = "Q&A 생성", description = "파일 ID로 추출된 텍스트를 기반으로 예상 질문과 답변을 생성합니다.")
    @PostMapping("/generate-qna/{fileId}")
    public ResponseEntity<QnaDto> generateQnA(@PathVariable Long fileId) {
        // 1. 파일 ID로 추출된 전체 텍스트 조회
        String fullText = extractTextService.getFullTextByFileId(fileId);

        // 2. Q&A 생성 서비스 호출
        QnaDto qnaResult = generateQnaService.generateQna(fullText);
        return ResponseEntity.ok(qnaResult);
    }
}