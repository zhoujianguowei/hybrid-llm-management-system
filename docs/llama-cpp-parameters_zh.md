# llama.cpp 启动参数参考（中文版）

本文列出系统 UI 配置映射到 llama.cpp 的全部启动参数（含范围与默认值），与系统版本: v1.13 行为一致，并与应用内使用指南保持同步。

## 支持的 llama.cpp 启动参数一览（含范围与默认值）

**一、文本采样参数**（作用于生成下一个 Token 的概率分布）

| UI 配置项 | llama.cpp 参数 | 范围 / 默认值 | 说明 |
|--------|------------|----------|----|
| 温度 | `--temp` | 0.00–2.00，默认 0.80 | 越低越确定，越高越发散；0 为贪婪解码 |
| Top K | `--top-k` | -1–1024，默认 40 | 仅保留概率前 K 个候选；-1 禁用 |
| Top P | `--top-p` | 0.00–1.00，默认 0.95 | 核采样，按累积概率截断；1.0 禁用 |
| Min P | `--min-p` | 0.00–1.00，默认 0.05 | 相对最高概率 Token 的比例阈值，可替代 Top P |
| 重复惩罚 | `--repeat-penalty` | 0.10–2.00，默认 1.00 | 对已出现 Token 打折；过高会导致语言破碎 |
| 重复最后 N | `--repeat-last-n` | 0/16/32/64/128/256/512/1024/-1，默认 64 | 重复惩罚追溯窗口；-1 为整个上下文 |
| 存在惩罚 | `--presence-penalty` | -2.00–2.00，默认 0.00 | 出现过即惩罚，鼓励转换话题 |
| 频率惩罚 | `--frequency-penalty` | -2.00–2.00，默认 0.00 | 按出现次数成比例惩罚，抑制口头禅 |

**二、硬件与系统调度参数**

| UI 配置项 | llama.cpp 参数 | 范围 / 默认值 | 说明 |
|--------|------------|----------|----|
| 上下文大小 | `-c` | 正整数 | KV Cache 长度，越长越占内存 |
| GPU 加载层数 | `-ngl` | ≥0 | 0 表示纯 CPU 推理 |
| 生成线程 | `--threads` | 正整数 | Decoding 阶段 CPU 线程数，建议不超过单颗 CPU 物理核数 |
| 批处理线程 | `--threads-batch` | 正整数 | Prefill 阶段 CPU 线程数，可设更高 |
| 逻辑批处理 | `-b` | 正整数 | Prefill 单次并行 Token 上限，增大加快长 Prompt 但增加显存峰值 |
| 物理批处理 | `-ub` | 正整数 ≤ `-b` | 微批次大小，防大批量计算显存溢出 |
| GPU 选择 | `selectedGpuIds` | 逗号分隔 GPU 序号 | 指定权重卸载到哪些 GPU（异构多卡精细切分） |
| 张量分割 | `--tensor-split` | 比例列表 | 多 GPU 显存分配比例 |
| 主 GPU | `CUDA_VISIBLE_DEVICES` 重排 | GPU 序号 | 主卡自动置于逻辑设备 0，无需 `-mg` |
| 张量并行 | `-sm tensor/graph` | tensor / graph | SM Tensor 多卡并行加速 |
| 模型加载模式 | `--load-mode` | 默认 mmap+mlock | mmap+mlock / mlock / mmap / auto / none / dio（仅 llama.cpp；ik_llama.cpp 固定 `--mlock`） |
| 懒加载模式 | `--lazy-mode` | 默认 off | off / auto（仅 >4GiB 张量按需读盘）/ on（需加载模式为 auto 或 mmap；仅 llama.cpp） |

**三、预测推理（Speculative Decoding）参数**

| UI 配置项 | llama.cpp 参数 | 范围 / 默认值 | 说明 |
|--------|------------|----------|----|
| 类型 | `--spec-type` | MTP / DFlash / DSpark | llama.cpp 用 `draft-<类型>`；ik_llama.cpp 用 `<类型>:n_max=N,n_min=N,p_min=P` 内联语法 |
| n-max | `--spec-draft-n-max` | ≥1，默认 3 | 每次 Draft 预测的 Token 数 |
| n-min | `--spec-draft-n-min` | 0–n-max，默认 0 | 每步 Draft 最少生成 Token 数 |
| p-min | `--spec-draft-p-min` | 0.00–1.00，默认 0.00 | 最小接受概率，平衡加速与质量 |
| draft-ngl | `--gpu-layers-draft` | 默认 auto | Draft 模型 GPU 层数；llama.cpp 支持 数值/auto/all，ik_llama.cpp 仅数值 |
| Draft 模型 | `--model-draft` | 文件路径 | DFlash/DSpark 必填（不存在则拒绝启动）；MTP 自动探测 `mtp/` 目录 |

**四、缓存与状态 / 其他服务参数**

| UI 配置项 | llama.cpp 参数 | 范围 / 默认值 | 说明 |
|--------|------------|----------|----|
| 缓存大小 | `--cache-ram` | MiB | KV Cache 容量上限，防 MoE 长文本缓存膨胀 |
| KV Cache 量化 | `-ctk` / `-ctv` | f16 / q8_0 / q4_0 等 | 量化 K/V 缓存省显存，轻微影响精度 |
| 检查点步长 | `--checkpoint-min-step` | 正整数 | KV 检查点最小间隔步长 |
| 检查点数量 | `--ctx-checkpoints` | 正整数 | 每 slot 最大检查点数 |
| 端口号 | `--port` | 默认 8080 | 模型服务监听端口 |
| 并行任务数 | `--parallel` | 正整数 | 并发请求数，增加吞吐与内存占用 |
| Jinja 模板 | `--jinja` | 开关 | 解析 Jinja2 聊天模板（Qwen、Llama 3 等需要） |
| MOE 层数 | `--n-cpu-moe` | 非负整数 | CPU 加载的 MoE 专家层数 |

**五、思考模式参数映射**（`--chat-template-kwargs` 自动下发）

思考等级（自低到高）：`no_think` / `low` / `medium` / `high` / `xhigh` / `max`；`thinking_mode` 模式使用三态 `disabled` / `adaptive` / `enabled`。

| 模式 | 关闭时发送 | 强度档发送 | 适用示例 |
|----|--------|--------|----|
| 单独思考模式 | `enable_thinking=false`（或 `thinking=false`） | `enable_thinking=true` | qwen3.5 / qwen3.6 |
| 多阶段思考模式 | `reasoning_effort="no_think"` | `reasoning_effort="high"` | hy3 |
| 混合模式 | `enable_thinking=false` | `enable_thinking=true` + `reasoning_effort="high"` | qwen3.8、deepseek-v4-flash |
| Thinking 模式 | `thinking_mode="disabled"` | `thinking_mode="adaptive"/"enabled"` | minimax-m3 |

> 默认等级差异：模型启动配置页默认取最高等级（思考能力最大化）；聊天页默认取最低等级（兼顾响应速度）。


---

## 支持的 GGUF 量化类型

| 精度等级 | 支持的量化格式 | 推荐场景 |
|----|----|----|
| 高精度 | `F32` `BF16` `F16` `IQ8` | 最高精度推理，占用显存最大 |
| Q8 | `Q8_0` `Q8_K` `Q8_K_P` `UD_Q8_K_XL` | 接近 FP16 精度，显存减半 |
| Q6 | `Q6_K` `Q6_K_L` `Q6_K_P` `UD_Q6_K` `UD_Q6_K_XL` | 精度与显存的平衡选择 |
| Q5 | `Q5_0` `Q5_1` `Q5_K_S` `Q5_K_M` `Q5_K_P` 等 | 日常推理推荐，精度损失极小 |
| Q4 | `Q4_0` `Q4_1` `Q4_K_S` `Q4_K_M` `IQ4_NL` `IQ4_XS` `IQ4_KS` 等 | 显存受限场景首选，性价比高 |
| Q3 | `Q3_K_S` `Q3_K_M` `IQ3_XXS` `IQ3_XS` `IQ3_S` `IQ3_M` 等 | 极端显存受限场景 |
| Q2 | `Q2_K` `Q2_K_S` `Q2_K_L` `IQ2_XXS` `IQ2_XS` `IQ2_S` `IQ2_M` 等 | 极限压缩，精度损失较大 |
| IQ1/TQ | `IQ1_S` `IQ1_M` `TQ1_0` `TQ2_0` | 实验性极低比特量化 |
| MoE | `MXFP4_MOE` | 混合专家模型专用量化 |

