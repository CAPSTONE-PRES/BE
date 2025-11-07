package com.pres.pres_server.service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    /**
     * 파일 업로드
     * @param file 업로드할 파일
     * @return S3에 업로드된 파일의 URL
     */
    public String upload(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("파일이 비었습니다.");

        String key = buildKey("profiles/", file.getOriginalFilename());

        try (InputStream is = file.getInputStream()) {
            PutObjectRequest req =
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            // 버킷 acl 차단이면 제외
                            //.acl(ObjectCannedACL.PUBLIC_READ)
                            .build();

            s3Client.putObject(req, RequestBody.fromInputStream(is, file.getSize()));
            return buildUrl(key);
        } catch (IOException e) {
            throw new RuntimeException("S3 업로드 실패", e);
        }
    }

    /**
     * 파일 삭제 (전체 URL 또는 key 모두 지원)
     */
    public void delete(String fileUrlOrKey) {
        String key = fileUrlOrKey.contains(".amazonaws.com/")
                ? extractKeyFromUrl(fileUrlOrKey)
                : fileUrlOrKey;

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("S3 삭제 성공: {}", key);
        } catch (Exception e) {
            log.error("S3 삭제 실패 key={}: {}", key, e.getMessage());
            throw new IllegalStateException("파일 삭제 실패");
        }
    }

    private String buildKey(String prefix, String originalName) {
        String ext = Optional.ofNullable(originalName)
                .filter(n -> n.contains("."))
                .map(n -> n.substring(n.lastIndexOf('.')))
                .orElse("");
        return prefix + UUID.randomUUID() + ext;
    }

    private String buildUrl(String key) {
        // 리전 포함 URL
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
        // 리전 미포함 표준 URL을 원하면:
        // return "https://" + bucket + ".s3.amazonaws.com/" + key;
    }

    private String extractKeyFromUrl(String url) {
        int idx = url.indexOf(".amazonaws.com/");
        if (idx < 0) return url;
        return url.substring(idx + ".amazonaws.com/".length() + url.substring(0, idx).lastIndexOf('/') + 1)
                .replaceFirst("^.+?\\.amazonaws\\.com/", ""); // 안전빵
    }

}