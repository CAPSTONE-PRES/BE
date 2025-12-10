package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.PresentationImage;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.PresentationImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.file.FileInfoDto;
import com.pres.pres_server.dto.file.FileUploadDto;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.UserRepository;
import com.pres.pres_server.repository.ExtractedTextRepository;
import com.pres.pres_server.service.file.ExtractTextService;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;

// 파일 업로드 요청의 전체 비즈니스 로직만 관리
// FileUploadService를 호출하여 파일을 먼저 저장한 후, DB 트랜잭션 내에서 파일의 메타데이터를 저장
// 만약 DB 저장에 실패하면, 이미 저장된 파일을 삭제하는 보상 로직을 실행 -> DB와 파일 시스템의 상태를 일치

// 파일 메타데이터를 DB에 저장하는 서비스
@Service
@RequiredArgsConstructor
@Slf4j
public class PresentationFileService {
    private final UserRepository userRepository;
    private final FileUploadService fileUploadService;
    private final ExtractedTextRepository extractedTextRepository;
    private final ExtractTextService extractTextService;
    private final ProjectRepository projectRepository;
    private final PresentationFileRepository presentationFileRepository;
    private final PresentationImageRepository presentationImageRepository;

    // TODO: 제한 위치 옮기는 것 고려
    @org.springframework.beans.factory.annotation.Value("${file.max-slides:500}")
    private int maxSlidesAllowed;

    @org.springframework.beans.factory.annotation.Value("${file.max-path-length:1024}")
    private int maxPathLength;
    @org.springframework.beans.factory.annotation.Value("${file.max-resource-size:104857600}")
    private long maxResourceSize; // 기본 100MB

    @Transactional
    public FileUploadDto uploadAndSave(MultipartFile file, Long uploaderId, Long projectId) {
        log.info("uploadAndSave start - uploaderId={}, projectId={}, originalName={}, size={}",
                uploaderId, projectId, file == null ? null : file.getOriginalFilename(),
                file == null ? 0 : file.getSize());
        // 1. 파일 시스템에 원본 파일 저장
        FileInfoDto origin = fileUploadService.saveFile(file);

        // 2) 타입 판별
        String origName = origin.getOriginalName();
        String lower = (origName == null ? "" : origName.toLowerCase());
        final boolean isPdf = lower.endsWith(".pdf");
        final int dpi = 150; // 기본값 (웹 미리보기 용도)
        final int maxSlides = 0; // 0 = 전체
        log.info("is pdf:" + isPdf);

        // 3) 원본 → 이미지 변환 (리스트)
        List<FileInfoDto> images = new java.util.ArrayList<>();
        log.info("images size:" + images.size() + "starts convert file to images");
        try {
            if (isPdf) {
                // PDF → 전 페이지 이미지
                log.info("Converting PDF to images - path={}, dpi={}", origin.getFilePath(), dpi);
                images = fileUploadService.createPdfImages(origin.getFilePath(), dpi);
            } else if (lower.endsWith(".pptx")) {
                // PPTX → 전 슬라이드 이미지
                log.info("Converting PPTX to images - path={}, dpi={}", origin.getFilePath(), dpi);
                images = fileUploadService.convertPptxToImage(origin.getFilePath(), dpi, maxSlides);
            } else {
                // 지원 확장자 제한
                fileUploadService.deleteFile(origin.getFilePath());
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. (.pptx 또는 .pdf만 지원)");
            }
            log.info("Image conversion result count={}", images == null ? 0 : images.size());
            if (images == null || images.isEmpty()) {
                // 생성 실패 보상
                fileUploadService.deleteFile(origin.getFilePath());
                throw new RuntimeException("이미지 변환 결과가 비어 있습니다.");
            }

        } catch (RuntimeException e) {
            // 변환 실패 보상
            for (FileInfoDto img : images) {
                try {
                    fileUploadService.deleteFile(img.getFilePath());
                } catch (Exception ignore) {
                }
            }
            try {
                fileUploadService.deleteFile(origin.getFilePath());
            } catch (Exception ignore) {
            }
            log.error("uploadAndSave: image conversion failed - origin={}, error={}", origin.getFilePath(),
                    e.getMessage(), e);
            throw e;
        }
        // PresentationFile을 먼저 DB에 저장해서 PK(fileId)를 확보.
        // 각 페이지(슬라이드)마다 바로 PresentationImage insert.
        // 썸네일은 첫 장만 기억했다가 마지막에 update.
        // 4) DB 저장 (방어 로직: 슬라이드 수 제한, 길이 검증, 배치 이미지 저장)
        // 상한 초과시 변환된 파일과 원본을 정리하고 실패 반환
        if (images.size() > maxSlidesAllowed) {
            for (FileInfoDto img : images) {
                try {
                    fileUploadService.deleteFile(img.getFilePath());
                } catch (Exception ignore) {
                }
            }
            try {
                fileUploadService.deleteFile(origin.getFilePath());
            } catch (Exception ignore) {
            }
            throw new IllegalArgumentException("슬라이드 수가 허용치를 초과했습니다: " + images.size());
        }

        // 경로/URL 길이 검증
        if (origin.getFilePath() != null && origin.getFilePath().length() > maxPathLength) {
            for (FileInfoDto img : images) {
                try {
                    fileUploadService.deleteFile(img.getFilePath());
                } catch (Exception ignore) {
                }
            }
            try {
                fileUploadService.deleteFile(origin.getFilePath());
            } catch (Exception ignore) {
            }
            throw new IllegalArgumentException("파일 경로 길이가 허용치를 초과했습니다.");
        }

        try {
            // PresentationFile을 먼저 저장(이미지 없음)
            PresentationFile entity = new PresentationFile();
            entity.setSaveName(origin.getSaveName());
            entity.setFilePath(origin.getFilePath());
            entity.setFileUrl(origin.getFileUrl());
            entity.setOriginalName(origin.getOriginalName());
            entity.setFileType(origin.getFileType());
            entity.setFileSize(origin.getSize());
            entity.setUploadedAt(origin.getUploadedAt());
            // 메인 발표 파일 업로드: additional = false
            entity.setAdditional(false);

            User uploader = userRepository.findById(uploaderId)
                    .orElseThrow(() -> new IllegalArgumentException("업로더를 찾을 수 없습니다. id=" + uploaderId));
            entity.setUploader(uploader);

            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다. id=" + projectId));
            entity.setProject(project);

            // (주의) 메인 업로드 경로이므로 additional=true 설정 제거

            PresentationFile saved = presentationFileRepository.save(entity);
            log.info("PresentationFile saved - fileId={}, path={}, saveName={}", saved.getFileId(), saved.getFilePath(),
                    saved.getSaveName());

            // 이미지 엔티티를 배치로 저장
            java.util.List<PresentationImage> imageEntities = new java.util.ArrayList<>();
            int pageNo = 1;
            for (FileInfoDto img : images) {
                PresentationImage pi = PresentationImage.builder()
                        .file(saved)
                        .pageNumber(pageNo++)
                        .path(img.getFilePath())
                        .url(img.getFileUrl())
                        .size(img.getSize())
                        .build();
                imageEntities.add(pi);
            }

            // 배치 사이즈로 저장(메모리/트랜잭션 부담 완화)
            final int BATCH = 100;
            for (int i = 0; i < imageEntities.size(); i += BATCH) {
                int end = Math.min(i + BATCH, imageEntities.size());
                presentationImageRepository.saveAll(imageEntities.subList(i, end));
            }

            // 썸네일 업데이트(첫 이미지)
            if (!images.isEmpty()) {
                FileInfoDto thumb = images.get(0);
                saved.setThumbnailPath(thumb.getFilePath());
                saved.setThumbnailUrl(thumb.getFileUrl());
                presentationFileRepository.save(saved);
            }

            return FileUploadDto.builder()
                    .fileId(saved.getFileId())
                    .fileUrl(saved.getFileUrl())
                    .thumbnailUrl(saved.getThumbnailUrl())
                    .build();

        } catch (RuntimeException e) {
            // DB 실패 시 보상: 생성된 파일 삭제
            for (FileInfoDto img : images) {
                try {
                    fileUploadService.deleteFile(img.getFilePath());
                } catch (Exception ignore) {
                }
            }
            try {
                fileUploadService.deleteFile(origin.getFilePath());
            } catch (Exception ignore) {
            }
            throw new RuntimeException("DB 저장 실패, 파일 롤백됨", e);
        }
    }

    /**
     * 추가 자료 업로드: 이미지 변환을 수행하지 않고 원본 파일만 저장하고 텍스트 추출을 수행합니다.
     * 주로 참고자료(예: 텍스트 중심 PDF) 업로드 후 Cue/QnA 생성에 활용할 용도입니다.
     */
    @Transactional
    public FileUploadDto uploadResourceAndExtract(MultipartFile file, Long uploaderId, Long projectId) {
        // 사전 검증: 확장자 및 크기 (저장 전에 검증해서 빠르게 예외 반환)
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("파일 이름을 확인할 수 없습니다.");
        }
        String lower = originalName.toLowerCase();
        if (!(lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".txt"))) {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. 허용: .pdf, .docx, .txt");
        }
        if (file.getSize() > maxResourceSize) {
            throw new IllegalArgumentException(
                    String.format("파일 크기가 허용치를 초과했습니다: %d bytes (최대 %d bytes)", file.getSize(), maxResourceSize));
        }

        // 1. 파일 저장
        FileInfoDto origin = fileUploadService.saveFile(file);

        try {
            // TODO: DB 저장 수정해야하는거 아닌지 확인 (파일 메타데이터 저장)
            PresentationFile entity = new PresentationFile();
            entity.setSaveName(origin.getSaveName());
            entity.setFilePath(origin.getFilePath());
            entity.setFileUrl(origin.getFileUrl());
            entity.setOriginalName(origin.getOriginalName());
            entity.setFileType(origin.getFileType());
            entity.setFileSize(origin.getSize());
            entity.setUploadedAt(origin.getUploadedAt());

            User uploader = userRepository.findById(uploaderId)
                    .orElseThrow(() -> new IllegalArgumentException("업로더를 찾을 수 없습니다. id=" + uploaderId));
            entity.setUploader(uploader);

            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다. id=" + projectId));
            entity.setProject(project);

            // 추가 자료로 저장함을 명시
            entity.setAdditional(true);

            PresentationFile saved = presentationFileRepository.save(entity);

            // 텍스트 추출 수행(이미지 변환 없이 바로 추출)
            extractTextService.extractTextAndSave(saved.getFileId());

            return FileUploadDto.builder()
                    .fileId(saved.getFileId())
                    .fileUrl(saved.getFileUrl())
                    .thumbnailUrl(saved.getThumbnailUrl())
                    .build();
        } catch (RuntimeException e) {
            // 실패 시 저장된 파일 보상
            try {
                fileUploadService.deleteFile(origin.getFilePath());
            } catch (Exception ignore) {
            }
            throw e;
        }
    }

    /**
     * 저장된 슬라이드 이미지를 모아 임시 PDF 파일을 생성하여 반환합니다.
     * 반환값은 생성된 PDF의 파일 시스템 경로입니다.
     */
    public String generatePdfFromImages(Long fileId) {
        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));

        List<PresentationImage> images = presentationImageRepository.findAllByFile_FileIdOrderByPageNumberAsc(fileId);
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("해당 파일에 이미지가 없습니다.");
        }

        // 이미지 경로 목록을 FileUploadService에 위임하여 PDF를 생성한다.
        java.util.List<String> paths = images.stream().map(PresentationImage::getPath).toList();
        return fileUploadService.createPdfFromImages(paths);
    }

    public List<String> getAllSlideImages(Long fileId) {
        if (!presentationFileRepository.existsById(fileId)) {
            throw new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId);
        }

        return presentationImageRepository.findAllByFile_FileIdOrderByPageNumberAsc(fileId)
                .stream()
                .map(PresentationImage::getUrl)
                .toList();
    }

    /**
     * 슬라이드 업서트: 이미지와/또는 텍스트를 업서트합니다.
     * restrictInsufficient=true인 경우, 해당 슬라이드가 ExtractedText.insufficientSlides에
     * 포함되어야만 허용합니다.
     */
    @Transactional
    public com.pres.pres_server.dto.file.ExtractedTextDto upsertSlide(Long fileId, Integer pageNumber,
            MultipartFile image, String text,
            boolean restrictInsufficient) {
        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));

        // 검증: restrictive 모드이면 ExtractedText의 insufficientSlides에 포함되는지 확인
        if (restrictInsufficient) {
            com.pres.pres_server.domain.ExtractedText ext = extractedTextRepository
                    .findByPresentationFileFileId(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("추출된 텍스트가 없습니다. 먼저 텍스트 추출을 수행하세요."));
            String insufficient = ext.getInsufficientSlides();
            boolean allowed = false;
            if (insufficient != null && !insufficient.isBlank()) {
                String s = insufficient.replace("[", "").replace("]", "");
                for (String part : s.split(",")) {
                    try {
                        if (Integer.parseInt(part.trim()) == pageNumber) {
                            allowed = true;
                            break;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (!allowed) {
                throw new IllegalArgumentException("요청한 슬라이드는 insufficient로 표시되어 있지 않습니다: " + pageNumber);
            }
        }

        // 파일 저장은 트랜잭션 외부에서 수행
        com.pres.pres_server.dto.file.FileInfoDto savedImage = null;
        if (image != null && !image.isEmpty()) {
            savedImage = fileUploadService.saveFile(image);
        }

        try {
            // 이미지 업데이트
            if (savedImage != null) {
                java.util.Optional<com.pres.pres_server.domain.PresentationImage> maybeImg = presentationImageRepository
                        .findByFile_FileIdAndPageNumber(fileId, pageNumber);
                if (maybeImg.isPresent()) {
                    PresentationImage img = maybeImg.get();
                    img.setPath(savedImage.getFilePath());
                    img.setUrl(savedImage.getFileUrl());
                    img.setSize(savedImage.getSize());
                    presentationImageRepository.save(img);
                } else {
                    PresentationImage img = PresentationImage.builder()
                            .file(file)
                            .pageNumber(pageNumber)
                            .path(savedImage.getFilePath())
                            .url(savedImage.getFileUrl())
                            .size(savedImage.getSize())
                            .build();
                    file.getImages().add(img);
                    presentationFileRepository.save(file);
                }

                // 썸네일 갱신(첫 페이지)
                if (pageNumber == 1) {
                    file.setThumbnailPath(savedImage.getFilePath());
                    file.setThumbnailUrl(savedImage.getFileUrl());
                    presentationFileRepository.save(file);
                }
            }

            // 텍스트 업데이트 및 재검증
            if (text != null) {
                // ExtractTextService가 내부적으로 ExtractedText를 갱신하여 DTO를 반환
                return extractTextService.updateSlideText(fileId, pageNumber, text);
            } else {
                // 텍스트 변경이 없는 경우, 현재 ExtractedTextDto를 반환
                return extractTextService.getExtractedTextByFileId(fileId);
            }

        } catch (RuntimeException e) {
            // DB 에러 발생 시 업로드된 파일 보상 삭제
            if (savedImage != null) {
                try {
                    fileUploadService.deleteFile(savedImage.getFilePath());
                } catch (Exception ignore) {
                }
            }
            throw e;
        }
    }

    // 파일 삭제
    @Transactional
    public void deleteFile(Long fileId) {
        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 파일을 찾을 수 없습니다. id=" + fileId));
        // 1) 삭제 대상 경로 수집(중복 제거)
        java.util.Set<String> paths = new java.util.HashSet<>();
        if (file.getFilePath() != null)
            paths.add(file.getFilePath());
        if (file.getThumbnailPath() != null)
            paths.add(file.getThumbnailPath());
        if (file.getImages() != null) {
            for (PresentationImage img : file.getImages()) {
                if (img.getPath() != null)
                    paths.add(img.getPath());
            }
        }
        // 2) DB 삭제 (자식은 orphanRemoval=true 로 함께 삭제)
        presentationFileRepository.delete(file);

        // 3) 파일 시스템 정리 (best-effort)
        for (String p : paths) {
            try {
                fileUploadService.deleteFile(p);
            } catch (Exception ignore) {
            }
        }
    }
}