package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.PresentationImage;
import com.pres.pres_server.repository.PresentationImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 프레젠테이션 이미지 조회 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationImageService {

    private final PresentationImageRepository presentationImageRepository;

    /**
     * 파일의 전체 이미지 URL 리스트 조회
     */
    @Transactional(readOnly = true)
    public List<String> getImageUrls(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);
        }

        List<PresentationImage> images = presentationImageRepository
                .findAllByFile_FileIdOrderByPageNumberAsc(fileId);

        if (images.isEmpty()) {
            log.warn("이미지를 찾을 수 없습니다: fileId={}", fileId);
            throw new IllegalStateException("이미지가 생성되지 않았습니다: fileId=" + fileId);
        }

        String apiPath = "/api/files/";

        return images.stream()
                .map(img -> apiPath + fileId + "/page/" + img.getPageNumber() + "/image")
                .toList();
    }

    /**
     * 페이지 번호 -> 외부 이미지 URL 매핑을 반환합니다.
     * 사용처: 썸네일 등 외부에서 직접 불러와야 할 때(img.url 필드 사용).
     */
    @Transactional(readOnly = true)
    public java.util.Map<Integer, String> getImageUrlMap(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);
        }

        List<PresentationImage> images = presentationImageRepository
                .findAllByFile_FileIdOrderByPageNumberAsc(fileId);

        if (images.isEmpty()) {
            log.warn("이미지를 찾을 수 없습니다: fileId={}", fileId);
            return java.util.Collections.emptyMap();
        }

        java.util.Map<Integer, String> map = new java.util.TreeMap<>();
        for (PresentationImage img : images) {
            if (img.getPageNumber() != null) {
                // 프록시 경로를 반환하도록 변경: 프론트는 백엔드 프록시를 통해 이미지를 가져오도록 함
                String proxyPath = "/api/files/" + fileId + "/page/" + img.getPageNumber() + "/image";
                map.put(img.getPageNumber(), proxyPath);
            }
        }
        return map;
    }

    /**
     * 특정 페이지의 이미지 리소스 조회
     */
    @Transactional(readOnly = true)
    public Resource getSlideImageResource(Long fileId, Integer pageNumber) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 파일 ID입니다: " + fileId);
        }
        if (pageNumber == null || pageNumber <= 0) {
            throw new IllegalArgumentException("유효하지 않은 페이지 번호입니다: " + pageNumber);
        }

        PresentationImage img = presentationImageRepository
                .findByFile_FileIdAndPageNumber(fileId, pageNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "이미지를 찾을 수 없습니다: fileId=" + fileId + ", page=" + pageNumber));

        FileSystemResource resource = new FileSystemResource(img.getPath());

        if (!resource.exists()) {
            log.error("이미지 파일이 디스크에 존재하지 않습니다: {}", img.getPath());
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "이미지 파일이 존재하지 않습니다");
        }

        return resource;
    }
}