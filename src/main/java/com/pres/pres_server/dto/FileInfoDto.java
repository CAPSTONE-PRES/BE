package com.pres.pres_server.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FileInfoDto {
    private String originalName;
    private String saveName;
    private String fileType;
    private Long size;
    private String filePath;
    private String fileUrl;
    private LocalDateTime uploadedAt;
    // 필요시 추가 필드
}
