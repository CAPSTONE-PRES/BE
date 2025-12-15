package com.pres.pres_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationsUpdateDto {
    private Boolean emailEnabled;
    private Boolean workspaceActivityEnabled;
    private Boolean inviteEnabled;
    private Boolean reviewCommentEnabled;
    private Boolean practiceReminderEnabled;
    private Boolean practiceRemindD1;
    private Boolean practiceRemindD2;
}
