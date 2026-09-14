package com.grw.xiaobai.hybrid.llm.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

public class JsonValidUtil {
    public static boolean isValidJSONObjectType(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        try {
            JSONObject.parseObject(str);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static boolean isValidJSONArrayType(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        try {
            JSONArray.parseArray(str);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
