package com.grw.xiaobai.hybrid.llm.enums;

public enum QuantizationTypeEnum {
    UD_IQ2_XXS,        // 新增
    UD_IQ3_XXS,        // 新增
    UD_Q2_K_XL,        // 33
    UD_Q3_K_XL,        // 34
    UD_Q4_K_XL,        // 35
    UD_Q5_K_XL,        // 新增
    UD_Q6_K_XL,        // 38
    UD_Q8_K_XL,        // 39

    MXFP4_MOE,         // 新增
    UD_IQ4_NL,         // 新增
    UD_IQ4_XS,         // 新增
    UD_Q3_K_M,         // 新增
    UD_Q3_K_S,         // 新增
    UD_Q4_K_M,         // 新增
    UD_Q4_K_S,         // 新增
    UD_Q5_K_M,         // 新增
    UD_Q5_K_S,         // 新增

    IQ4_KM_L,          // 新增
    UD_IQ1_M,          // 新增
    UD_IQ1_S,          // 新增
    UD_IQ2_M,          // 新增
    UD_IQ3_S,          // 新增

    IQ2_KXL,           // 新增
    IQ2_XXS,           // 19
    IQ3_KXL,           // 新增
    IQ3_XXS,           // 23
    IQ4_KXL,           // 新增
    Q3_K_XL,           // 新增
    UD_Q6_K,           // 新增

    IQ2_KL,            // (原始项)
    IQ2_KM,            // 新增
    IQ2_KS,            // (原始项)
    IQ2_XS,            // 20
    IQ3_KL,            // 新增
    IQ3_KM,            // 新增
    IQ3_KS,            // (原始项)
    IQ3_XS,            // 22
    IQ4_KL,            // 新增
    IQ4_KM,            // 新增
    IQ4_KS,            // (原始项)
    IQ4_NL,            // 25
    IQ4_XS,            // 30
    IQ5_KS,
    Q2_K_L,            // 新增
    Q2_K_S,            // 21
    Q3_K_L,            // 13
    Q3_K_M,            // 12
    Q3_K_S,            // 11
    Q4_K_L,            // 新增
    Q4_K_M,            // 15
    Q4_K_P,
    Q4_K_S,            // 14
    Q5_K_M,            // 17
    Q5_K_P,
    Q5_K_S,            // 16
    Q6_K_L,            // 新增
    Q6_K_P,
    Q8_K_P,

    IQ1_M,             // 31
    IQ1_S,             // 24
    IQ2_M,             // 29
    IQ2_S,             // 28
    IQ3_M,             // 27
    IQ3_S,             // 26
    IQ4_K,
    IQ5_K,
    TQ1_0,             // 36
    TQ2_0,             // 37

    BF16,              // 32
    Q2_K,              // 10
    Q3_K,              // 12 (别名: Q3_K_M)
    Q4_0,              // 2
    Q4_1,              // 3
    Q4_K,              // 15 (别名: Q4_K_M)
    Q5_0,              // 8
    Q5_1,              // 9
    Q5_K,              // 17 (别名: Q5_K_M)
    Q6_K,              // 18
    Q8_0,              // 7
    Q8_K,              // 新增

    F16,               // 1
    F32,               // 0
    IQ8                // 新增
}
