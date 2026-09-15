package com.grw.xiaobai.hybrid.llm.entity.user;

import lombok.Data;

@Data
public class SessionInfo {
    private String username;
    private Long createTime;
    private Long activateTime;

    public SessionInfo(String username, Long createTime) {
        this.username = username;
        this.createTime = createTime;
        this.activateTime = createTime;
    }

    public void activate() {
        activateTime = System.currentTimeMillis();
    }
}
