package com.grw.xiaobai.hybrid.llm.entity.chat;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMediaText implements Serializable {
    private String url;
    private String name;
    private String relativePath;
}
