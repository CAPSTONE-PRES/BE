
package com.pres.pres_server.controller;

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
import com.pres.pres_server.dto.QnaListDto;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    public ResponseEntity<Void> deleteFile(@PathVariable("fileId") Long fileId) {
        presentationFileService.deleteFile(fileId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "파일에서 텍스트 추출", description = "업로드된 파일 ID로 텍스트를 추출하고 DB에 저장합니다.")
    @PostMapping("/extract-text/{fileId}")
    public ResponseEntity<ExtractedTextDto> extractTextFromFile(@PathVariable("fileId") Long fileId) {
        // 텍스트 추출 및 DB 저장 (fileId만 사용)
        ExtractedTextDto extractedText = extractTextService.extractTextAndSave(fileId);
        return ResponseEntity.ok(extractedText);
    }

    // 추출한 텍스트를 기반으로 큐카드 생성
    @Operation(summary = "큐카드 생성", description = "파일 ID로 추출된 텍스트를 기반으로 큐카드를 생성합니다.")
    @PostMapping("/generate-cue/{fileId}")
    public ResponseEntity<CueCardDto> generateCue(@PathVariable("fileId") Long fileId) {
        // 큐카드 생성을 담당하는 서비스 호출 (fileId만 전달)
        CueCardDto cueCard = generateCueService.generateCueCards(fileId);
        return ResponseEntity.ok(cueCard);
    }

    // 생성된 큐카드 조회
    @Operation(summary = "큐카드 조회", description = "파일 ID로 저장된 큐카드를 조회합니다.")
    @GetMapping("/cue-cards/{fileId}")
    public ResponseEntity<CueCardDto> getCueCards(@PathVariable("fileId") Long fileId) {
        CueCardDto cueCard = generateCueService.getCueCardsByFileId(fileId);
        return ResponseEntity.ok(cueCard);
    }

    // 추출한 텍스트를 기반으로 예상 질문 및 적절한 답변 생성하고 DB에 저장
    @Operation(summary = "Q&A 생성 및 DB 저장", description = "파일 ID로 추출된 텍스트를 기반으로 예상 질문과 답변을 생성하고 DB에 저장합니다.")
    @PostMapping("/generate-and-save-qna/{fileId}")
    public ResponseEntity<Map<String, Object>> generateAndSaveQnA(@PathVariable("fileId") Long fileId) {
        // 1. 파일 ID로 추출된 전체 텍스트 조회
        String fullText = extractTextService.getFullTextByFileId(fileId);

        // 2. Q&A 생성 및 저장 (fileId 기반) - 예외는 GlobalExceptionHandler가 처리
        var savedQuestions = generateQnaService.generateAndSaveQna(fullText, fileId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", String.format("Q&A가 성공적으로 생성되고 저장되었습니다. (질문 %d개 생성)", savedQuestions.size()));
        response.put("questionCount", savedQuestions.size());

        // 3. 생성된 Q&A 내용도 함께 반환
        var qnaListDto = generateQnaService.getSavedQnaAsDto(fileId);
        response.put("generatedQnA", qnaListDto);

        return ResponseEntity.ok(response);
    }

    // Q&A 미리보기 (DB 저장 없음)
    @Operation(summary = "Q&A 미리보기", description = "파일 ID로 추출된 텍스트를 기반으로 Q&A를 생성하지만 DB에 저장하지 않고 미리보기만 제공합니다.")
    @PostMapping("/preview-qna/{fileId}")
    public ResponseEntity<Map<String, Object>> previewQnA(@PathVariable("fileId") Long fileId) {
        // 1. 파일 ID로 추출된 전체 텍스트 조회
        String fullText = extractTextService.getFullTextByFileId(fileId);

        // 2. Q&A 미리보기 생성 (DB 저장 없음)
        Map<String, String> qnaContent = generateQnaService.generateQna(fullText);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Q&A 미리보기가 생성되었습니다.");
        response.put("preview", qnaContent);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/qna/{fileId}")
    public ResponseEntity<QnaListDto> getSavedQnA(@PathVariable("fileId") Long fileId) {
        QnaListDto qnaListDto = generateQnaService.getSavedQnaAsDto(fileId);
        return ResponseEntity.ok(qnaListDto);
    }

    // Q&A 재생성 (사용자가 기존 Q&A가 마음에 안 들 때)
    @Operation(summary = "Q&A 재생성", description = "기존 Q&A를 삭제하고 새로운 Q&A를 생성하여 저장합니다.")
    @PostMapping("/regenerate-qna/{fileId}")
    public ResponseEntity<Map<String, Object>> regenerateQnA(@PathVariable("fileId") Long fileId) {
        // 1. 파일 ID로 추출된 전체 텍스트 조회
        String fullText = extractTextService.getFullTextByFileId(fileId);

        // 2. Q&A 재생성 (기존 삭제 후 새로 생성) - 예외는 GlobalExceptionHandler가 처리
        var regeneratedQuestions = generateQnaService.regenerateQna(fullText, fileId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", String.format("Q&A가 성공적으로 재생성되었습니다. (질문 %d개 생성)", regeneratedQuestions.size()));
        response.put("questionCount", regeneratedQuestions.size());

        // 3. 재생성된 Q&A 내용도 함께 반환
        var qnaListDto = generateQnaService.getSavedQnaAsDto(fileId);
        response.put("regeneratedQnA", qnaListDto);

        return ResponseEntity.ok(response);
    }
}