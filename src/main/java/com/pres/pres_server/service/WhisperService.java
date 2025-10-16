package com.pres.pres_server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import org.springframework.http.converter.StringHttpMessageConverter;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.pres.pres_server.service.analyse.SilenceDetectionService.WhisperSegment;

/**
 * OpenAI Whisper API를 사용한 음성-텍스트 변환 서비스
 * 
 * <p>
 * 이 서비스는 음성 파일을 텍스트로 변환하는 외부 API 호출을 담당합니다.
 * </p>
 * <p>
 * <b>보장:</b> 반환값은 절대 null이 아니며, 최소한 빈 문자열("")을 반환합니다.
 * </p>
 */
@Service
public class WhisperService {
    private static final Logger log = LoggerFactory.getLogger(WhisperService.class);

    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    private static final String WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String DEFAULT_MODEL = "whisper-1";

    private final RestTemplate restTemplate;

    /**
     * RestTemplate을 싱글톤으로 생성 및 설정
     */
    public WhisperService() {
        this.restTemplate = new RestTemplate();
        // 한글 깨짐 방지 - UTF-8 인코딩 설정
        this.restTemplate.getMessageConverters()
                .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    /**
     * 음성 파일을 텍스트로 변환 (timestamp 정보 포함)
     * 
     * @param wavFile           변환할 WAV 파일
     * @param includeTimestamps timestamp 정보를 포함할지 여부
     * @return TranscriptionResult (text + segments)
     */
    public TranscriptionResult transcribeWithTimestamps(File wavFile, boolean includeTimestamps) {
        validateInputFile(wavFile);

        log.info("      ▶ Preparing Whisper request for file: {} (timestamps: {})",
                wavFile.getName(), includeTimestamps);

        try {
            HttpEntity<MultiValueMap<String, Object>> request = buildRequest(wavFile, includeTimestamps);

            log.info("      ▶ Sending Whisper API request...");
            Map<String, Object> response = callWhisperApi(request);

            String text = extractTextFromResponse(response);
            List<WhisperSegment> segments = includeTimestamps ? extractSegments(response) : null;

            log.info("      ▶ Whisper API responded, text length = {}, segments = {}",
                    text.length(), segments != null ? segments.size() : 0);

            return TranscriptionResult.builder()
                    .text(text)
                    .segments(segments)
                    .build();

        } catch (RestClientException e) {
            log.error("      ✗ Whisper API 호출 실패: {}", e.getMessage());
            return TranscriptionResult.builder().text("").build();

        } catch (Exception e) {
            log.error("      ✗ Whisper 변환 중 예외 발생", e);
            return TranscriptionResult.builder().text("").build();
        }
    }

    /**
     * 음성 파일을 텍스트로 변환
     * 
     * @param wavFile 변환할 WAV 파일 (null 불가, 존재하는 파일이어야 함)
     * @return 변환된 텍스트 (null 반환 없음, 실패 시 빈 문자열)
     * @throws IllegalArgumentException 파일이 null이거나 존재하지 않는 경우
     */
    public String transcribe(File wavFile) {
        return transcribeWithTimestamps(wavFile, false).getText();
    }

    /**
     * 입력 파일 유효성 검증
     */
    private void validateInputFile(File wavFile) {
        if (wavFile == null) {
            throw new IllegalArgumentException("입력 파일이 null입니다.");
        }
        if (!wavFile.exists()) {
            throw new IllegalArgumentException(
                    String.format("파일이 존재하지 않습니다: %s", wavFile.getAbsolutePath()));
        }
        if (!wavFile.canRead()) {
            throw new IllegalArgumentException(
                    String.format("파일을 읽을 수 없습니다: %s", wavFile.getAbsolutePath()));
        }
    }

    /**
     * Whisper API 요청 객체 생성
     */
    private HttpEntity<MultiValueMap<String, Object>> buildRequest(File wavFile, boolean includeTimestamps) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(OPENAI_API_KEY);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(wavFile));
        body.add("model", DEFAULT_MODEL);

        // timestamp 정보를 받으려면 response_format을 verbose_json으로 설정
        if (includeTimestamps) {
            body.add("response_format", "verbose_json");
            body.add("timestamp_granularities[]", "segment"); // segment 단위 timestamp
        }

        return new HttpEntity<>(body, headers);
    }

    /**
     * Whisper API 호출
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callWhisperApi(
            HttpEntity<MultiValueMap<String, Object>> request) {

        Map<String, Object> response = restTemplate.postForObject(
                WHISPER_API_URL, request, Map.class);

        if (response == null) {
            log.warn("      ⚠ Whisper API 응답이 null");
            throw new RestClientException("API 응답이 null입니다.");
        }

        return response;
    }

    /**
     * API 응답에서 텍스트 추출 및 null 안전성 보장
     */
    private String extractTextFromResponse(Map<String, Object> response) {
        Object textObj = response.get("text");

        if (textObj == null) {
            log.warn("      ⚠ 응답에 'text' 필드가 없거나 null");
            return "";
        }

        String text = textObj.toString();
        return text != null ? text : "";
    }

    /**
     * API 응답에서 segments 추출 (verbose_json 형식)
     */
    @SuppressWarnings("unchecked")
    private List<WhisperSegment> extractSegments(Map<String, Object> response) {
        Object segmentsObj = response.get("segments");

        if (segmentsObj == null || !(segmentsObj instanceof List)) {
            log.warn("      ⚠ 응답에 'segments' 필드가 없거나 형식이 다름");
            return null;
        }

        List<Map<String, Object>> segmentsList = (List<Map<String, Object>>) segmentsObj;
        List<WhisperSegment> segments = new ArrayList<>();

        for (Map<String, Object> seg : segmentsList) {
            try {
                segments.add(WhisperSegment.builder()
                        .start(getDoubleValue(seg, "start"))
                        .end(getDoubleValue(seg, "end"))
                        .text((String) seg.get("text"))
                        .build());
            } catch (Exception e) {
                log.warn("      ⚠ segment 파싱 실패: {}", e.getMessage());
            }
        }

        return segments;
    }

    /**
     * Map에서 double 값 추출 (Number 타입 처리)
     */
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    /**
     * Whisper API 응답 결과
     */
    @lombok.Data
    @lombok.Builder
    public static class TranscriptionResult {
        private String text; // 변환된 텍스트
        private List<WhisperSegment> segments; // segment 정보 (timestamp 포함)
    }
}
