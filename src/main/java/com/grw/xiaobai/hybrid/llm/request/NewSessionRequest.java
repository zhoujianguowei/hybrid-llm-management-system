package com.grw.xiaobai.hybrid.llm.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class NewSessionRequest implements Serializable {
    private String apiConfigId;
    private String apiConfigName;
    private String modelName;
}