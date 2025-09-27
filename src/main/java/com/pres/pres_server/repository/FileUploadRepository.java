package com.pres.pres_server.repository;

import com.pres.pres_server.domain.PresentationFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileUploadRepository extends JpaRepository<PresentationFile, Long> {
    // 파일 ID로 파일 정보 조회
    PresentationFile findById(long id);

    

}
