package com.pres.pres_server.service.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper delegating triggers to unified NotificationService
 */
@Component
@RequiredArgsConstructor
public class NotificationTriggerService {

    private final NotificationService delegate;

    public void notifyInvite(Long userId, String workspaceName, String invitedBy, String link) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("workspaceName", workspaceName);
        vars.put("invitedBy", invitedBy);
        vars.put("link", link);
        delegate.sendIfAllowed(userId, NotificationType.INVITE, vars);
    }

    public void notifyReviewComment(Long userId, String projectName, String commentAuthor, String link) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("projectName", projectName);
        vars.put("commentAuthor", commentAuthor);
        vars.put("link", link);
        delegate.sendIfAllowed(userId, NotificationType.REVIEW_COMMENT_ADDED, vars);
    }
}
