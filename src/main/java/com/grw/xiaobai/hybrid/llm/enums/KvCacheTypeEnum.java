package com.grw.xiaobai.hybrid.llm.enums;

public enum KvCacheTypeEnum {
    bf16,f16, q8_0, q5_1, q5_0, q4_1, q4_0, iq4_nl;

    public double cacheByte() {
        double result = 2;
        switch (this) {
            case q8_0:
                result = 1;
                break;
            case q5_1:
            case q5_0:
                result = 5.0 / 8;
                break;
            case q4_0:
            case iq4_nl:
            case q4_1:
                result = 0.5;

        }
        return result;
    }
}
