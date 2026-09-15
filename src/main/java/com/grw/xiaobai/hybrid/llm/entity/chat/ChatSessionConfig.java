package com.grw.xiaobai.hybrid.llm.entity.chat;

import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import java.io.Serializable;
import lombok.Data;

@Data
public class ChatSessionConfig implements Serializable {
    private String id;
    private UserRoleEnum role;
    private int maxAttachmentSize;
    private int maxTextAttachmentSize;
    private int maxTextAttachments;
    private int maxAttachments;
    private int maxMessageCount;
    private int pinnedSessionLimit;
    private Long createTime;
    private Long updateTime;
}
