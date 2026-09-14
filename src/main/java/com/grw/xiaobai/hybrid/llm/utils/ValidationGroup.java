package com.grw.xiaobai.hybrid.llm.utils;

import javax.validation.groups.Default;

/**
 * 校验分组，定义常见的业务操作增删改查分组
 */
public class ValidationGroup {

    public interface Insert extends Default {
    }

    public interface Delete extends Default {
    }

    public interface Update extends Default {
    }

    public interface Select extends Default {
    }

    public interface InsertOrUpdate extends Default {
    }
}
