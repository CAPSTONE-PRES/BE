package com.pres.pres_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationsDto {
    private boolean emailEnabled;
    private boolean workspaceActivityEnabled;
    private boolean inviteEnabled;
    private boolean reviewCommentEnabled;
    private boolean practiceReminderEnabled;
    private boolean practiceRemindD1;
    private boolean practiceRemindD2;
}
