package com.pres.pres_server.service.file;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.FileInfoDto;
import com.pres.pres_server.dto.FileUploadDto;
import com.pres.pres_server.repository.FileUploadRepository;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

// 파일 업로드 요청의 전체 비즈니스 로직만 관리
// FileUploadService를 호출하여 파일을 먼저 저장한 후, DB 트랜잭션 내에서 파일의 메타데이터를 저장
// 만약 DB 저장에 실패하면, 이미 저장된 파일을 삭제하는 보상 로직을 실행 -> DB와 파일 시스템의 상태를 일치

// 파일 메타데이터를 DB에 저장하는 서비스
@Service
@RequiredArgsConstructor
public class PresentationFileService {
    private final FileUploadRepository fileUploadRepository;
    private final UserRepository userRepository;
    private final FileUploadService fileUploadService;
    private final ProjectRepository projectRepository;

    @Transactional
    public FileUploadDto uploadAndSave(MultipartFile file, Long uploaderId, Long projectId) {
        // 1. 파일 시스템에 파일 먼저 저장
        FileInfoDto fileInfoDto = fileUploadService.saveFile(file);

        // 2. 파일 저장 성공 시, DB에 메타데이터 저장
        try {
            PresentationFile presentationFile = new PresentationFile();
            presentationFile.setSaveName(fileInfoDto.getSaveName());
            presentationFile.setFilePath(fileInfoDto.getFilePath());
            presentationFile.setFileUrl(fileInfoDto.getFileUrl());
            presentationFile.setOriginalName(fileInfoDto.getOriginalName());
            presentationFile.setFileType(fileInfoDto.getFileType());
            presentationFile.setFileSize(fileInfoDto.getSize());
            presentationFile.setUploadedAt(fileInfoDto.getUploadedAt());

            // 유저, 프로젝트 설정
            User uploader = userRepository.findById(uploaderId)
                    .orElseThrow(() -> new IllegalArgumentException("업로더를 찾을 수 없습니다. id=" + uploaderId));
            presentationFile.setUploader(uploader);
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다. id=" + projectId));
            presentationFile.setProject(project);

            PresentationFile savedFile = fileUploadRepository.save(presentationFile);
            return FileUploadDto.builder()
                    .fileId(savedFile.getFileId())
                    .fileUrl(savedFile.getFileUrl())
                    .build();
        } catch (RuntimeException e) {
            // 3. DB 저장 실패 시, 이미 저장된 파일 삭제 (보상 로직)
            fileUploadService.deleteFile(fileInfoDto.getFilePath());
            throw new RuntimeException("DB 저장 실패, 파일 롤백됨", e);
        }
    }

    // 파일 삭제
    @Transactional
    public void deleteFile(Long fileId) {
        PresentationFile file = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 파일을 찾을 수 없습니다. id=" + fileId));
        // 1. DB에서 메타데이터 삭제
        fileUploadRepository.delete(file);
        // 2. 파일 시스템에서 실제 파일 삭제
        fileUploadService.deleteFile(file.getFilePath());
    }
}