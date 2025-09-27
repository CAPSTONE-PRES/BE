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

import org.springframework.http.converter.StringHttpMessageConverter;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.Map;

@Service
public class WhisperService {
    private static final Logger log = LoggerFactory.getLogger(WhisperService.class);

    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    private static final String WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions";

    public String transcribe(File wavFile) throws Exception {
        log.info("      ▶ Preparing Whisper request for file: {}", wavFile.getName());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(OPENAI_API_KEY);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(wavFile));
        body.add("model", "whisper-1");

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);
        log.info("      ▶ Sending Whisper API request...");

        RestTemplate rest = new RestTemplate();
        // 한글 깨짐 현상 -> utf-8 명시
        rest.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = rest.postForObject(WHISPER_API_URL, req, Map.class);

        String text = (String) resp.getOrDefault("text", "");
        log.info("      ▶ Whisper API responded, text length = {}", text.length());
        return text;
    }
}
