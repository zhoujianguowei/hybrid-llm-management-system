package com.grw.xiaobai.hybrid.llm.response;

import lombok.Data;

@Data
public class OpenApiModelTestResp {
    /**
     * 错误信息，不为空表示请求异常
     */
    private String errorMsg;
    /**
     * 可用模型列表
     */
    private java.util.List<String> modelNames;
}
