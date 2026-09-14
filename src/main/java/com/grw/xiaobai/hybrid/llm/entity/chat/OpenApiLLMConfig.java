package com.grw.xiaobai.hybrid.llm.entity.chat;

import com.grw.xiaobai.hybrid.llm.exception.InvalidParamException;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class OpenApiLLMConfig implements Serializable, Cloneable {
    private static final Pattern BASE_URL_PATTERN = Pattern.compile("^https?://.+");

    private String id;
    private String baseUrl;
    private String apiKey;
    private Long createTime;
    private Long updateTime;

    public void validate() {
        if (StringUtils.isBlank(baseUrl)) {
            throw InvalidParamException.builder()
                    .msgKey("chat.openapi_url_required")
                    .build();
        }
        Matcher matcher = BASE_URL_PATTERN.matcher(baseUrl);
        if (!matcher.find()) {
            throw InvalidParamException.builder()
                    .msgKey("chat.openapi_url_invalid")
                    .paramErrorDetail(baseUrl)
                    .build();
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
