# llama.cpp Startup Parameters Reference

> 🌐 **English** | [中文](llama-cpp-parameters_zh.md)

This document lists every system UI setting mapped to llama.cpp startup arguments (with ranges and defaults), consistent with v1.13 and kept in sync with the in-app usage guide.

## Supported llama.cpp startup parameters (with ranges and defaults)

> Full explanations and screenshots are available in the in-app usage guide (linked from the login page), section "llama.cpp User Guide".

#### 1. Sampling parameters (shape the next-token probability distribution)

| UI Setting | llama.cpp Argument | Range / Default | Description |
|--------|------------|----------|----|
| Temperature | `--temp` | 0.00-2.00, default 0.80 | Lower = deterministic, higher = creative; 0 = greedy decoding |
| Top K | `--top-k` | -1-1024, default 40 | Keep only top-K candidates; -1 disables |
| Top P | `--top-p` | 0.00-1.00, default 0.95 | Nucleus sampling by cumulative probability; 1.0 disables |
| Min P | `--min-p` | 0.00-1.00, default 0.05 | Relative threshold vs. the max-probability token; replaces Top P in many setups |
| Repeat penalty | `--repeat-penalty` | 0.10-2.00, default 1.00 | Discount already-generated tokens; too high breaks fluency |
| Repeat last N | `--repeat-last-n` | 0/16/32/64/128/256/512/1024/-1, default 64 | Lookback window for repeat penalty; -1 = whole context |
| Presence penalty | `--presence-penalty` | -2.00-2.00, default 0.00 | Flat penalty for seen tokens, encourages new topics |
| Frequency penalty | `--frequency-penalty` | -2.00-2.00, default 0.00 | Penalty proportional to occurrence count |

#### 2. Hardware & scheduling parameters

| UI Setting | llama.cpp Argument | Range / Default | Description |
|--------|------------|----------|----|
| Context size | `-c` | positive int | KV cache length; longer uses more memory |
| GPU layers | `-ngl` | >=0 | 0 = CPU-only inference |
| Threads | `--threads` | positive int | CPU threads for decoding; keep <= physical cores of one CPU |
| Batch threads | `--threads-batch` | positive int | CPU threads for prefill; can be set higher |
| Batch size | `-b` | positive int | Logical prefill batch size; larger speeds up long prompts but raises peak memory |
| UBatch size | `-ub` | positive int <= `-b` | Micro-batch size, avoids VRAM OOM |
| GPU selection | `selectedGpuIds` | comma-separated GPU ids | Choose which GPUs hold offloaded weights (heterogeneous multi-GPU) |
| Tensor split | `--tensor-split` | ratio list | Per-GPU memory allocation ratio |
| Main GPU | `CUDA_VISIBLE_DEVICES` reorder | GPU id | Main GPU becomes logical device 0, no `-mg` needed |
| Tensor parallelism | `-sm tensor/graph` | tensor / graph | SM Tensor multi-GPU acceleration |
| Load mode | `--load-mode` | default mmap+mlock | mmap+mlock / mlock / mmap / auto / none / dio (llama.cpp only; ik_llama.cpp always uses `--mlock`) |
| Lazy mode | `--lazy-mode` | default off | off / auto (only tensors >4 GiB read on demand) / on (requires auto or mmap load mode; llama.cpp only) |

#### 3. Speculative decoding parameters

| UI Setting | llama.cpp Argument | Range / Default | Description |
|--------|------------|----------|----|
| Type | `--spec-type` | MTP / DFlash / DSpark | llama.cpp uses `draft-<type>`; ik_llama.cpp uses inline `<type>:n_max=N,n_min=N,p_min=P` |
| n-max | `--spec-draft-n-max` | >=1, default 3 | Tokens drafted per step |
| n-min | `--spec-draft-n-min` | 0 to n-max, default 0 | Minimum drafted tokens per step |
| p-min | `--spec-draft-p-min` | 0.00-1.00, default 0.00 | Minimum acceptance probability, balances speed vs. quality |
| draft-ngl | `--gpu-layers-draft` | default auto | GPU layers for the draft model; llama.cpp: value/auto/all, ik_llama.cpp: value only |
| Draft model | `--model-draft` | file path | Required for DFlash/DSpark (launch rejected if missing); MTP auto-detects the `mtp/` directory |

#### 4. Cache & state / other service parameters

| UI Setting | llama.cpp Argument | Range / Default | Description |
|--------|------------|----------|----|
| Cache size | `--cache-ram` | MiB | KV cache limit, prevents bloat on long-context MoE models |
| KV cache quantization | `-ctk` / `-ctv` | f16 / q8_0 / q4_0, etc. | Quantized K/V cache saves memory with slight accuracy cost |
| Checkpoint step | `--checkpoint-min-step` | positive int | Minimum interval between KV state checkpoints |
| Checkpoints | `--ctx-checkpoints` | positive int | Max checkpoints per slot |
| Port | `--port` | default 8080 | Model service port |
| Parallel | `--parallel` | positive int | Concurrent request count; raises throughput and memory use |
| Jinja | `--jinja` | switch | Enable Jinja2 chat template parsing (required by Qwen, Llama 3, etc.) |
| MOE layers | `--n-cpu-moe` | non-negative int | Number of MoE expert layers loaded to CPU |

#### 5. Thinking-mode parameter mapping (`--chat-template-kwargs`, sent automatically)

Thinking levels (low to high): `no_think` / `low` / `medium` / `high` / `xhigh` / `max`; the `thinking_mode` mode uses the tri-state `disabled` / `adaptive` / `enabled`.

| Mode | Sent when off | Sent at strength level | Examples |
|----|--------|--------|----|
| Boolean thinking | `enable_thinking=false` (or `thinking=false`) | `enable_thinking=true` | qwen3.5 / qwen3.6 |
| Multi-stage thinking | `reasoning_effort="no_think"` | `reasoning_effort="high"` | hy3 |
| Hybrid mode | `enable_thinking=false` | `enable_thinking=true` + `reasoning_effort="high"` | qwen3.8, deepseek-v4-flash |
| Thinking mode | `thinking_mode="disabled"` | `thinking_mode="adaptive"/"enabled"` | minimax-m3 |

> Default level differs by page: model launch config defaults to the highest level (maximum thinking); the chat page defaults to the lowest level (response speed).


---

## Supported GGUF quantization types

| Precision Tier | Supported Formats | Recommended Scenario |
|----|----|----|
| High precision | `F32` `BF16` `F16` `IQ8` | Maximum precision, largest VRAM footprint |
| Q8 | `Q8_0` `Q8_K` `Q8_K_P` `UD_Q8_K_XL` | Near-FP16 accuracy with half the memory |
| Q6 | `Q6_K` `Q6_K_L` `Q6_K_P` `UD_Q6_K` `UD_Q6_K_XL` | Balanced accuracy and memory |
| Q5 | `Q5_0` `Q5_1` `Q5_K_S` `Q5_K_M` `Q5_K_P`, etc. | Recommended for daily use, minimal accuracy loss |
| Q4 | `Q4_0` `Q4_1` `Q4_K_S` `Q4_K_M` `IQ4_NL` `IQ4_XS` `IQ4_KS`, etc. | Best for memory-constrained setups, great value |
| Q3 | `Q3_K_S` `Q3_K_M` `IQ3_XXS` `IQ3_XS` `IQ3_S` `IQ3_M`, etc. | Extreme memory constraints |
| Q2 | `Q2_K` `Q2_K_S` `Q2_K_L` `IQ2_XXS` `IQ2_XS` `IQ2_S` `IQ2_M`, etc. | Extreme compression, notable accuracy loss |
| IQ1/TQ | `IQ1_S` `IQ1_M` `TQ1_0` `TQ2_0` | Experimental ultra-low-bit quantization |
| MoE | `MXFP4_MOE` | Dedicated quantization for mixture-of-experts models |

