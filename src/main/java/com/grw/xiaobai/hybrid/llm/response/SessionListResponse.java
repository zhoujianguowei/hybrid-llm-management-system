package com.grw.xiaobai.hybrid.llm.response;

import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSessionSummary;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SessionListResponse implements Serializable {
    private List<ChatSessionSummary> sessions;
    private int total;
    private boolean hasMore;
}