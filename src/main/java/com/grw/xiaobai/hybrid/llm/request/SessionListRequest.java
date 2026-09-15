package com.grw.xiaobai.hybrid.llm.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class SessionListRequest implements Serializable {
    private int offset;
    private int limit;
}