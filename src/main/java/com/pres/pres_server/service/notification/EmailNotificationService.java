package com.pres.pres_server.service.notification;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 이메일 템플릿 구성 및 실제 발송 책임.
 * - 정책 판단은 하지 않음(오케스트레이터가 판단한 후 호출되어야 함)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    public void send(Long userId, NotificationType type, Map<String, Object> vars) {
        try {
            Optional<User> opt = userRepository.findById(userId);
            if (opt.isEmpty()) {
                log.warn("No user found for id={}, skip send {}", userId, type);
                return;
            }
            String to = opt.get().getEmail();
            if (to == null || to.isBlank()) {
                log.warn("No email for userId={}, skip notification {}", userId, type);
                return;
            }

            String subject = buildSubject(type);
            String body = buildBody(type, vars);

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent: userId={} type={}", userId, type);
        } catch (Exception e) {
            log.warn("Failed to send notification {} to userId={}: {}", type, userId, e.getMessage());
        }
    }

    private String buildSubject(NotificationType type) {
        return switch (type) {
            case WORKSPACE_ACTIVITY -> "워크스페이스 활동 알림";
            case INVITE -> "워크스페이스 초대 알림";
            case REVIEW_COMMENT -> "새로운 검토 의견 알림";
            case PRACTICE_REMINDER_D1 -> "발표 연습 D-1 알림";
            case PRACTICE_REMINDER_D2 -> "발표 연습 D-2 알림";
        };
    }

    private String buildBody(NotificationType type, Map<String, Object> vars) {
        String projectName = safe(vars.get("projectName"));
        String workspaceName = safe(vars.get("workspaceName"));
        String commentAuthor = safe(vars.get("commentAuthor"));
        String link = safe(vars.get("link"));
        Object dueObj = vars.get("dueDate");
        String dueDate = dueObj == null ? "" : dueObj.toString();

        return switch (type) {
            case WORKSPACE_ACTIVITY -> String.format("[%s] 워크스페이스에 활동이 있습니다. 자세히: %s", workspaceName, link);
            case INVITE -> String.format("[%s] 님이 당신을 워크스페이스에 초대했습니다. 초대 링크: %s", workspaceName, link);
            case REVIEW_COMMENT ->
                String.format("[%s]님이 프로젝트 '%s'에 새 검토의견을 남겼습니다. 확인: %s", commentAuthor, projectName, link);
            case PRACTICE_REMINDER_D1 -> String.format("발표가 내일입니다: %s (%s). 연습해보세요: %s", projectName, dueDate, link);
            case PRACTICE_REMINDER_D2 -> String.format("발표가 모레입니다: %s (%s). 연습해보세요: %s", projectName, dueDate, link);
        };
    }

    private String safe(Object o) {
        return o == null ? "" : o.toString();
    }
}
