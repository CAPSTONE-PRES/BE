package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.domain.Project;
import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.FileInfoDto;
import com.pres.pres_server.dto.FileUploadDto;
import com.pres.pres_server.repository.FileUploadRepository;
import com.pres.pres_server.repository.ProjectRepository;
import com.pres.pres_server.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PresentationFileServiceTest {
        @Mock
        private FileUploadRepository fileUploadRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private FileUploadService fileUploadService;
        @Mock
        private ProjectRepository projectRepository;

        @InjectMocks
        private PresentationFileService presentationFileService;

        @BeforeEach
        void setUp() {
                MockitoAnnotations.openMocks(this);
        }

        @Test
        @DisplayName("파일 업로드 및 DB 저장 성공 테스트")
        void uploadAndSave_성공_정상동작() {
                // given
                MockMultipartFile multipartFile = new MockMultipartFile("file", "test.pdf", "application/pdf",
                                "dummy".getBytes());
                FileInfoDto fileInfoDto = FileInfoDto.builder()
                                .saveName("saveName.pdf")
                                .filePath("/path/saveName.pdf")
                                .fileUrl("/url/saveName.pdf")
                                .originalName("test.pdf")
                                .fileType("pdf")
                                .size(123L)
                                .uploadedAt(java.time.LocalDateTime.now())
                                .build();
                User uploader = User.builder()
                                .email("test@example.com")
                                .username("testuser")
                                .build();
                Project project = new Project();
                project.setTitle("test project");
                PresentationFile savedFile = new PresentationFile();
                savedFile.setFileId(1L);
                savedFile.setFileUrl("/url/saveName.pdf");

                when(fileUploadService.saveFile(any())).thenReturn(fileInfoDto);
                when(userRepository.findById(any())).thenReturn(Optional.of(uploader));
                when(projectRepository.findById(any())).thenReturn(Optional.of(project));
                when(fileUploadRepository.save(any(PresentationFile.class))).thenReturn(savedFile);

                // when
                FileUploadDto result = presentationFileService.uploadAndSave(multipartFile, 1L, 1L);

                // then
                assertNotNull(result);
                assertEquals(savedFile.getFileId(), result.getFileId());
                assertEquals(savedFile.getFileUrl(), result.getFileUrl());
                verify(fileUploadService).saveFile(any());
                verify(fileUploadRepository).save(any(PresentationFile.class));
        }

        @Test
        @DisplayName("DB 저장 실패 시 파일 롤백 테스트")
        void uploadAndSave_DB저장실패_파일롤백() {
                // given
                MockMultipartFile multipartFile = new MockMultipartFile("file", "test.pdf", "application/pdf",
                                "dummy".getBytes());
                FileInfoDto fileInfoDto = FileInfoDto.builder()
                                .saveName("saveName.pdf")
                                .filePath("/path/saveName.pdf")
                                .fileUrl("/url/saveName.pdf")
                                .originalName("test.pdf")
                                .fileType("pdf")
                                .size(123L)
                                .uploadedAt(java.time.LocalDateTime.now())
                                .build();
                User uploader = User.builder()
                                .email("test@example.com")
                                .username("testuser")
                                .build();
                Project project = new Project();
                project.setTitle("test project");

                when(fileUploadService.saveFile(any())).thenReturn(fileInfoDto);
                when(userRepository.findById(any())).thenReturn(Optional.of(uploader));
                when(projectRepository.findById(any())).thenReturn(Optional.of(project));
                when(fileUploadRepository.save(any(PresentationFile.class)))
                                .thenThrow(new RuntimeException("DB Error"));

                // when & then
                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> presentationFileService.uploadAndSave(multipartFile, 1L, 1L));
                assertTrue(ex.getMessage().contains("DB 저장 실패"));
                verify(fileUploadService).deleteFile(fileInfoDto.getFilePath());
        }

        @Test
        @DisplayName("파일 삭제 성공 테스트")
        void deleteFile_성공_정상동작() {
                // given
                PresentationFile file = new PresentationFile();
                file.setFilePath("/path/saveName.pdf");
                when(fileUploadRepository.findById(any())).thenReturn(Optional.of(file));

                // when
                presentationFileService.deleteFile(1L);

                // then
                verify(fileUploadRepository).delete(file);
                verify(fileUploadService).deleteFile(file.getFilePath());
        }

        @Test
        @DisplayName("삭제할 파일이 없을 때 예외 테스트")
        void deleteFile_파일없음_예외발생() {
                // given
                when(fileUploadRepository.findById(any())).thenReturn(Optional.empty());

                // when & then
                assertThrows(IllegalArgumentException.class, () -> presentationFileService.deleteFile(1L));
        }

        @Test
        @DisplayName("업로더를 찾을 수 없을 때 예외 및 롤백 테스트")
        void uploadAndSave_업로더없음_예외및롤백() {
                // given
                MockMultipartFile multipartFile = new MockMultipartFile("file", "test.pdf", "application/pdf",
                                "dummy".getBytes());
                FileInfoDto fileInfoDto = FileInfoDto.builder()
                                .filePath("/path/saveName.pdf")
                                .build();

                when(fileUploadService.saveFile(any())).thenReturn(fileInfoDto);
                when(userRepository.findById(any())).thenReturn(Optional.empty());

                // when & then
                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> presentationFileService.uploadAndSave(multipartFile, 1L, 1L));
                assertTrue(ex.getMessage().contains("DB 저장 실패"));
                assertTrue(ex.getCause().getMessage().contains("업로더를 찾을 수 없습니다"));
                verify(fileUploadService).deleteFile(fileInfoDto.getFilePath()); // 롤백 확인
        }

        @Test
        @DisplayName("프로젝트를 찾을 수 없을 때 예외 및 롤백 테스트")
        void uploadAndSave_프로젝트없음_예외및롤백() {
                // given
                MockMultipartFile multipartFile = new MockMultipartFile("file", "test.pdf", "application/pdf",
                                "dummy".getBytes());
                FileInfoDto fileInfoDto = FileInfoDto.builder()
                                .filePath("/path/saveName.pdf")
                                .build();
                User uploader = User.builder()
                                .email("test@example.com")
                                .username("testuser")
                                .build();

                when(fileUploadService.saveFile(any())).thenReturn(fileInfoDto);
                when(userRepository.findById(any())).thenReturn(Optional.of(uploader));
                when(projectRepository.findById(any())).thenReturn(Optional.empty());

                // when & then
                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> presentationFileService.uploadAndSave(multipartFile, 1L, 1L));
                assertTrue(ex.getMessage().contains("DB 저장 실패"));
                assertTrue(ex.getCause().getMessage().contains("프로젝트를 찾을 수 없습니다"));
                verify(fileUploadService).deleteFile(fileInfoDto.getFilePath()); // 롤백 확인
        }

        @Test
        @DisplayName("파일 저장 실패 시 예외 전파 테스트")
        void uploadAndSave_파일저장실패_예외전파() {
                // given
                MockMultipartFile multipartFile = new MockMultipartFile("file", "test.pdf", "application/pdf",
                                "dummy".getBytes());
                when(fileUploadService.saveFile(any())).thenThrow(new IllegalArgumentException("업로드할 파일이 없습니다."));

                // when & then
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> presentationFileService.uploadAndSave(multipartFile, 1L, 1L));
                assertEquals("업로드할 파일이 없습니다.", ex.getMessage());

                // DB 관련 호출이 없어야 함
                verify(userRepository, never()).findById(any());
                verify(projectRepository, never()).findById(any());
                verify(fileUploadRepository, never()).save(any());
        }
}
