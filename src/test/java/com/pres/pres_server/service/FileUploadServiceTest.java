package com.pres.pres_server.service;

import com.pres.pres_server.dto.FileInfoDto;
import com.pres.pres_server.service.file.FileUploadService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class FileUploadServiceTest {
    @InjectMocks
    private FileUploadService fileUploadService;

    private AutoCloseable closeable;
    private String testUploadDir = "uploads/test";
    private Path createdFilePath;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(fileUploadService, "uploadDir", testUploadDir);
        // 업로드 디렉토리 생성
        new File(testUploadDir).mkdirs();
    }

    @AfterEach
    void tearDown() throws Exception {
        // 테스트 중 생성된 파일/디렉토리 삭제
        if (createdFilePath != null && Files.exists(createdFilePath)) {
            Files.deleteIfExists(createdFilePath);
        }
        File dir = new File(testUploadDir);
        if (dir.exists()) {
            dir.delete();
        }
        closeable.close();
    }

    @Test
    void saveFile_and_deleteFile_정상동작() throws IOException {
        // given
        String fileName = "testfile.txt";
        String content = "Hello, test!";
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName, "text/plain", content.getBytes());

        // when
        FileInfoDto fileInfo = fileUploadService.saveFile(multipartFile);
        createdFilePath = Path.of(fileInfo.getFilePath());

        // then
        assertThat(Files.exists(createdFilePath)).isTrue();
        assertThat(fileInfo.getOriginalName()).isEqualTo(fileName);
        assertThat(fileInfo.getFileType()).isEqualTo("text/plain");
        assertThat(fileInfo.getSize()).isEqualTo(content.getBytes().length);
        assertThat(fileInfo.getFileUrl()).contains(fileName);

        // 파일 삭제 테스트
        fileUploadService.deleteFile(fileInfo.getFilePath());
        assertThat(Files.exists(createdFilePath)).isFalse();
    }

    @Test
    @DisplayName("null 파일 업로드 시 예외 발생 테스트")
    void saveFile_null파일_예외발생() {
        // when & then
        assertThrows(IllegalArgumentException.class,
                () -> fileUploadService.saveFile(null));
    }

    @Test
    @DisplayName("빈 파일 업로드 시 예외 발생 및 메시지 검증")
    void saveFile_빈파일_예외발생() {
        // given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fileUploadService.saveFile(emptyFile));
        assertThat(exception.getMessage()).isEqualTo("빈 파일은 업로드할 수 없습니다.");
    }

    @Test
    @DisplayName("파일명이 없는 경우 테스트")
    void saveFile_파일명없음_처리() throws IOException {
        // given
        MockMultipartFile fileWithoutName = new MockMultipartFile(
                "file", null, "text/plain", "content".getBytes());

        // when
        FileInfoDto fileInfo = fileUploadService.saveFile(fileWithoutName);
        createdFilePath = Path.of(fileInfo.getFilePath());

        // then
        assertThat(fileInfo.getOriginalName()).isNull();
        assertThat(Files.exists(createdFilePath)).isTrue();
    }

    @Test
    @DisplayName("빈 문자열 파일명 테스트")
    void saveFile_빈문자열파일명_null로처리() throws IOException {
        // given
        MockMultipartFile fileWithEmptyName = new MockMultipartFile(
                "file", "", "text/plain", "content".getBytes());

        // when
        FileInfoDto fileInfo = fileUploadService.saveFile(fileWithEmptyName);
        createdFilePath = Path.of(fileInfo.getFilePath());

        // then
        assertThat(fileInfo.getOriginalName()).isNull();
        assertThat(Files.exists(createdFilePath)).isTrue();
    }

    @Test
    @DisplayName("특수문자가 포함된 파일명 테스트")
    void saveFile_특수문자파일명_처리() throws IOException {
        // given
        String fileName = "test file!@#$%^&*()_+.txt";
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName, "text/plain", "content".getBytes());

        // when
        FileInfoDto fileInfo = fileUploadService.saveFile(multipartFile);
        createdFilePath = Path.of(fileInfo.getFilePath());

        // then
        assertThat(Files.exists(createdFilePath)).isTrue();
        assertThat(fileInfo.getOriginalName()).isEqualTo(fileName);
    }

    @Test
    @DisplayName("대용량 파일 업로드 테스트")
    void saveFile_대용량파일_처리() throws IOException {
        // given - 1MB 파일 생성
        byte[] largeContent = new byte[1024 * 1024]; // 1MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.txt", "text/plain", largeContent);

        // when
        long startTime = System.currentTimeMillis();
        FileInfoDto fileInfo = fileUploadService.saveFile(largeFile);
        long endTime = System.currentTimeMillis();
        createdFilePath = Path.of(fileInfo.getFilePath());

        // then
        assertThat(Files.exists(createdFilePath)).isTrue();
        assertThat(fileInfo.getSize()).isEqualTo(largeContent.length);
        assertThat(endTime - startTime).isLessThan(5000); // 5초 이내
    }

    @Test
    @DisplayName("동일한 파일명 업로드 시 중복 처리 테스트")
    void saveFile_동일파일명_중복처리() throws IOException {
        // given
        String fileName = "duplicate.txt";
        MockMultipartFile file1 = new MockMultipartFile(
                "file", fileName, "text/plain", "content1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile(
                "file", fileName, "text/plain", "content2".getBytes());

        // when
        FileInfoDto fileInfo1 = fileUploadService.saveFile(file1);
        FileInfoDto fileInfo2 = fileUploadService.saveFile(file2);

        createdFilePath = Path.of(fileInfo1.getFilePath()); // 첫 번째 파일 경로 저장

        // then
        assertThat(fileInfo1.getFilePath()).isNotEqualTo(fileInfo2.getFilePath());
        assertThat(Files.exists(Path.of(fileInfo1.getFilePath()))).isTrue();
        assertThat(Files.exists(Path.of(fileInfo2.getFilePath()))).isTrue();

        // 정리
        Files.deleteIfExists(Path.of(fileInfo2.getFilePath()));
    }

    @Test
    @DisplayName("존재하지 않는 파일 삭제 테스트")
    void deleteFile_존재하지않는파일_예외없음() {
        // given
        String nonExistentPath = testUploadDir + "/nonexistent.txt";

        // when & then - 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> fileUploadService.deleteFile(nonExistentPath));
    }

    @Test
    @DisplayName("빈 경로로 파일 삭제 테스트")
    void deleteFile_빈경로_예외발생() {
        // when & then
        assertThrows(IllegalArgumentException.class,
                () -> fileUploadService.deleteFile(""));
    }

    @Test
    @DisplayName("null 경로로 파일 삭제 테스트")
    void deleteFile_null경로_예외발생() {
        // when & then
        assertThrows(IllegalArgumentException.class,
                () -> fileUploadService.deleteFile(null));
    }

    @Test
    @DisplayName("업로드 디렉토리가 존재하지 않는 경우 테스트")
    void saveFile_업로드디렉토리없음_자동생성() throws IOException {
        // given
        String nonExistentDir = "uploads/nonexistent";
        ReflectionTestUtils.setField(fileUploadService, "uploadDir", nonExistentDir);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes());

        // when
        FileInfoDto fileInfo = fileUploadService.saveFile(file);
        createdFilePath = Path.of(fileInfo.getFilePath());

        // then
        assertThat(Files.exists(createdFilePath)).isTrue();
        assertThat(Files.exists(Path.of(nonExistentDir))).isTrue();

        // 정리 - 파일 먼저 삭제 후 디렉토리 삭제
        Files.deleteIfExists(createdFilePath);
        Files.deleteIfExists(Path.of(nonExistentDir));
    }

    @Test
    @DisplayName("다양한 파일 확장자 테스트")
    void saveFile_다양한확장자_처리() throws IOException {
        // given
        String[] extensions = { ".pdf", ".pptx", ".docx", ".jpg", ".png" };
        String[] mimeTypes = {
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "image/jpeg",
                "image/png"
        };

        for (int i = 0; i < extensions.length; i++) {
            // given
            String fileName = "test" + extensions[i];
            MockMultipartFile file = new MockMultipartFile(
                    "file", fileName, mimeTypes[i], "content".getBytes());

            // when
            FileInfoDto fileInfo = fileUploadService.saveFile(file);
            Path filePath = Path.of(fileInfo.getFilePath());

            // then
            assertThat(Files.exists(filePath)).isTrue();
            assertThat(fileInfo.getFileType()).isEqualTo(mimeTypes[i]);

            // 정리
            Files.deleteIfExists(filePath);
        }
    }

    @Test
    @DisplayName("파일 크기 검증 테스트")
    void saveFile_파일크기검증() throws IOException {
        // given
        byte[] content = "test content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", content);

        // when
        FileInfoDto fileInfo = fileUploadService.saveFile(file);
        createdFilePath = Path.of(fileInfo.getFilePath());

        // then
        assertThat(fileInfo.getSize()).isEqualTo(content.length);
        assertThat(Files.size(createdFilePath)).isEqualTo(content.length);
    }

    @Test
    @DisplayName("예외 메시지 검증 - null 파일")
    void saveFile_null파일_메시지검증() {
        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fileUploadService.saveFile(null));
        assertThat(exception.getMessage()).isEqualTo("업로드할 파일이 없습니다.");
    }

    @Test
    @DisplayName("예외 메시지 검증 - null 경로 삭제")
    void deleteFile_null경로_메시지검증() {
        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fileUploadService.deleteFile(null));
        assertThat(exception.getMessage()).isEqualTo("삭제할 파일 경로가 유효하지 않습니다.");
    }

    @Test
    @DisplayName("예외 메시지 검증 - 빈 경로 삭제")
    void deleteFile_빈경로_메시지검증() {
        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fileUploadService.deleteFile(""));
        assertThat(exception.getMessage()).isEqualTo("삭제할 파일 경로가 유효하지 않습니다.");
    }
}
