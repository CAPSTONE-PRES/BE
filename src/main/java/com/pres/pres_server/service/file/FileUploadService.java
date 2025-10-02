package com.pres.pres_server.service.file;

import org.springframework.web.multipart.MultipartFile;

import com.pres.pres_server.dto.FileInfoDto;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.io.IOException;
import java.nio.file.Files;

import org.springframework.stereotype.Service;

// 순수 파일 시스템 I/O 담당 서비스 (내부 동작용)
// DB와는 직접적으로 관련 없음
@Service
public class FileUploadService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    // 파일 시스템에 파일 저장, 저장된 파일의 정보 반환
    public FileInfoDto saveFile(MultipartFile file) {

        if (file == null) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        }
        // 파일 이름과 경로 처리
        String originalName = file.getOriginalFilename();

        // 빈 문자열도 null로 정규화 (일관성 확보)
        if (originalName != null && originalName.trim().isEmpty()) {
            originalName = null;
        }

        // 파일 저장용 안전한 이름 생성
        String saveFileName;
        if (originalName != null) {
            // Windows에서 지원하지 않는 특수문자 제거/변환
            saveFileName = originalName.replaceAll("[<>:\"/\\\\|?*]", "_");
        } else {
            // 프론트에 warning 전달 고려
            saveFileName = "unnamed_file";
        }

        String saveName = UUID.randomUUID().toString() + "_" + saveFileName;
        long size = file.getSize();
        String fileType = file.getContentType();
        LocalDateTime uploadedAt = LocalDateTime.now();

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath(); // 절대 경로로 변환하여 안정성 확보
        Path fullPath = uploadPath.resolve(saveName);

        // 파일 시스템에 파일 저장
        try {
            // 디렉토리 없을 시 생성
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            // 파일 저장
            file.transferTo(fullPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }
        String filePath = fullPath.toString();
        String fileUrl = "/files/" + saveName; // 서비스 환경에 맞게 수정

        FileInfoDto fileInfoDto = FileInfoDto.builder()
                .originalName(originalName)
                .saveName(saveName)
                .fileType(fileType)
                .size(size)
                .filePath(filePath)
                .fileUrl(fileUrl)
                .uploadedAt(uploadedAt)
                .build();
        return fileInfoDto;
    }

    // 파일 시스템 filePath를 기준으로 파일 삭제
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("삭제할 파일 경로가 유효하지 않습니다.");
        }
        Path path = Paths.get(filePath);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException("파일 삭제 실패", e);
        }
    }
}