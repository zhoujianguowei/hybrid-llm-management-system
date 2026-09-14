package com.grw.xiaobai.hybrid.llm.entity.user;

import java.util.List;

import com.grw.xiaobai.hybrid.llm.entity.file.FileInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    private ChatContext chatContext;
    private FileManagerContext fileManagerContext;

    @Data
    public static class ChatContext {
        private String lastOpenedChatId;
        private Boolean lastPinnedSessionOpenedStatus = true;
        private Boolean sidebarCollapsed = false;
        private List<String> activeChatIdList;
        private Integer sessionRetentionDays = 365;
        private String sendShortcutKey = "ctrl+enter";
        private boolean attachReasoningContent = false;
        private String systemPrompt;
    }

    @Data
    public static class FileManagerContext {
        private FileInfo lastVisitedFileDir;
    }
}