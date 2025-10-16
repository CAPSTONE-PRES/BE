package com.pres.pres_server.dto;

import lombok.*;

@Getter
@Setter
public class PresentationFileRequest {
    private String fileUrl;
    private String filePath;
    private String originalName;
    private String saveName;
    private String fileType;
    private Long fileSize;
}