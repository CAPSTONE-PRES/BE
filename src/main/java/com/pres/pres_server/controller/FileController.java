package com.pres.pres_server.controller;

import com.pres.pres_server.dto.file.*;
import com.pres.pres_server.dto.qna.QnaGenerateResponseDto;
import com.pres.pres_server.dto.qna.QnaListDto;
import com.pres.pres_server.service.file.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "File Controller", description = "파일 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {
    private final PresentationFileService presentationFileService;
    private final ExtractTextService extractTextService;
    private final GenerateCueService generateCueService;
    private final GenerateQnaService generateQnaService;
    private final CueSlideService cueSlideService;
    private final PresentationImageService presentationImageService;

    // TODO: file upload 로직과 image 변환 로직 API 분리?
    @Operation(summary = "presentation file 업로드", description = "presentation file을 업로드하고, 파일 ID와 URL을 반환합니다.")
    @PostMapping(value = "/upload", consumes = { "multipart/form-data" })
    public ResponseEntity<FileUploadDto> uploadFile(@RequestPart("file") MultipartFile file,
            @RequestParam("uploaderId") Long uploaderId,
            @RequestParam("projectId") Long projectId) {
        // 파일 업로드, 이미지 변환 및 db저장
        log.info("파일 업로드 요청: uploaderId={}, projectId={}, fileName={}",
                uploaderId, projectId, file.getOriginalFilename());
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

    @Operation(summary = "큐카드,Qr 생성 및 저장", description = "파일 ID로 추출된 텍스트를 기반으로 큐카드를 생성합니다.")
    @PostMapping("/generate-cue/{fileId}")
    public ResponseEntity<CueGenerationResponseDto> generateCue(@PathVariable("fileId") Long fileId,
            @RequestParam(name = "maxSections", required = false, defaultValue = "5") int maxSections) {
        // 큐카드 생성을 담당하는 서비스 호출 (fileId만 전달)
        CueGenerationResponseDto cueCard = generateCueService.generateCueCards(fileId, maxSections);
        return ResponseEntity.ok(cueCard);
    }

    @Operation(summary = "큐카드 조회 (QR 미포함)", description = "파일 ID로 저장된 큐카드를 조회합니다.")
    @GetMapping("/cue-cards/{fileId}")
    public ResponseEntity<CueCardDto> getCueCards(@PathVariable("fileId") Long fileId) {
        CueCardDto cueCard = generateCueService.getCueCardsByFileId(fileId);
        return ResponseEntity.ok(cueCard);
    }

    @Operation(summary = "큐카드 조회 (QR 포함)", description = "파일 ID로 QR 정보가 포함된 큐카드를 조회합니다.")
    @GetMapping("/cue-cards-with-qr/{fileId}")
    public ResponseEntity<CueCardDto> getCueCardsWithQr(@PathVariable("fileId") Long fileId) {
        CueCardDto cueCard = generateCueService.getCueCardsWithQrByFileId(fileId);
        return ResponseEntity.ok(cueCard);
    }

    @Operation(summary = "Q&A 생성 및 DB 저장", description = "파일 ID로 추출된 텍스트를 기반으로 예상 질문과 답변을 생성하고 DB에 저장합니다.")
    @PostMapping("/generate-and-save-qna/{fileId}")
    public ResponseEntity<QnaGenerateResponseDto> generateAndSaveQnA(@PathVariable Long fileId) {
        QnaGenerateResponseDto response = generateQnaService.generateAndSaveQnaWithResponse(fileId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Q&A 미리보기", description = "파일 ID로 추출된 텍스트를 기반으로 Q&A를 생성하지만 DB에 저장하지 않고 미리보기만 제공합니다.")
    @PostMapping("/preview-qna/{fileId}")
    public ResponseEntity<Map<String, Object>> previewQnA(@PathVariable Long fileId) {
        Map<String, Object> response = generateQnaService.previewQnaWithResponse(fileId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "DB에 저장된 전체 예상 질문 및 답변 조회", description = "파일 ID로 저장된 Q&A 목록을 조회합니다.")
    @GetMapping("/qna/{fileId}")
    public ResponseEntity<QnaListDto> getSavedQnA(@PathVariable("fileId") Long fileId) {
        QnaListDto qnaListDto = generateQnaService.getSavedQnaAsDto(fileId);
        return ResponseEntity.ok(qnaListDto);
    }

    @Operation(summary = "Q&A 재생성", description = "기존 Q&A를 삭제하고 새로운 Q&A를 생성하여 저장합니다.")
    @PostMapping("/regenerate-qna/{fileId}")
    public ResponseEntity<QnaGenerateResponseDto> regenerateQnA(@PathVariable Long fileId) {
        QnaGenerateResponseDto response = generateQnaService.regenerateQnaWithResponse(fileId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "QR로 슬라이드 대본 조회", description = "QR 코드 스캔 시 해당 슬라이드의 BASIC 대본(섹션별 텍스트)만 반환합니다. " +
            "ADVANCED 대본은 포함되지 않습니다. " +
            "path variable {slug}는 QR 코드에 포함된 고유 식별자(slug)입니다.")
    @GetMapping("/qr/{slug}")
    public ResponseEntity<CueSlideDto> getByQrSlug(@PathVariable("slug") String slug) {
        CueSlideDto response = cueSlideService.getSlideByQr(slug);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "파일의 QR 정보 조회", description = "연습모드에서 사용할 슬라이드별 QR 정보(slug)를 조회합니다.")
    @GetMapping("/qr-info/{fileId}")
    public ResponseEntity<Map<Integer, String>> getQrInfo(@PathVariable("fileId") Long fileId) {
        Map<Integer, String> qrInfo = cueSlideService.getQrInfoByFileId(fileId);
        return ResponseEntity.ok(qrInfo);
    }

    @Operation(summary = "파일의 전체 이미지 조회", description = "슬라이드에 보여줄 전체 파일 이미지 url 리스트를 조회합니다.")
    @GetMapping("/images/{fileId}")
    public ResponseEntity<List<String>> getPresentationImages(@PathVariable Long fileId) {
        List<String> urls = presentationImageService.getImageUrls(fileId);
        return ResponseEntity.ok(urls);
    }

    @Operation(summary = "특정 페이지 이미지 조회", description = "파일 ID와 페이지 번호로 해당 슬라이드 이미지를 조회합니다.")
    @GetMapping("/{fileId}/page/{pageNumber}/image")
    public ResponseEntity<Resource> getSlideImage(
            @PathVariable Long fileId,
            @PathVariable Integer pageNumber) {
        Resource resource = presentationImageService.getSlideImageResource(fileId, pageNumber);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }

    // TODO: 추가 자료 업로드 API 생성 (이미지 생성 x, extract Text만 진행해서 CueCard와 QnA 생성에 도움)

    // TODO: pdf 다운로드 API 생성 (현재 db에 이미지로 저장된 상태, 라이브러리 활용 필요)

}