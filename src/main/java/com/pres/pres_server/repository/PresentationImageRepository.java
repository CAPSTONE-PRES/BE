package com.pres.pres_server.repository;

import com.pres.pres_server.domain.PresentationImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PresentationImageRepository extends JpaRepository<PresentationImage, Long> {

    List<PresentationImage> findByFile_FileIdOrderByPageNumberAsc(Long fileId);

    void deleteByFile_FileId(Long fileId);


}
