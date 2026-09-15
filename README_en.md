# Hybrid LLM Management System

> ### 🌐 Language / 语言
> **English** | [**中文**](README_zh.md)

[![Java](https://img.shields.io/badge/Java-8%2B-orange?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/downloads/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-Java%20Web-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![Inference](https://img.shields.io/badge/Inference-llama.cpp%20%7C%20OpenAI%20API-blue)](https://github.com/ggml-org/llama.cpp)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey)](#)
[![Release](https://img.shields.io/badge/Release-v1.13-brightgreen)](https://github.com/zhoujianguowei/hybrid-llm-management-system/releases)
[![License](https://img.shields.io/badge/License-Commercial%20%2B%2030%20Days%20Trial-red)](#-how-to-obtain-a-lifetime-license)

**📖 Table of Contents**

- [📋 Project Overview](#-project-overview)
- [🌟 Feature Details](#-feature-details)
- [🏷️ Version Information](#-version-information)
- [❗ Compatibility & Known Limitations](#-compatibility--known-limitations)
- [🚀 Quick Start](#-quick-start)
- [🛠️ Installation Guide](#-installation-guide)
- [👥 User Roles](#-user-roles)
- [🔒 Offline Licensing & Hardware Binding](#-offline-licensing--hardware-binding)
- [❓ FAQ](#-faq)
- [📮 Contact & Support](#-contact--support)

---

## 📋 Project Overview

This system (Hybrid LLM Management System) is a comprehensive management platform for locally deployed large language models, integrating **file management, user permissions, model scheduling, AI chat, and system monitoring** into one secure, efficient, and user-friendly solution.

Built on **Java + Spring Boot** (with Bootstrap 5 on the frontend), the system schedules local GGUF-formatted large language models through **llama.cpp** on the backend, while also supporting **OpenAI-compatible** remote API integration. Its core objective is to address cumbersome path permission management, model parameter configuration, and resource monitoring in local model deployments.

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
| 📊 **Real-Time Resource Monitoring Panel** | Integrated `nvidia-smi` for millisecond-level monitoring, providing dynamic trend charts of GPU VRAM and system memory usage to help administrators accurately assess resource availability before launching models |
| ⏰ **Intelligent Scheduled Shutdown/Reboot** | Supports one-time and periodic (workday-based) scheduled shutdown/wake tasks, with built-in task conflict detection and expiry warnings — ideal for unattended server environments |

![Main Interface](imgs/base_main.png)
![Model Launch Thinking Mode](imgs/model-launch-thinking.png)
![AI Chat Thinking Level Selection](imgs/chat-multi-think-level.png)

---

## 🌟 Feature Details

### 1. Deep Integration of the llama.cpp Inference Engine

**Optionally integrate ik_llama.cpp as an inference engine (not required)**: once its path is configured, you can switch between llama.cpp and ik_llama.cpp when starting a model. The ik_llama.cpp branch performs better in concurrent processing and acceleration of certain quantized models, providing extra flexibility for performance enthusiasts. (Note: automatic GGUF shard merging is still based on llama.cpp under the hood; shards produced by ik_llama.cpp need to be merged manually.)

![Model List](imgs/model-list-overview.png)

- **Multi-GPU Parallel Acceleration**: Full support for llama.cpp **SM Tensor** scheduling for multi-GPU parallel inference.
- **MTP Speculative Acceleration**: Supports **Multi-Token Prediction** speculative decoding, covering DSpark, DFlash, and MTP types, significantly reducing inference steps; configurable speculative decoding parameters such as n-min and draft-ngl; MTP draft files are auto-detected from the mtp/ directory and can also be selected manually; DFlash/DSpark require a `.gguf` draft model
- **Multiple Quantization Formats**: Supports common quantization types such as F32/BF16/F16Q8_0/Q8_0, Q6_K, Q5_K_M, Q4_K_M, IQ3_XXS, and more.
- **Automated GGUF Management**:
  - Automatically recognizes sharded files (e.g., `model-00001-of-00002.gguf`) with one-click merge
  - Built-in naming convention checks keep model file names uniform (format: `model_name_quantization.gguf`)
  - Automatically matches `mmproj` multimodal projection files and `mtp` draft model files
- **Fine-Grained Startup Parameters**: 18+ tunable parameters including context size, GPU offload layers, parallel task count, tensor split, KV cache quantization, temperature, thread count, and batch size, plus `--load-mode` and `--lazy-mode` model load modes (only applicable to llama.cpp, not applicable to ik_llama.cpp).
![GPU Configuration](imgs/model-gpu-config.png)

### 2. Intelligent AI Chat

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
- **Conversation Statistics**: Real-time display of prompt prefill speed, decode speed, current context usage ratio, and other performance metrics.

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

### 6. Real-Time Resource Monitoring

![GPU Monitoring](imgs/model-gpu-overview.png)

- **GPU Monitoring**: Real-time `nvidia-smi` parsing with data synced every 10 seconds and a trend chart of the last 60 samples, giving an at-a-glance view of each GPU's model and VRAM usage.
![Memory Monitoring](imgs/model-mem.png)
- **Memory Monitoring**: Real-time display of total, used, and available memory with overall usage percentage, plus a 3-minute memory usage curve to help analyze memory peaks during model loading.

### 7. Intelligent Scheduled Reboot

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

| Edition | Version Suffix | Description | Included Features |
| :--- | :--- | :--- | :--- |
| **Base Edition** | `release_base` | Minimal deployment version | Core features: file management, user management, AI chat, permission system |
| **Ultimate Edition** | `release_ultimate` | Full-featured version | All Base Edition features + model management, resource monitoring, scheduled shutdown/reboot, and more |

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
| **Scheduled Shutdown/Reboot** | This feature depends on the underlying OS command set and hardware support; not all machines can run it properly. Tested environments: dual X99 (Ubuntu 22.04, E5-2696 v4) and Mac M1 Pro — shutdown/reboot work normally; Windows 10 64-bit (z690 motherboard, i7-13700K) — shutdown works, but automatic wake-up reboot is limited by motherboard BIOS and could not be woken up in self-testing. |
| **GPU Monitoring** | GPU monitoring depends on the `nvidia-smi` tool and **only supports systems with NVIDIA GPUs**. macOS uses Apple Silicon (M series) or integrated graphics with no corresponding monitoring interface, so no GPU monitoring panel is provided on macOS (memory monitoring is unaffected). |
| **Thinking Mode Auto-Detection** | Automatic GGUF thinking-mode detection currently parses the embedded Jinja template via regex matching, so a few models may be detected incorrectly. In that case, thinking levels can be configured manually in the model management settings under system settings in AI chat to override the automatic detection (see [FAQ](#-faq)). |

---

## 🚀 Quick Start

1. **Prepare the environment**: Install JDK 8 or above and download the [llama.cpp](https://github.com/ggml-org/llama.cpp/releases) executable (see the [Installation Guide](#-installation-guide) for details).
2. **Download & run**: Download the [latest release package](https://github.com/zhoujianguowei/hybrid-llm-management-system/releases), extract it, and run the launcher script for your platform.
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

**2. Install llama.cpp**

Download and extract [llama.cpp releases](https://github.com/ggml-org/llama.cpp/releases) (pick the build for your platform). **For Windows systems with NVIDIA GPUs, you must also download the corresponding cudart resources and place them in the extracted directory** (e.g., if using CUDA 13.3, download cudart 13.3).
![llama.cpp and cudart Installation](imgs/install-llama-cpp-cudart.png)

**3. Download & Extract the Release Package**

Download the [latest release package](https://github.com/zhoujianguowei/hybrid-llm-management-system/releases), extract it, and run the corresponding launcher script. JVM parameters can be adjusted as needed.
![JAR Download and Run Script](imgs/install-jar-release.png)
![JAR Startup Configuration](imgs/install-jar-config.png)

**4. Start the Service & Complete Basic Configuration**

Once the JAR has started, open [http://localhost:8098/command/static/file/login.html](http://localhost:8098/command/static/file/login.html) in your browser. Default credentials are `admin` / `admin`; you must change your password after the first login.
![Login Page](imgs/install-login-page.png)

**5. Update License & View the Guide**

After logging in, click the **Update License** button to refresh your machine's authorization code. The question mark button in the toolbar opens the detailed usage guide and feature documentation.
![License Update](imgs/install-license-update.png)
![Help Guide](imgs/install-help-guide.png)

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

**Ultimate Edition v1.1 and above supports automatic detection of locally deployed models — no OpenAI API or model thinking-level parameters need to be configured. If you are using v1.0 or the Base Edition, an OpenAI API must be configured.**
![AI Chat Demo](imgs/install-chat-demo.png)
![AI Deep Thinking Mode](imgs/install-chat-thinking.png)

---

## 👥 User Roles

| Role | Permission Level | Description | Accessible Features |
| :--- | :--- | :--- | :--- |
| **Admin** | Highest (Level 3) | System maintainer | All features: file management, user management, model management, resource monitoring, scheduled shutdown, global configuration |
| **User** | Standard (Level 2) | Registered user | File management (permission-restricted), AI chat, personal settings; accounts have validity periods and are automatically banned upon expiry |
| **Guest** | Temporary (Level 1) | Self-registered account | File management (permission-restricted), AI chat, personal settings; accounts have validity periods and are automatically banned upon expiry |

---

## 🔒 Offline Licensing & Hardware Binding

The Ultimate Edition uses a **pure offline, one-machine-one-code, hardware-bound** activation mechanism. During operation the system is 100% isolated from external networks, with no data upload or backdoor dependencies whatsoever. Core components are protected with industrial-grade deep obfuscation and encryption, ensuring your private assets and code context remain absolutely secure.

**How the machine code is determined:**

- The system's physical fingerprint is computed jointly from your **CPU, motherboard, and operating system**.
- **Worry-Free Hardware Upgrades**: Routine hot-swapping or replacement of peripherals such as the **GPU, hard drive, RAM, or power supply** will **never** invalidate your license.
- **Re-Activation Triggers**: The machine code only changes when the underlying operating system is reinstalled or the core components (CPU/motherboard) are fully replaced.

**Version Compatibility Notes:**

- **Base and Ultimate Edition licenses are not interchangeable**: Base Edition licenses only work with the Base Edition, and Ultimate Edition licenses only work with the Ultimate Edition.
- **Same-Major-Version License Reuse**: License codes within the same major version series are interchangeable. For example, v3.1, v3.2, and v3.53 all belong to the v3 series and their licenses can be used across them.
- **Worry-Free Minor Version Upgrades**: Blocking bug fixes and minor optimizations are released as minor version upgrades; existing licenses remain valid.
- **Higher Version Licenses Are Downward Compatible**: A higher version license can be used with lower versions. For example, an Ultimate Edition v3.53 license is compatible with v3.1, v3.2, and all v2 and v1 series versions.
- **Lower Version Licenses Cannot Be Used with Higher Versions**: For example, a v1 series license code cannot be used on v2 or v3 series software.

### 🛒 How to Obtain a Lifetime License

All release packages come with a built-in **30-day full-feature free trial (Ultimate Edition Trial)**. After the trial expires, you can purchase the lifetime version through the dedicated channels below.

> **🔒 Privacy Guarantee**: Activation is fully offline. The license code is generated entirely from your hardware hash. The system never collects any analytics, telemetry, or user privacy data — use it with confidence.

*Please fill in your **Machine Code** and **email address to receive the license code** in the **Notes/Reference** field on the Wise payment page.*
*(If you forget to fill them in at the time of payment, simply send the payment receipt screenshot together with your machine code to `zhoujianguowei@gmail.com`. The license code will be issued manually within 24 hours.)*

| Software Version | Lifetime Price | Offline Payment Link |
| :--- | :--- | :--- |
| 📦 **Base Edition** | **$9** / Lifetime | [Purchase via Wise](https://wise.com/pay/r/gm5d-VpLW7MCSEw) |
| 👑 **Ultimate Edition** | **$19** / Lifetime | [Purchase via Wise](https://wise.com/pay/r/-5LpLO3Vcn6x4Cc) |

---

## ❓ FAQ

**Q: What is the difference between the Base Edition and the Ultimate Edition?**

A: The Base Edition is a minimal deployment version covering core features such as file management, user management, AI chat, and the permission system. The Ultimate Edition adds advanced features on top, including model management, resource monitoring, and scheduled shutdown/reboot. Licenses for the two editions are not interchangeable — see [Edition Comparison](#edition-comparison).

**Q: Why is there no GPU monitoring panel on macOS?**

A: GPU monitoring depends on the `nvidia-smi` tool and only supports systems with NVIDIA GPUs. macOS uses Apple Silicon (M series) or integrated graphics with no corresponding monitoring interface, so no GPU monitoring panel is provided on macOS (memory monitoring is unaffected).

**Q: Scheduled shutdown works on Windows, but the machine cannot wake up automatically to reboot.**

A: Wake-on-schedule depends on motherboard BIOS hardware support. Some machines (e.g., the self-tested z690 + i7-13700K environment) cannot be woken automatically due to BIOS/hardware limitations; the shutdown function itself is unaffected.

**Q: Will replacing my GPU/hard drive/RAM invalidate my license?**

A: No. The machine code is computed from the CPU, motherboard, and operating system. Routine replacement of peripherals such as the GPU, hard drive, RAM, or power supply does not affect your license; it only changes when the OS is reinstalled or the CPU/motherboard is replaced.

**Q: How long is the trial period, and how do I purchase a lifetime license?**

A: Every release package includes a 30-day full-feature free trial. After the trial expires, you can purchase a lifetime license via the [Wise payment links](#-how-to-obtain-a-lifetime-license) (Base Edition $9 / Ultimate Edition $19). Please include your machine code and receiving email in the payment notes.

**Q: Why does AI chat ask me to configure an OpenAI API?**

A: Ultimate Edition v1.1 and above supports automatic detection of locally deployed models, so no API configuration is needed. v1.0 or the Base Edition requires an OpenAI-compatible API to be configured before AI chat can be used.

**Q: What should I do if a model's thinking mode is detected incorrectly?**

A: Automatic GGUF thinking-mode detection in the Ultimate Edition currently relies on regex matching and may produce incorrect results for some models. In that case, you can override the thinking levels manually via the model management settings under system settings in AI chat (an "override local detection" option is supported).

---

## 📮 Contact & Support

- **Project**: [github.com/zhoujianguowei/hybrid-llm-management-system](https://github.com/zhoujianguowei/hybrid-llm-management-system)
- **Downloads**: [Releases page](https://github.com/zhoujianguowei/hybrid-llm-management-system/releases)
- **Feedback**: Bug reports and feature suggestions are welcome via GitHub Issues
- **Licensing / Purchase Inquiries**: `zhoujianguowei@gmail.com`
