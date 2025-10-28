package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.PresentationImage;
import com.pres.pres_server.repository.PresentationFileRepository;
import com.pres.pres_server.repository.PresentationImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.file.FileInfoDto;
import com.pres.pres_server.dto.file.FileUploadDto;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.UserRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;

// 파일 업로드 요청의 전체 비즈니스 로직만 관리
// FileUploadService를 호출하여 파일을 먼저 저장한 후, DB 트랜잭션 내에서 파일의 메타데이터를 저장
// 만약 DB 저장에 실패하면, 이미 저장된 파일을 삭제하는 보상 로직을 실행 -> DB와 파일 시스템의 상태를 일치

// 파일 메타데이터를 DB에 저장하는 서비스
@Service
@RequiredArgsConstructor
public class PresentationFileService {
    private final UserRepository userRepository;
    private final FileUploadService fileUploadService;
    private final ProjectRepository projectRepository;
    private final PresentationFileRepository presentationFileRepository;
    private final PresentationImageRepository presentationImageRepository;

    @Transactional
    public FileUploadDto uploadAndSave(MultipartFile file, Long uploaderId, Long projectId) {
        // 1. 파일 시스템에 원본 파일 저장
        FileInfoDto origin = fileUploadService.saveFile(file);

        // 2) 타입 판별
        String origName = origin.getOriginalName();
        String lower = (origName == null ? "" : origName.toLowerCase());
        final boolean isPdf = lower.endsWith(".pdf");
        final int dpi = 200;       // 기본값 (웹 미리보기 용도)
        final int maxSlides = 0;   // 0 = 전체


        // 3) 원본 → 이미지 변환 (리스트)
        List<FileInfoDto> images = new java.util.ArrayList<>();
        try {
            if (isPdf) {
                // PDF → 전 페이지 이미지
                images = fileUploadService.createPdfImages(origin.getFilePath(), dpi);
            } else if (lower.endsWith(".pptx")) {
                // PPTX → 전 슬라이드 이미지
                images = fileUploadService.convertPptxToImage(origin.getFilePath(), dpi, maxSlides);
            } else {
                // 지원 확장자 제한
                fileUploadService.deleteFile(origin.getFilePath());
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. (.pptx 또는 .pdf만 지원)");
            }
            if (images.isEmpty()) {
                // 생성 실패 보상
                fileUploadService.deleteFile(origin.getFilePath());
                throw new RuntimeException("이미지 변환 결과가 비어 있습니다.");
            }

        } catch (RuntimeException e) {
            // 변환 실패 보상
            for (FileInfoDto img : images) {
                try { fileUploadService.deleteFile(img.getFilePath()); } catch (Exception ignore) {}
            }
            try { fileUploadService.deleteFile(origin.getFilePath()); } catch (Exception ignore) {}
            throw e;
        }

        // 4) DB 저장
        try {
            PresentationFile entity = new PresentationFile();
            // 원본 파일 메타
            entity.setSaveName(origin.getSaveName());
            entity.setFilePath(origin.getFilePath());
            entity.setFileUrl(origin.getFileUrl());
            entity.setOriginalName(origin.getOriginalName());
            entity.setFileType(origin.getFileType());
            entity.setFileSize(origin.getSize());
            entity.setUploadedAt(origin.getUploadedAt());


            // 썸네일(첫 이미지)
            FileInfoDto thumb = images.get(0);
            entity.setThumbnailPath(thumb.getFilePath());
            entity.setThumbnailUrl(thumb.getFileUrl());

            // 연관 주입
            User uploader = userRepository.findById(uploaderId)
                    .orElseThrow(() -> new IllegalArgumentException("업로더를 찾을 수 없습니다. id=" + uploaderId));
            entity.setUploader(uploader);

            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다. id=" + projectId));
            entity.setProject(project);

            // 모든 생성 이미지 메타를 자식 엔티티로 붙임
            int pageNo = 1;
            for (FileInfoDto img : images) {
                PresentationImage pi = PresentationImage.builder()
                        .file(entity)                 // FK 연결
                        .pageNumber(pageNo++)
                        .path(img.getFilePath())
                        .url(img.getFileUrl())
                        .size(img.getSize())
                        .build();
                entity.getImages().add(pi);           // cascade=ALL 이면 한 번에 insert
            }

            PresentationFile saved = presentationFileRepository.save(entity);

            return FileUploadDto.builder()
                    .fileId(saved.getFileId())
                    .fileUrl(saved.getFileUrl())
                    .thumbnailUrl(saved.getThumbnailUrl())
                    .build();

        } catch (RuntimeException e) {
            // DB 실패 시, 생성 파일 보상 삭제
            for (FileInfoDto img : images) {
                try { fileUploadService.deleteFile(img.getFilePath()); } catch (Exception ignore) {}
            }
            try { fileUploadService.deleteFile(origin.getFilePath()); } catch (Exception ignore) {}
            throw new RuntimeException("DB 저장 실패, 파일 롤백됨", e);
        }
    }

    public List<String> getAllSlideImages(Long fileId) {
        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다" + fileId));

        return presentationImageRepository.findAllByFile_FileIdOrderByPageNumberAsc(fileId)
                .stream()
                .map(PresentationImage::getUrl)
                .toList();
    }

    // 파일 삭제
    @Transactional
    public void deleteFile(Long fileId) {
        PresentationFile file = presentationFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 파일을 찾을 수 없습니다. id=" + fileId));
        // 1) 삭제 대상 경로 수집(중복 제거)
        java.util.Set<String> paths = new java.util.HashSet<>();
        if (file.getFilePath() != null) paths.add(file.getFilePath());
        if (file.getThumbnailPath() != null) paths.add(file.getThumbnailPath());
        if (file.getImages() != null) {
            for (PresentationImage img : file.getImages()) {
                if (img.getPath() != null) paths.add(img.getPath());
            }
        }
        // 2) DB 삭제 (자식은 orphanRemoval=true 로 함께 삭제)
        presentationFileRepository.delete(file);

        // 3) 파일 시스템 정리 (best-effort)
        for (String p : paths) {
            try { fileUploadService.deleteFile(p); } catch (Exception ignore) {}
        }
        }
}