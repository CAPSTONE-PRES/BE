package com.pres.pres_server.service.file;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.web.multipart.MultipartFile;

import com.pres.pres_server.dto.file.FileInfoDto;

import org.springframework.beans.factory.annotation.Value;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

// 순수 파일 시스템 I/O 담당 서비스 (내부 동작용)
// DB와는 직접적으로 관련 없음
@Service
@Slf4j
public class FileUploadService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @org.springframework.beans.factory.annotation.Value("${file.font-dir:}")
    private String fontDir;

    // 폰트 등록 상태 플래그
    private static volatile boolean fontsRegistered = false;

    // 등록되지 않은 경우 fontDir에 있는 ttf/otf 폰트를 JVM에 등록합니다.
    public void registerFontsFromConfig() {
        if (fontsRegistered) return;
        synchronized (FileUploadService.class) {
            if (fontsRegistered) return;
            if (fontDir == null || fontDir.isBlank()) {
                fontsRegistered = true; // no-op
                return;
            }
            try {
                java.nio.file.Path dir = Paths.get(fontDir);
                if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                    log.warn("Font dir does not exist or is not a directory: {}", fontDir);
                    fontsRegistered = true;
                    return;
                }
                java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
                try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(dir)) {
                    stream.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".ttf") || n.endsWith(".otf");
                    }).forEach(p -> {
                        try {
                            java.awt.Font f = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, p.toFile());
                            ge.registerFont(f);
                            log.info("Registered font: {}", p.getFileName());
                        } catch (Exception e) {
                            log.warn("Failed to register font {}: {}", p.getFileName(), e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("Failed to register fonts from {}: {}", fontDir, e.getMessage());
            } finally {
                fontsRegistered = true;
            }
        }
    }

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

        // 확장자만 떼기
        String ext = "";
        if (originalName != null) {
            int dotIdx = originalName.lastIndexOf('.');
            if (dotIdx != -1 && dotIdx < originalName.length() - 1) {
                ext = originalName.substring(dotIdx); // ".pdf" 같은거
            }
        }

        // 저장용 파일명 UUID + 확장자
        String saveName = UUID.randomUUID().toString() + ext;

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

    /**
     * PPTX 파일을 PDF로 변환하여 저장하고, 변환된 PDF의 FileInfoDto 반환
     * (실제 변환 구현은 외부 라이브러리 필요, 예: Apache POI+PDFBox, LibreOffice CLI 등)
     *
     * @param pptxFilePath 저장된 PPTX 파일의 경로
     * @return 변환된 PDF 파일의 FileInfoDto
     */
    public List<FileInfoDto> convertPptxToImage(String pptxFilePath, int dpi, int maxSlides) {
        if (pptxFilePath.isEmpty()) {
            throw new IllegalArgumentException("파일 경로가 유효하지 않습니다.");
        }
        final Path path = Paths.get(pptxFilePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("PPTX 파일이 존재하지 않습니다: " + pptxFilePath);
        }
        if (!path.getFileName().toString().toLowerCase().endsWith(".pptx")) {
            throw new IllegalArgumentException("지원하지 않는 확장자( .pptx 파일만 지원 가능): " + path.getFileName());
        }
        // DPI 안전범위
        int safeDpi = Math.max(72, Math.min(dpi, 450));
        int slideLimit = Math.max(0, maxSlides);

            try {
            // 폰트 디렉토리에 폰트를 등록해서 렌더링 폰트 폴백에 도움
            try {
                registerFontsFromConfig();
            } catch (Exception ignore) {
            }
            return convertPptxToImagesInternal(path, safeDpi, slideLimit);
        } catch (IOException e) {
            throw new RuntimeException("PPTX → 이미지 변환 실패: " + path, e);
        }
    }

    // PPTX(.pptx) 처리
    private List<FileInfoDto> convertPptxToImagesInternal(Path pptxPath, int dpi, int maxSlides) throws IOException {
        ImageIO.setUseCache(true);
        List<FileInfoDto> result = new ArrayList<>();
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(uploadPath);

        try (XMLSlideShow show = new XMLSlideShow(Files.newInputStream(pptxPath))) {
            // Diagnostic: log available font families on the JVM (helps detect missing fonts)
            try {
                java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
                String[] availableFonts = ge.getAvailableFontFamilyNames();
                log.info("Available JVM fonts (count={}): {}", availableFonts.length,
                        String.join(", ", java.util.Arrays.copyOf(availableFonts, Math.min(30, availableFonts.length))));
            } catch (Exception e) {
                log.warn("Failed to list JVM fonts: {}", e.getMessage());
            }

            // Diagnostic: inspect slides for declared font families in text runs
            try {
                int slideIdx = 0;
                for (XSLFSlide s : show.getSlides()) {
                    slideIdx++;
                    try {
                        java.util.Set<String> slideFonts = new java.util.LinkedHashSet<>();
                        for (org.apache.poi.sl.usermodel.Shape shape : s.getShapes()) {
                            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape) {
                                org.apache.poi.xslf.usermodel.XSLFTextShape tx = (org.apache.poi.xslf.usermodel.XSLFTextShape) shape;
                                for (org.apache.poi.xslf.usermodel.XSLFTextParagraph p : tx.getTextParagraphs()) {
                                    for (org.apache.poi.xslf.usermodel.XSLFTextRun r : p.getTextRuns()) {
                                        try {
                                            String f = r.getFontFamily();
                                            if (f != null && !f.isBlank())
                                                slideFonts.add(f);
                                        } catch (Exception ignore) {
                                        }
                                    }
                                }
                            }
                        }
                        if (!slideFonts.isEmpty())
                            log.info("Slide[{}] declared font families: {}", slideIdx, slideFonts);
                    } catch (Exception ex) {
                        log.debug("Failed to inspect slide text fonts for slide {}: {}", slideIdx, ex.getMessage());
                    }
                }
            } catch (Exception ignore) {
            }

            final String baseName = pptxPath.getFileName().toString().replaceAll("(?i)\\.pptx$",
                    "");
            final Dimension pg = show.getPageSize(); // pt 단위 (1/72 inch)
            final double scale = dpi / 72.0; // 최소 72dpi 보정
            final int w = (int) Math.round(pg.getWidth() * scale);
            final int h = (int) Math.round(pg.getHeight() * scale);
            final int total = show.getSlides().size();
            final int end = (maxSlides > 0) ? Math.min(maxSlides, total) : total;

            for (int i = 0; i < end; i++) {
                XSLFSlide slide = show.getSlides().get(i);

                // 배경 흰색의 RGB 이미지 (웹 표시/썸네일 용도)
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = img.createGraphics();
                // 품질 힌트
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    g2.setColor(Color.WHITE);
                    g2.fillRect(0, 0, w, h);

                    g2.scale(scale, scale);
                    slide.draw(g2);
                } catch (Exception e) {
                    throw new IOException("슬라이드 렌더링 실패 file=" + pptxPath.getFileName() + " index=" + i, e);
                } finally {
                    g2.dispose();

                }
                String imageFileName = String.format("%s_slide%02d.png", baseName, i + 1);
                String saveName = UUID.randomUUID() + "_" + imageFileName;
                Path outPath = uploadPath.resolve(saveName);

                boolean ok = ImageIO.write(img, "png", outPath.toFile());
                if (!ok || !Files.exists(outPath)) {
                    throw new IOException("이미지 저장 실패: " + outPath);
                }

                long fileSize = Files.size(outPath);
                String fileUrl = "/files/" + saveName;

                result.add(FileInfoDto.builder()
                        .originalName(imageFileName)
                        .saveName(saveName)
                        .fileType("image/png")
                        .size(fileSize)
                        .filePath(outPath.toString())
                        .fileUrl(fileUrl)
                        .uploadedAt(LocalDateTime.now())
                        .build());
            }
        }
        return result;
    }

    /**
     * PDF 전체 페이지를 이미지(PNG)로 변환하여 저장하고, 각 이미지의 FileInfoDto 리스트 반환
     *
     * @param pdfFilePath PDF 파일 경로(절대경로 권장)
     * @param dpi         렌더링 DPI (권장 150~300)
     * @return 변환된 이미지 파일들의 FileInfoDto 리스트 (페이지 순서대로)
     */
    public List<FileInfoDto> createPdfImages(String pdfFilePath, int dpi) {
        log.info("start createPdfImages");
        if (pdfFilePath == null || pdfFilePath.isEmpty()) {
            throw new IllegalArgumentException("파일 경로가 유효하지 않습니다.");
        }
        final Path pdfPath = Paths.get(pdfFilePath);
        if (!Files.exists(pdfPath)) {
            throw new IllegalArgumentException("PDF 파일이 존재하지 않습니다: " + pdfFilePath);
        }

        // DPI 안전범위 (필요시 조정)
        final int safeDpi = 150;

        final List<FileInfoDto> result = new ArrayList<>();
        final Path uploadPath = Paths.get(uploadDir).toAbsolutePath();

        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            log.info("failed to create directories");
            throw new RuntimeException("업로드 디렉토리 생성 실패: " + uploadPath, e);
        }

        // 대용량 PDF 대비 임시 파일 캐시 사용
        ImageIO.setUseCache(true);
        PDDocument document = null;
        log.info("start convert pdf to images");
        try {
            document = PDDocument.load(pdfPath.toFile());
            PDFRenderer renderer = new PDFRenderer(document);

            final int totalPages = document.getNumberOfPages();
            final String baseName = pdfPath.getFileName().toString().replaceAll("(?i)\\.pdf$", "");

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                BufferedImage bim = null;
                try { // 메모리 체크
                    checkMemoryAndWait();
                    // 렌더링
                    bim = renderer.renderImageWithDPI(pageIndex, safeDpi);
                    // 파일명/경로
                    String imageFileName = String.format("%s_page%02d.png", baseName, pageIndex + 1);
                    String saveName = java.util.UUID.randomUUID().toString() + "_" + imageFileName;
                    Path imagePath = uploadPath.resolve(saveName);

                    // 압축 옵션으로 저장
                    saveImageWithCompression(bim, imagePath);

                    if (!Files.exists(imagePath)) {
                        log.info("!Files.exists(imagePath)");
                        throw new RuntimeException("PDF→이미지 저장 실패: " + imagePath);
                    }

                    long fileSize = Files.size(imagePath);
                    String fileUrl = "/files/" + saveName;

                    result.add(FileInfoDto.builder()
                            .originalName(imageFileName)
                            .saveName(saveName)
                            .fileType("image/png")
                            .size(fileSize)
                            .filePath(imagePath.toString())
                            .fileUrl(fileUrl)
                            .uploadedAt(LocalDateTime.now())
                            .build());
                } finally {
                    // BufferedImage 명시적 해제
                    if (bim != null) {
                        bim.flush();
                        bim = null;
                    }

                    // 메모리 정리 힌트
                    if (pageIndex % 3 == 0) {
                        System.gc();
                        Thread.sleep(100);
                    }
                }
            }

        } catch (IOException e) {
            log.info("failed to create images" + e.getMessage());
            throw new RuntimeException("PDF→이미지 변환 실패: " + pdfFilePath, e);
        } catch (InterruptedException e) {
            log.info("failed to create images" + e.getMessage());
            Thread.currentThread().interrupt();
            throw new RuntimeException("PDF 변환 중단 됨", e);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException ignore) {
                }
            }
        }
        log.info("end createPdfImages");
        return result;
    }

    /**
     * 업로드 시 생성된 파일 URL(예: "/files/{saveName}")로부터 실제 파일 시스템 경로를 반환합니다.
     * 
     * @param fileUrl 저장된 파일의 public URL
     * @return 절대 파일 시스템 경로
     */
    public String resolveFilePathFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("fileUrl is null or empty");
        }
        // Expecting format "/files/{saveName}"
        String prefix = "/files/";
        if (!fileUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("Unsupported fileUrl format: " + fileUrl);
        }
        String saveName = fileUrl.substring(prefix.length());
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        Path fullPath = uploadPath.resolve(saveName);
        return fullPath.toString();
    }

    /**
     * 업로드된 파일 URL로부터 {@link Resource} 반환 (컨트롤러/서비스에서 바로 사용 가능)
     * 예외 발생 시 적절한 HTTP 상태코드로 변환하여 던집니다.
     */
    public Resource getFileResource(String fileUrl) {
        try {
            String path = resolveFilePathFromUrl(fileUrl);
            FileSystemResource resource = new FileSystemResource(path);
            if (!resource.exists()) {
                log.error("업로드 파일이 디스크에 존재하지 않습니다: {}", path);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일이 존재하지 않습니다");
            }
            return resource;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid fileUrl provided: {}", fileUrl);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 저장된 이미지 파일 경로 목록을 받아 임시 PDF 파일을 생성합니다.
     * 반환값은 생성된 PDF의 파일 시스템 경로입니다.
     */
    public String createPdfFromImages(java.util.List<String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new IllegalArgumentException("imagePaths must not be empty");
        }

        java.util.UUID uuid = java.util.UUID.randomUUID();
        String pdfName = uuid.toString() + ".pdf";
        java.nio.file.Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF 생성용 디렉토리 생성 실패", e);
        }

        java.nio.file.Path pdfPath = uploadPath.resolve(pdfName);

        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (String imgPath : imagePaths) {
                if (imgPath == null) continue;
                java.awt.image.BufferedImage bimg = javax.imageio.ImageIO.read(new java.io.File(imgPath));
                if (bimg == null) continue;
                float width = bimg.getWidth();
                float height = bimg.getHeight();

                org.apache.pdfbox.pdmodel.common.PDRectangle rect = new org.apache.pdfbox.pdmodel.common.PDRectangle(width, height);
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(rect);
                doc.addPage(page);

                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImage = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bimg);
                try (org.apache.pdfbox.pdmodel.PDPageContentStream content = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    content.drawImage(pdImage, 0, 0, width, height);
                }
            }
            doc.save(pdfPath.toFile());
        } catch (Exception e) {
            throw new RuntimeException("PDF 생성 실패", e);
        }

        return pdfPath.toString();
    }

    // 메모리 상태 체크 및 대기
    private void checkMemoryAndWait() throws InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        long availableMemory = maxMemory - usedMemory;

        // 사용 가능한 메모리가 100MB 미만이면 GC 대기
        if (availableMemory < 100 * 1024 * 1024) {
            System.gc();
            Thread.sleep(500);
        }
    }

    // png 압축 옵션으로 이미지 저장
    private void saveImageWithCompression(BufferedImage image, Path outputPath) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("PNG writer를 찾을 수 없습니다");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputPath.toFile())) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }
    }
}