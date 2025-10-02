package com.pres.pres_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pres.pres_server.domain.ExtractedText;

import java.util.Optional;

@Repository
public interface ExtractedTextRepository extends JpaRepository<ExtractedText, Long> {

    Optional<ExtractedText> findByPresentationFile_FileId(Long fileId);

    void deleteByPresentationFile_FileId(Long fileId);

    boolean existsByPresentationFile_FileId(Long fileId);
}