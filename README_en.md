# Hybrid LLM Management System

> ### 🌐 Language / 语言
> **English** | [**中文**](README_zh.md)

[![Java](https://img.shields.io/badge/Java-8%2B-orange?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/downloads/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-Java%20Web-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![Inference](https://img.shields.io/badge/Inference-llama.cpp%20%7C%20OpenAI%20API-blue)](https://github.com/ggml-org/llama.cpp)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey)](#)
[![Release](https://img.shields.io/badge/Release-v1.13-brightgreen)](https://github.com/zhoujianguowei/hybrid-llm-management-system/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

![Demo](imgs/demo.gif)

> 📺 **Full demo video** (5:25, 720p, ~6.6MB): [demo-preview.mp4](demo-preview.mp4)

**📖 Table of Contents**

- [📋 Project Overview](#-project-overview)
- [🌟 Feature Details](#-feature-details)
- [🏷️ Version Information](#-version-information)
- [❗ Compatibility & Known Limitations](#-compatibility--known-limitations)
- [🚀 Quick Start](#-quick-start)
- [🛠️ Installation Guide](#-installation-guide)
- [🧩 Build & Development](#-build--development)
- [👥 User Roles](#-user-roles)
- [💼 Open-Source Edition vs Ultimate Edition](#-open-source-edition-vs-ultimate-edition)
- [❓ FAQ](#-faq)
- [📮 Contact & Support](#-contact--support)

---

## 📋 Project Overview

This system (Hybrid LLM Management System) is a comprehensive management platform for locally deployed large language models, integrating **file management, user permissions, model scheduling, AI chat, and system monitoring** into one secure, efficient, and user-friendly solution.

> 📌 **This repository is the free open-source edition (Base Edition, Apache-2.0)** of the system, covering file management, the permission system, AI chat, and user management. The Ultimate Edition (closed-source commercial version) adds llama.cpp local model scheduling, thinking-mode auto-detection, resource monitoring, scheduled shutdown/reboot and more — see [Open-Source Edition vs Ultimate Edition](#-open-source-edition-vs-ultimate-edition).

Built on **Java + Spring Boot** (with Bootstrap 5 on the frontend), the system schedules local GGUF-formatted large language models through **llama.cpp** on the backend, while also supporting **OpenAI-compatible** remote API integration. Its core objective is to address cumbersome path permission management, model parameter configuration, and resource monitoring in local model deployments.

> 🤖 **About AI assistance**: The frontend UI code and some supporting scripts of this project were completed with the help of an AI coding assistant, with human review and testing. This follows common open-source practice, and does not affect functionality or maintainability. If you have concerns about AI-generated content, please point out specific issues via Code Review / Issues.

### ✨ Key Highlights

| Feature | Description |
| :--- | :--- |
| 🔥 **Thinking Mode Auto-Detection** | Automatically detects thinking-mode support by recognizing the Jinja template embedded in GGUF files. Thinking level can be flexibly selected at model startup or during chat runtime, with no manual configuration required. Verified models include: unsloth-quantized qwen3.8 series, gemma4, hy3, deepseek-v4-flash-0731, inkling-small, minimax-m3, and other common open-source models |
| ⚡ **Deep llama.cpp Integration** | Full support for multi-GPU parallel acceleration (SM Tensor) and Speculative Decoding, including MTP (Multi-Token Prediction), DFlash, and DSpark types, significantly increasing inference throughput and response speed |
| 🔒 **Fully Offline & Out-of-the-Box** | Frontend resources are 100% localized with zero dependency on external CDNs, ensuring smooth operation even in high-security intranet environments |
| 🚀 **Zero-Config Deployment & Cross-Platform** | Fully compatible with Windows, Linux, and macOS. No database installation or configuration required; deployed as a single JAR package for a true "start-and-run" experience |
| 🛡️ **Fine-Grained Path Permissions** | Path-inheritance based validation mechanism covering five core permissions: Read, Execute, Upload, Download, and Delete. Supports minimum role restrictions and per-user authorization to ensure absolute data security |
| 📂 **Project-Level AI Code Analysis** | Supports bulk file upload with automatic text extraction. Key files from an entire source directory can be submitted to the AI at once for cross-file project analysis and refactoring suggestions |
| ⚙️ **GGUF Automated Lifecycle Management** | Automatically recognizes sharded GGUF files with one-click merge support. Built-in naming convention checks ensure model files are uniform and easy to retrieve or schedule |
| 📊 **Real-Time Resource Monitoring Panel** | Integrated `nvidia-smi` for real-time monitoring, providing dynamic trend charts of GPU VRAM and system memory usage to help administrators accurately assess resource availability before launching models |
| ⏰ **Intelligent Scheduled Shutdown/Reboot** | Supports one-time and periodic (workday-based) scheduled shutdown/wake tasks, with built-in task conflict detection and expiry warnings — ideal for unattended server environments |

![Main Interface](imgs/base_main.png)
![Model Launch Thinking Mode](imgs/model-launch-thinking.png)
![AI Chat Thinking Level Selection](imgs/chat-multi-think-level.png)

---

## 🌟 Feature Details

> 💡 Some screenshots below show the Ultimate Edition UI; the open-source (Base) edition does not include the entry points for features marked [Ultimate] (model management, resource monitoring, scheduled power control).

### 1. Deep Integration of the llama.cpp Inference Engine [Ultimate]

> 💡 The model scheduling capabilities in this section belong to the Ultimate Edition; the open-source edition connects to any deployed OpenAI-compatible inference service (llama.cpp server / Ollama / vLLM, etc.) for AI chat.

**Optionally integrate ik_llama.cpp as an inference engine (not required)**: once its path is configured, you can switch between llama.cpp and ik_llama.cpp when starting a model. The ik_llama.cpp branch performs better in concurrent processing and acceleration of certain quantized models, providing extra flexibility for performance enthusiasts. (Note: automatic GGUF shard merging is still based on llama.cpp under the hood; shards produced by ik_llama.cpp need to be merged manually.)

![Model List](imgs/model-list-overview.png)

- **Multi-GPU Parallel Acceleration**: Full support for llama.cpp **SM Tensor** scheduling for multi-GPU parallel inference.
- **MTP Speculative Acceleration**: Supports **Multi-Token Prediction** speculative decoding, covering DSpark, DFlash, and MTP types, significantly reducing inference steps; configurable speculative decoding parameters such as n-min and draft-ngl; MTP draft files are auto-detected from the mtp/ directory and can also be selected manually; DFlash/DSpark require a `.gguf` draft model
- **Multiple Quantization Formats**: Supports common quantization types such as F32/BF16/F16Q8_0/Q8_0, Q6_K, Q5_K_M, Q4_K_M, IQ3_XXS, and more.
- **Automated GGUF Management**:
  - Automatically recognizes sharded files (e.g., `model-00001-of-00002.gguf`) with one-click merge
  - Built-in naming convention checks keep model file names uniform (format: `model_name_quantization.gguf`)
  - Automatically matches `mmproj` multimodal projection files and `mtp` draft model files
- **Fine-Grained Startup Parameters**: UI settings are automatically mapped to llama.cpp command-line arguments, covering context size, GPU offload layers, parallel task count, tensor split, KV cache quantization, temperature, thread count, batch size, model load modes (`--load-mode` / `--lazy-mode`, only applicable to llama.cpp, not applicable to ik_llama.cpp), and more.
- **Thinking Mode Parameter Mapping**: Thinking mode settings are automatically converted into `--chat-template-kwargs`, sending `enable_thinking` / `reasoning_effort` / `thinking_mode` parameters to the llama.cpp server.

<details>
<summary><b>Supported llama.cpp startup parameters (with ranges and defaults)</b></summary>

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

</details>

<details>
<summary><b>Supported GGUF quantization types</b></summary>

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

</details>

![GPU Configuration](imgs/model-gpu-config.png)

### 2. Intelligent AI Chat

> 💡 The open-source edition requires configuring an OpenAI-compatible API in system settings; the Ultimate Edition automatically detects capabilities and thinking modes of locally scheduled models (Jinja template parsing) with no manual configuration.

![AI Chat](imgs/chat-main.png)

- **Conversation Management**: Supports creating new chats, loading history, deleting sessions, renaming, and pinning frequently used conversations.
- **Multi-Method Attachment Upload**:
  - File upload: supports uploading via copied file paths, as well as images copied to the clipboard; common text formats and image formats are supported
    ![Multi-Method Attachment Upload](imgs/multi-input.png)
  - Folder upload: supports uploading an entire directory listing. If the model supports image input, image files are included; otherwise only plain-text files are included
    ![Project-Level Code Analysis](imgs/chat-project-analysis.png)
- **Code Generation**: AI-generated code blocks support one-click copy, saving to local files, and collapsible display for long code.
- **Message Rendering**: Supports Markdown syntax highlighting, LaTeX formula rendering, and a sandboxed HTML preview pane, with the current model name shown for assistant replies.
- **Deep Thinking Mode**: Supports deep thinking for models such as unsloth-quantized qwen3.8 series, gemma4, hy3, deepseek-v4-flash-0731, inkling-small, and minimax-m3.
- **System-Level Configuration**: Administrators can configure OpenAI API integration, model capability definitions (regex-based thinking mode on/off, with an option to override auto-detected capabilities/thinking modes of local models), and per-role limits on attachment size and maximum message count.
- **Conversation Statistics**: Real-time display of prompt prefill speed, decode speed, cached-token count, current context usage ratio, and other performance metrics (**full statistics are only available for the llama.cpp engine**; other engines currently show only the number of tokens used).

#### System Settings

Administrators open the "System Settings" dialog from the AI chat page, which contains three configuration tabs:

| Tab | Purpose |
|----|----|
| **OpenAI Integration** | Configure OpenAI-compatible API endpoints and keys (with connection testing); the primary model source for the open-source edition |
| **Model Configuration** | Model capability definitions and thinking-mode configuration (see below) |
| **Session Settings** | Per-role quotas such as attachment size and maximum message count to prevent resource abuse |

#### Model Capability Definition & Thinking Mode

The "Model Configuration" tab defines multimodal capabilities, thinking modes, and visibility for models matching specific names:

- **Match Type**: Supports **exact match** and **regex match** on model names, so one rule can cover a single model or a whole family (e.g. `qwen3\.8.*`).
- **Multimodal Capabilities**: Declare image / video / audio input support per model, which drives the attachment entry in chat (in the current version, multimodal capability is actually limited to images).
- **Visibility**: A model can be visible to all users, normal users and above, or administrators only.
- **Thinking Mode**: Choose one of the four modes for models with "deep thinking" support, and select the available levels:

| Mode | Parameters | Description | Examples |
|----|----|----|----|
| Boolean thinking | `enable_thinking` / `thinking` | Boolean switch only; on/off without intensity levels | qwen3.5 / qwen3.6 |
| Multi-stage thinking | `reasoning_effort` | Single parameter controls both switch and intensity (`no_think` / `low` / `medium` / `high` / `xhigh` / `max`) | hy3 |
| Hybrid mode | `enable_thinking` + `reasoning_effort` | Boolean parameter controls the switch; `reasoning_effort` controls intensity | qwen3.8, deepseek-v4-flash |
| Thinking mode | `thinking_mode` | Tri-state `disabled` / `adaptive` / `enabled`; highest detection priority in GGUF metadata | minimax-m3 |

- **GGUF Auto-Detection (Ultimate Edition)**: When a local GGUF model starts, the system parses chat-template metadata to detect `thinking_mode` / `enable_thinking` / `reasoning_effort` parameters and fills the configuration automatically, so manual definitions are usually unnecessary.
- **Override Auto-Detection**: This switch decides the precedence against auto-detection — when enabled, this definition forcibly overrides the auto-detected capabilities/thinking configuration; when disabled, auto-detected local models follow the detection result (visibility always applies). The open-source edition has no local auto-detection, so capability definitions take effect directly.
- **Parameter Mapping**: Thinking-mode settings are automatically converted into llama.cpp `--chat-template-kwargs` parameters in requests (e.g. `enable_thinking=true,reasoning_effort="high"`).

For more screenshots and details, see the in-app usage guide (linked from the login page).

### 3. File Management

![File Manager Main Interface](imgs/file-manager-main.png)

- **Smart Upload**: Supports drag-and-drop file upload and chunked upload for large files, with real-time progress display for each chunk and the overall percentage in the right task bar.
![File Upload Progress](imgs/file-upload-progress.png)
- **Flexible Download**: Supports downloading files or folders. Individual files download directly, while folders are automatically packaged into `.zip` archives in the background; compression progress is viewable in the download task list.
- **Multimedia Preview**: Supports online preview of multiple formats without download.
  - Images (JPG, PNG): full-screen view support
  - Video: Range request support, freely draggable progress bar
  - Documents (PDF): online preview support
  - Text/Code: preview support for common text formats including TXT, Java, Python, JS, HTML, JSON, YAML, etc.
- **Search**: Supports fuzzy filename matching with wildcards (e.g., `*.py`) for quick filtering of specific file types.
- **View Switching**: Toggle between list view (detailed properties) and tile view (suitable for image browsing).
- **Secure Deletion**: Deletion requires double confirmation, strict `DELETE` permission validation; protected paths cannot be deleted even by administrators.

### 4. Fine-Grained Permission System

![Permission Tree Management](imgs/permission-tree.png)

- **Path Inheritance Mechanism**: When accessing a file with no matching rule, the system automatically traces upward to parent directories until the nearest matching rule is found.
![Permission Edit](imgs/permission-edit.png)
- **Permission Cascade (Priority)**: **Delete > Upload > Download > Execute > Read**; higher-level permissions automatically include lower-level ones.
- **Dual Role & User Validation**: Each permission can be configured with a minimum allowed role, and also supports an additional list of specifically authorized users.
- **Path Penetration**: Even without execute permission on a parent directory, a file can be accessed directly as long as you hold read permission on the file itself.
- **Permission Tree Management**: Visual tree structure for a clear view of the system-wide permission distribution; quickly locate and modify permissions of specific subdirectories.
- **Path Protection Mechanism**: Protected paths display a special marker in the file manager UI, and all deletion requests are blocked outright.

### 5. Full User Lifecycle Management

![User Management](imgs/user-list.png)

- **Role Management**: Supports three roles — Admin, User, and Guest — with role switching at any time.
- **Status Control**: One-click ban/unban. When a banned user attempts to log in, an unban request workflow is triggered for the administrator to handle in real time.
- **Guest Validity Period**: Configurable default validity period (in days) for guest accounts; the system periodically scans and automatically bans expired accounts.
- **Self-Service Registration**: Once guest registration is enabled, users can register on the login page and receive a preset validity period automatically.

### 6. Real-Time Resource Monitoring [Ultimate]

![GPU Monitoring](imgs/model-gpu-overview.png)

- **GPU Monitoring**: Real-time `nvidia-smi` parsing with data synced every 10 seconds and a trend chart of the last 60 samples, giving an at-a-glance view of each GPU's model and VRAM usage.
![Memory Monitoring](imgs/model-mem.png)
- **Memory Monitoring**: Real-time display of total, used, and available memory with overall usage percentage, plus a 3-minute memory usage curve to help analyze memory peaks during model loading.

### 7. Intelligent Scheduled Reboot [Ultimate]

![Scheduled Reboot](imgs/reboot-add-detailed.png)

- **Dual-Mode Tasks**:
  - One-Time Tasks: automatically deleted after execution, suitable for temporary maintenance and single experiments
  - Periodic Tasks: configurable workdays with automatic next-cycle calculation upon expiry, suitable for daily scheduled shutdown/wake
- **Task Management**: View all created tasks including execution time, status (pending/running/expired), and real-time countdown; supports deletion or postponement.
- **Expiry Warning**: When a task is less than 10 minutes from execution, a countdown warning card pops up at the top of the page.
![Reboot Warning](imgs/reboot-warning.png)

---

## 🏷 Version Information

### Edition Comparison

> 💡 **Starting with v1.13 (release_base), the Base edition is officially free and open source, with no license or expiration restrictions.** Users who previously purchased Base Edition licenses for earlier versions are unaffected and may keep using them; Base v1.13 and later are free to use at no cost.

| Edition | Availability | Description | Included Features |
| :--- | :--- | :--- | :--- |
| **Open-Source Edition (Base)** | This repository / `release_base` packages | Free & open source, Apache-2.0 | Core features: file management, user management, AI chat (via OpenAI-compatible API), permission system |
| **Ultimate Edition** | `release_ultimate` packages | Closed-source commercial edition, one-time purchase | All open-source features + llama.cpp local model scheduling, thinking-mode auto-detection, resource monitoring, scheduled shutdown/reboot, and more |

### Version History

| Version | Changes |
| :--- | :--- |
| **v1.13** | 1. Added --load-mode and --lazy-mode launch parameters (only applicable to llama.cpp, not applicable to ik_llama.cpp)<br />2. Speculative Decoding enhancements: added n-min and draft-ngl parameters; MTP draft file supports manual selection (auto-detects the mtp/ directory when left blank); DFlash/DSpark require a .gguf draft with real-time validation and file existence check before launch<br />3. AI chat enhancements: model feature configuration can override the auto-detected capabilities/thinking modes of local models; relaxed thinking level validation (a non-thinking level is no longer required); hover tooltips added to the thinking mode dropdown; new sandboxed HTML preview pane, LaTeX formula rendering, and the model name is now shown in message rendering<br />4. Fixed issues including model restart port reuse, hardened model stop flow, session titles overwritten by auto-titling, missing per-turn speed display, scroll jitter on session switch, message rendering wrongly splitting prose as lists, chunked upload interrupted by file changes, and large directory deletion timeout<br />5. Extended login session lifetime from 2 to 12 hours and WebSocket idle timeout from 1 to 10 hours |
| **v1.12** | 1. Added llama.cpp Speculative Decoding support covering DSpark, DFlash, and MTP types, with configurable n-max and p-min parameters<br />2. AI Chat enhancements: Markdown syntax highlighting, toggle for including thinking content in OpenAI requests, and runtime modification of temperature, top-p, and other parameters<br />3. Enhanced OpenAI request compatibility: expanded from llama.cpp-only to mainstream engines such as Ollama, kTransformer, and sglang (detailed data such as decode/prefill speed is currently only available for llama.cpp)<br />4. Fixed known issues including file list upload and AI chat special character escaping errors |
| **v1.11** | 1. Fixed a model thinking-mode auto-detection bug and added auto-detection support for inkling-small, minimax-m3, and deepseek-v4-flash-0731 models<br />2. Fixed a model parameter import bug and code rendering issues on the AI chat page<br />3. UI display optimization for the AI chat page |
| **v1.1** | 1. Ultimate Edition AI chat supports automatic detection of local models with no manual API configuration; thinking level can be selected at model startup or during conversation (self-tested with unsloth's qwen3.6, gemma4, deepseek-v4-flash, hy3, and other models)<br />2. Added English language support with Chinese/English UI switching<br />3. Added more llama.cpp parameter support, including primary GPU, repeat-penalty, top-k, top-p, etc.<br />4. Fixed various bugs, including abnormal AI chat window closure prompts and path search popup height issues |
| **v1.0** | Initial release with file management, AI chat, user management, model management, and scheduled shutdown/reboot features |

> 💡 Only major versions are listed above; see [Releases](https://github.com/zhoujianguowei/hybrid-llm-management-system/releases) for the complete changelog.

---

## ❗ Compatibility & Known Limitations

| Item | Description |
| :--- | :--- |
| **Multimodal Capability** | The current version's multimodal capability **only supports image input**; audio and video input are not supported. |
| **Conversation Statistics** | Full conversation statistics (prefill / decode speed, cached tokens, etc.) are **only available for models launched with the llama.cpp engine**; other inference engines (Ollama, vLLM, sglang, etc.) currently show only the number of tokens used. |
| **Scheduled Shutdown/Reboot** | This feature depends on the underlying OS command set and hardware support; not all machines can run it properly. Tested environments: dual X99 (Ubuntu 22.04, E5-2696 v4) and Mac M1 Pro — shutdown/reboot work normally; Windows 10 64-bit (z690 motherboard, i7-13700K) — shutdown works, but automatic wake-up reboot is limited by motherboard BIOS and could not be woken up in self-testing. |
| **GPU Monitoring** | GPU monitoring depends on the `nvidia-smi` tool and **only supports systems with NVIDIA GPUs**. macOS uses Apple Silicon (M series) or integrated graphics with no corresponding monitoring interface, so no GPU monitoring panel is provided on macOS (memory monitoring is unaffected). |
| **Thinking Mode Auto-Detection** | Automatic GGUF thinking-mode detection currently parses the embedded Jinja template via regex matching, so a few models may be detected incorrectly. In that case, thinking levels can be configured manually in the model configuration tab under system settings in AI chat to override the automatic detection (see [FAQ](#-faq)). |
| **Dependency & Security Baseline** | To keep JDK 8 compatibility, current dependencies are pinned to Spring Boot 2.1.x / fastjson 1.2.83 and related versions; for known dependency risks, security recommendations (default credentials, Swagger switch, etc.) and the upgrade plan, see [SECURITY.md](SECURITY.md) before deploying to production. |

---

## 🚀 Quick Start

1. **Prepare the environment**: Install JDK 8 or above (the Ultimate Edition additionally requires the [llama.cpp](https://github.com/ggml-org/llama.cpp/releases) executable — see the [Installation Guide](#-installation-guide)).
2. **Download & run**: Download the [latest release package](https://github.com/zhoujianguowei/hybrid-llm-management-system/releases) (the open-source edition has no license restrictions) or [build from source](#-build--development); extract it and run the launcher script for your platform.
3. **Log in & configure**: Open [http://localhost:8098/command/static/file/login.html](http://localhost:8098/command/static/file/login.html) in your browser. Default credentials are `admin` / `admin`; please change them after the first login.

---

## 🛠 Installation Guide

> The complete installation flow below is demonstrated on **Windows**; Linux / macOS steps are similar.

### Official Test Environment Reference

- **OS**: Windows 10 64-bit / Ubuntu 22.04 LTS / macOS (M1 Pro)
- **CPU**: Intel i7-13700K / Dual Xeon E5-2696 v4
- **GPU**: NVIDIA RTX 2080 Ti (11GB/22GB Modified)
- **RAM**: 32GB / 128GB DDR4

### Installation Steps (Windows Demo)

**1. Install JDK**

Install [JDK 8 or above](https://www.oracle.com/java/technologies/downloads/#java8) (JDK 8 is recommended, as it has been tested across multiple platforms) and add the Java executable path to your system environment variables. On Linux or macOS, add the Java bin path to your PATH.
![JDK Environment Configuration](imgs/install-jdk-env.png)

**2. Install llama.cpp (Ultimate only)**

Not required for the open-source edition. Download and extract [llama.cpp releases](https://github.com/ggml-org/llama.cpp/releases) (pick the build for your platform). **For Windows systems with NVIDIA GPUs, you must also download the corresponding cudart resources and place them in the extracted directory** (e.g., if using CUDA 13.3, download cudart 13.3).
![llama.cpp and cudart Installation](imgs/install-llama-cpp-cudart.png)

**3. Download & Extract the Release Package**

Download the [latest release package](https://github.com/zhoujianguowei/hybrid-llm-management-system/releases), extract it, and run the corresponding launcher script. JVM parameters can be adjusted as needed.
![JAR Download and Run Script](imgs/install-jar-release.png)
![JAR Startup Configuration](imgs/install-jar-config.png)

**4. Start the Service & Complete Basic Configuration**

Once the JAR has started, open [http://localhost:8098/command/static/file/login.html](http://localhost:8098/command/static/file/login.html) in your browser. Default credentials are `admin` / `admin`; you must change your password after the first login.
![Login Page](imgs/install-login-page.png)

**5. View the Usage Guide**

After logging in, the question mark button in the toolbar opens the detailed usage guide and feature documentation. The open-source edition requires no license and can be used directly; for Ultimate Edition licensing, see [Open-Source Edition vs Ultimate Edition](#-open-source-edition-vs-ultimate-edition).
![Help Guide](imgs/install-help-guide.png)

Steps 6 ~ 8 below configure local model scheduling and apply to the Ultimate Edition only; for the open-source edition, simply configure an OpenAI-compatible API in the AI chat system settings instead.

**6. Configure Model Management**

On first entry to the model management page, fill in the directory containing GGUF model files and the directory of the llama.cpp executable; the system will then automatically detect and list available models. The **Model Naming And Shard Merge** button shows the detailed GGUF directory layout and naming conventions, and supports automatic shard detection and merging.
![Model Management Configuration](imgs/install-model-config.png)
![Model Naming and Shard Merge](imgs/install-model-naming-merge.png)

**7. Configure GPU Monitoring (Optional, NVIDIA GPUs Only)**

Use `where nvidia-smi` (Windows) or `which nvidia-smi` (Linux/macOS) to find the system path of `nvidia-smi`, then add it on the model settings page. GPU information will be visible once configured.
![Configure nvidia-smi Path](imgs/install-nvidia-smi-path.png)
![GPU Information Display](imgs/install-gpu-info.png)
![GPU Monitoring Panel](imgs/install-gpu-monitor.png)

**8. Start a Model**

![Model Startup](imgs/install-model-start.png)
![Model Running](imgs/install-model-running.png)

**9. Start AI Chat**

**Ultimate Edition v1.1 and above supports automatic detection of locally deployed models — no OpenAI API or model thinking-level parameters need to be configured. For the open-source edition, configure an OpenAI-compatible API (e.g., a locally running llama.cpp server or Ollama) in system settings.**
![AI Chat Demo](imgs/install-chat-demo.png)
![AI Deep Thinking Mode](imgs/install-chat-thinking.png)

---

## 🧩 Build & Development

### Requirements

- JDK 8 or above (JDK 8 / 11 recommended)
- Gradle: not required — use the Wrapper shipped with this repository (`./gradlew` / `gradlew.bat`)

### Build from Source

```bash
# Compile and produce the executable jar: build/libs/hybridLLM-v1.13_release_base.jar
./gradlew bootJar

# Package release archives (jar + launcher scripts):
# base-hybridLLM-v1.13-windows-amd64.zip / linux-amd64.tar.gz / darwin-arm64.tar.gz
./gradlew buildJar

# Run
java -jar build/libs/hybridLLM-v1.13_release_base.jar
```

Then open `http://localhost:8098/command/static/file/login.html` in your browser. Default credentials are `admin` / `admin` (**change them immediately after first login**).

### Key Configuration

Configuration lives in `src/main/resources/application.yml` (the `sit` profile is active by default):

| Property | Default | Description |
| :--- | :--- | :--- |
| `server.port` | `8098` | Service port |
| `server.servlet.context-path` | `/command` | Context path |
| `spring.servlet.multipart.max-file-size` | `200MB` | Max upload size per file |
| `swagger.enable` | `true` | Swagger docs switch, **recommended `false` in production** |
| `llm.version` | `v1.13_release_base` | Version identifier (fixed to `release_base` for the open-source edition) |

Running-time settings such as file storage directories, AI feature definitions, and attachment size limits are maintained in the system settings page after login (persisted to the data directory; no external database required).

### Project Structure

```
src/main/java/com/grw/xiaobai/hybrid/llm/
├── controller/   REST API layer
├── service/      business interfaces + impl
├── manager/      in-memory config/state (sessions, permission tree, upload tasks...)
├── entity/       domain POJOs (chat/file/model/user/...)
├── config/       Spring configuration (MVC, thread pool, Swagger...)
├── task/         scheduled tasks
└── utils/        utilities
src/main/resources/static/        frontend static assets (vanilla JS + Bootstrap 5, no Node build chain)
```

Layering: `controller → service → manager → entity`. The system has no database — user/permission/task state is persisted to files on disk and kept in memory at runtime.

### More Development Docs

- Contributing guide & code conventions: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security recommendations & known items: [SECURITY.md](SECURITY.md)
- Release changelog: [CHANGELOG.md](CHANGELOG.md)
- Third-party components & licenses: [NOTICE](NOTICE)

---

## 👥 User Roles

| Role | Permission Level | Description | Accessible Features |
| :--- | :--- | :--- | :--- |
| **Admin** | Highest (Level 3) | System maintainer | All features: file management, user management, model management, resource monitoring, scheduled shutdown, global configuration |
| **User** | Standard (Level 2) | Registered user | File management (permission-restricted), AI chat, personal settings; accounts have validity periods and are automatically banned upon expiry |
| **Guest** | Temporary (Level 1) | Self-registered account | File management (permission-restricted), AI chat, personal settings; accounts have validity periods and are automatically banned upon expiry |

---

## 💼 Open-Source Edition vs Ultimate Edition

> Note: **Starting with the v1.13 release_base, the Base edition is officially free and open source — no license and no expiration.** Existing Base Edition licenses for historical versions remain valid; v1.13+ can be used directly with no license required.

This repository is the **free open-source edition (Base)** of the system, released under the **Apache-2.0 license**: free for commercial use and derivative works (please keep the original LICENSE and NOTICE attribution; the project name and logo are not licensed).

The **Ultimate Edition** is the closed-source commercial version. It adds llama.cpp local model scheduling, thinking-mode auto-detection, resource monitoring and scheduled shutdown/reboot on top of the open-source edition, with ongoing updates and support.

**Ultimate Edition licensing & purchase:**

- Ultimate Edition releases include a built-in **30-day full-feature free trial**; after the trial you can buy a one-time, lifetime license.
- The Ultimate Edition uses a **fully offline, one-machine-one-code** mechanism. The machine code is computed from your CPU / motherboard / operating system; replacing the GPU, hard drive or RAM does not invalidate your license; full licensing rules are described in the Ultimate Edition release package.
- Please fill in your **Machine Code** and **email address to receive the license code** in the **Notes/Reference** field on the Wise payment page. If you forget, send your payment receipt together with the machine code to `zhoujianguowei@gmail.com` (manual issuance within 24 hours).

| Software Version | Lifetime Price | Offline Payment Link |
| :--- | :--- | :--- |
| 👑 **Ultimate Edition** | **$19** / Lifetime | [Purchase via Wise](https://wise.com/pay/r/-5LpLO3Vcn6x4Cc) |

> **🔒 Privacy Guarantee**: During operation the system is 100% isolated from external networks with no telemetry or data collection; the open-source edition's code is fully public and can be audited at any time.

---

## ❓ FAQ

**Q: What is the difference between the open-source edition and the Ultimate Edition?**

A: The open-source edition is this repository, licensed free under Apache-2.0, covering file management, user management, AI chat (via an OpenAI-compatible API), and the permission system. The Ultimate Edition adds llama.cpp local model scheduling, thinking-mode auto-detection, resource monitoring, and scheduled shutdown/reboot, and is a closed-source one-time-purchase product — see [Open-Source Edition vs Ultimate Edition](#-open-source-edition-vs-ultimate-edition).

**Q: Why is there no GPU monitoring panel on macOS?**

A: GPU monitoring depends on the `nvidia-smi` tool and only supports systems with NVIDIA GPUs. macOS uses Apple Silicon (M series) or integrated graphics with no corresponding monitoring interface, so no GPU monitoring panel is provided on macOS (memory monitoring is unaffected).

**Q: Scheduled shutdown works on Windows, but the machine cannot wake up automatically to reboot.**

A: Wake-on-schedule depends on motherboard BIOS hardware support. Some machines (e.g., the self-tested z690 + i7-13700K environment) cannot be woken automatically due to BIOS/hardware limitations; the shutdown function itself is unaffected.

**Q: (Ultimate) Will replacing my GPU/hard drive/RAM invalidate my license?**

A: No. The machine code is computed from the CPU, motherboard, and operating system. Routine replacement of peripherals such as the GPU, hard drive, RAM, or power supply does not affect your license; it only changes when the OS is reinstalled or the CPU/motherboard is replaced.

**Q: How long is the trial period, and how do I purchase the Ultimate Edition?**

A: Ultimate Edition releases include a 30-day full-feature free trial. After the trial expires, you can purchase a lifetime license via the [Wise payment link](#-open-source-edition-vs-ultimate-edition) ($19). Please include your machine code and receiving email in the payment notes.

**Q: Why does AI chat ask me to configure an OpenAI API?**

A: The open-source edition uses AI chat as a standard API client: configure an OpenAI-compatible API in system settings (it can be a locally running llama.cpp server, Ollama, etc.). Ultimate Edition v1.1 and above automatically detects locally scheduled models, so no manual API configuration is needed in that scenario.

**Q: What should I do if a model's thinking mode is detected incorrectly?**

A: Automatic GGUF thinking-mode detection in the Ultimate Edition currently relies on regex matching and may produce incorrect results for some models. In that case, you can override the thinking levels manually via the model configuration tab under system settings in AI chat (an "Override Auto-Detection" option is supported).

---

## 📮 Contact & Support

- **Project**: [github.com/zhoujianguowei/hybrid-llm-management-system](https://github.com/zhoujianguowei/hybrid-llm-management-system)
- **Downloads**: [Releases page](https://github.com/zhoujianguowei/hybrid-llm-management-system/releases)
- **Feedback**: Bug reports and feature suggestions are welcome via GitHub Issues
- **Licensing / Purchase Inquiries**: `zhoujianguowei@gmail.com`
