package com.grw.xiaobai.hybrid.llm.entity.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockedNotification {
    private String username;
    private Long requestUnlockDate;
    private String reason;
}
