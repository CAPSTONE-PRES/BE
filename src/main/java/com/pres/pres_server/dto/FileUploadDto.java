package com.pres.pres_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class FileUploadDto {
    private Long fileId;
    private String fileUrl;

    // 필요시 fileName, fileType, fileSize 등 추가
}