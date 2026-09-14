package com.grw.xiaobai.hybrid.llm.request;

import com.grw.xiaobai.hybrid.llm.entity.chat.ChatMediaText;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CopyUserMessageRequest implements Serializable {
    private String content;
    private List<ChatMediaText> chatMediaTextList;
}
