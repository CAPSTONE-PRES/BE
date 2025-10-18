package com.pres.pres_server.service.file;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.PresentationFile;
import com.pres.pres_server.dto.file.CueBasicDto;
import com.pres.pres_server.dto.file.CueSlideDto;
import com.pres.pres_server.repository.CueCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CueSlideService {

    private final CueCardRepository cueCardRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSlide(PresentationFile file, CueSlideDto dto) {
        int slide = dto.getSlideNumber();
        cueCardRepository.deleteByPresentationFile_FileIdAndSlideNumber(file.getFileId(), slide);

        // BASIC
        if (dto.getBasic() != null) {
            for (CueBasicDto b : dto.getBasic()) {
                if (b == null) continue;
                if (b.getText() == null || b.getText().isBlank()) continue;

                CueCard c = new CueCard();
                c.setPresentationFile(file);
                c.setSlideNumber(slide);
                c.setMode(CueCard.Mode.BASIC);
                c.setSectionNumber(b.getSection());
                c.setSectionKeyword(
                        b.getKeyword() == null || b.getKeyword().isBlank() ? null : b.getKeyword().trim()
                );
                c.setContent(b.getText().trim());
                cueCardRepository.save(c);
            }
        }

        // ADVANCED
        String advText = (dto.getAdvanced() == null || dto.getAdvanced().isBlank())
                ? "(심화버전 없음)" : dto.getAdvanced().trim();
        CueCard adv = new CueCard();
        adv.setPresentationFile(file);
        adv.setSlideNumber(slide);
        adv.setMode(CueCard.Mode.ADVANCED);
        adv.setSectionNumber(0);
        adv.setSectionKeyword(null);
        adv.setContent(advText);
        cueCardRepository.save(adv);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistInsufficient(PresentationFile file, int slideNum) {
        cueCardRepository.deleteByPresentationFile_FileIdAndSlideNumber(file.getFileId(), slideNum);
        CueCard adv = new CueCard();
        adv.setPresentationFile(file);
        adv.setSlideNumber(slideNum);
        adv.setMode(CueCard.Mode.ADVANCED);
        adv.setSectionNumber(0);
        adv.setContent("(내용 부족 – 요약 생략)");
        cueCardRepository.save(adv);
    }

}
