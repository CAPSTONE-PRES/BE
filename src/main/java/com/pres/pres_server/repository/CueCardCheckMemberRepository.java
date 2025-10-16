package com.pres.pres_server.repository;

import com.pres.pres_server.domain.CueCard;
import com.pres.pres_server.domain.CueCardCheckMember;
import com.pres.pres_server.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CueCardCheckMemberRepository extends JpaRepository<CueCardCheckMember, Long> {
    Optional<CueCardCheckMember> findByCueCardAndUser(CueCard cueCard, User user);
    List<CueCardCheckMember> findByCueCard(CueCard cueCard);
    List<CueCardCheckMember> findByCueCardInAndCheckedTrue(List<CueCard> cueCards);
    List<CueCardCheckMember> findByUserAndCueCardIn(User user, List<CueCard> cueCards);
}
