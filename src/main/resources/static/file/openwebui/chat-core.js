const wsMap = new Map();
const thinkingStateMap = new Map();
let currentChatSessionId = null;
let selectedApiConfigId = null;
let selectedChatModel = null;
let currentTotalTokens = 0;
let currentPromptPerSecond = null;
let currentPredictedPerSecond = null;
let currentMediaList = [];
let currentTextFileList = [];
let sessionConfig = null;
let sessionConfigLoaded = false;
let currentMessageElement = null;
let currentAssistantMessage = '';
let currentThinkingContent = null;
let lastUserMessage = '';
let sessionOffset = 0;
let isRecovering = false;
const SESSION_PAGE_SIZE = 20;
let chatModels = [];
let apiConfigs = [];
let sessions = [];
let pinnedSessions = [];
let pinnedOffset = 0;
const PINNED_PAGE_SIZE = 10;
let pinnedExpanded = true;
let autoScrollDisabled = false;
let isGenerating = false;
let isAdmin = false;
let thinkingEnabled = false;
let currentThinkingLevel = 'no_think';

function getChatThinkingLevel() {
    return currentThinkingLevel;
}
const sessionThinkingState = new Map();
const sessionAttachReasoningState = new Map();
// maxTokens 为 0 表示采用服务端默认值（请求不携带 max_tokens）
const RUNTIME_PARAM_DEFAULTS = { temperature: 0.8, topP: 1, maxTokens: 0, presencePenalty: 0, frequencyPenalty: 0 };
const RUNTIME_PARAM_RANGES = {
    temperature: { min: 0, max: 2 },
    topP: { min: 0, max: 1 },
    presencePenalty: { min: -2, max: 2 },
    frequencyPenalty: { min: -2, max: 2 }
};
const RUNTIME_PARAM_ELEMENTS = {
    temperature: { range: 'runtimeTemperatureRange', value: 'runtimeTemperatureValue' },
    topP: { range: 'runtimeTopPRange', value: 'runtimeTopPValue' },
    maxTokens: { range: 'runtimeMaxTokensRange', value: 'runtimeMaxTokensValue' },
    presencePenalty: { range: 'runtimePresencePenaltyRange', value: 'runtimePresencePenaltyValue' },
    frequencyPenalty: { range: 'runtimeFrequencyPenaltyRange', value: 'runtimeFrequencyPenaltyValue' }
};
const sessionRuntimeConfigState = new Map();
let runtimePersistTimer = null;
const UserRoleEnum = { admin: 'admin', user: 'user', guest: 'guest' };
let skipAutoScroll = false;
let sessionExpiryHandled = false;
let switchingSessionChatId = null; // Track which chatId is being intentionally switched
let uploadPendingCount = 0;
let allMessages = [];
let renderedMessageStart = 0;
let messageScrollLoading = false;
// Incremented on every session switch/new chat. renderMessages binds its
// scroll-retry loop to the value captured at render time and stops as soon as
// this counter moves, so switching sessions can never leave two retry loops
// racing scrollToBottom on the same container (the source of switch-time shake).
let sessionRenderToken = 0;
const MESSAGE_RENDER_BATCH = 20;
const MESSAGE_SENTINAL_OFFSET = 10;
let messageSentinelObserver = null;
const LOAD_THRESHOLD_BYTES = 30 * 1024;
const CODE_EXT_MAP = {
    javascript: 'js', typescript: 'ts', python: 'py', java: 'java',
    c: 'c', cpp: 'cpp', 'c++': 'cpp', csharp: 'cs', 'c#': 'cs',
    go: 'go', rust: 'rs', ruby: 'rb', php: 'php', swift: 'swift',
    kotlin: 'kt', scala: 'scala', r: 'r', matlab: 'm',
    shell: 'sh', bash: 'sh', powershell: 'ps1',
    html: 'html', xml: 'xml', svg: 'svg', json: 'json', yaml: 'yaml', yml: 'yaml',
    css: 'css', scss: 'scss', less: 'less', sql: 'sql',
    markdown: 'md', latex: 'tex', diff: 'diff',
    vue: 'vue', svelte: 'svelte',
    dockerfile: 'Dockerfile', makefile: 'Makefile',
    ini: 'ini', toml: 'toml',
    graphql: 'graphql', proto: 'proto',
    perl: 'pl', lua: 'lua', haskell: 'hs', elixir: 'ex',
    clojure: 'clj', fsharp: 'fs', 'f#': 'fs',
};

if (!sessionId) {
    window.location.href = STATIC_BASE + '/login.html';
}

// 实时读取当前 session，避免页面加载时的内存旧值在重新登录后失效
function getCurrentSessionId() {
    return localStorage.getItem('sessionId') || sessionId;
}

function getSessionParam() {
    return 'sessionId=' + encodeURIComponent(getCurrentSessionId());
}

async function api(path, options = {}) {
    const headers = options.headers || {};
    const url = path + (path.includes('?') ? '&' : '?') + getSessionParam();
    const response = await fetch(API_BASE + url, {...options, headers});
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
        const data = await response.json();
        if (data.code === 401) {
            if (typeof showDialog === 'function') {
                showDialog({ type: 'info', title: t('chat.login_expired_title'), message: t('chat.login_expired_message'), buttons: [], duration: 2000 }).then(function() { window.location.href = STATIC_BASE + '/login.html'; }).catch(function() { window.location.href = STATIC_BASE + '/login.html'; });
            } else {
                showAlert(t('chat.login_expired_alert'), 'error');
                setTimeout(() => window.location.href = STATIC_BASE + '/login.html', 1500);
            }
            throw new Error('Session expired');
        }
        return data;
    } else {
        throw new Error('Expected JSON response');
    }
}

function showAlert(message, type, persistent) {
    if (persistent) {
        const dialogType = type === 'error' ? 'error' : (type === 'warning' ? 'warn' : 'info');
        showDialog({ type: dialogType, message: message, buttons: [{ label: t('dialog.confirm'), value: 'confirm', primary: true }] });
    } else {
        // 简单提示统一走 toast 样式
        showToast(message, type === 'warning' ? 'warn' : type, 5000);
    }
}

function connectWebSocket(chatId, sendCheckRecovery = false) {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${location.host}${API_BASE}/ws/chat?${getSessionParam()}&chatId=${chatId}`;

    const ws = new WebSocket(wsUrl);
    ws.binaryType = 'arraybuffer';
    if (ws.extensions) {
        ws.extensions = 'permessage-deflate; maxFrameSize=1048576';
    }

    ws.onopen = () => {
        console.log('WebSocket connected for chatId:', chatId);
        sessionExpiryHandled = false;
        const currentWs = wsMap.get(chatId);
        if (currentWs === ws) {
            currentWs.reconnectCount = 0;
            currentWs.reconnectTimer = null;
        }
        if (switchingSessionChatId === chatId) switchingSessionChatId = null;
        if (sendCheckRecovery) {
            ws.send(JSON.stringify({ type: 'check_recovery', sessionId: getCurrentSessionId(), chatId: chatId }));
        }
    };

    ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        handleMessage(data, chatId);
    };

    ws.onerror = (error) => {
        if (ws.pageIsUnloading || ws.readyState === WebSocket.CLOSING || ws.readyState === WebSocket.CLOSED) {
            return;
        }
        console.error('WebSocket error:', error);
    };

    ws.onclose = (event) => {
        console.log('WebSocket closed for chatId:', chatId, 'code:', event.code);
        const currentWs = wsMap.get(chatId);
        if (currentWs !== ws) {
            console.log('Connection replaced for chatId:', chatId);
            return;
        }
        if (event.code === 1008) {
            if (!sessionExpiryHandled) {
                showAlert(t('chat.session_expired_refresh'), 'info');
                setTimeout(() => {
                    window.location.reload();
                }, 2000);
            }
        } else if (event.code === 1001) {
            console.log(`Connection replaced for ${chatId}`);
            if (!sessionExpiryHandled && !ws.pageIsUnloading && switchingSessionChatId !== chatId && chatId === currentChatSessionId) {
                showAlert(t('chat.session_other_window'), 'info', true);
            }
            wsMap.delete(chatId);
        } else {
            console.log(`Connection closed for ${chatId}, code: ${event.code}`);
            if (!sessionExpiryHandled && !ws.pageIsUnloading && switchingSessionChatId !== chatId) {
                if (shouldReconnectAfterClose(chatId, event.code, ws)) {
                    scheduleWebSocketReconnect(chatId, ws);
                } else if (chatId === currentChatSessionId) {
                    showAlert(t('chat.connection_disconnected'), 'info', true);
                }
            }
            wsMap.delete(chatId);
        }
    };

    ws.reconnectCount = 0;
    wsMap.set(chatId, ws);
    return ws;
}

function getCurrentWs() {
    return wsMap.get(currentChatSessionId);
}

function waitForWebSocket(chatId, timeout = 3000) {
    return new Promise((resolve) => {
        const ws = wsMap.get(chatId);
        if (!ws) {
            resolve();
            return;
        }
        if (ws.readyState === WebSocket.OPEN) {
            resolve();
            return;
        }
        const timeoutId = setTimeout(resolve, timeout);
        ws.addEventListener('open', () => {
            clearTimeout(timeoutId);
            resolve();
        }, { once: true });
    });
}

function shouldReconnectAfterClose(chatId, code, ws) {
    if ((code !== 1000 && code !== 1006) || ws.reconnectTimer || ws.pageIsUnloading) {
        return false;
    }
    if ((ws.reconnectCount || 0) >= 3) {
        return false;
    }
    const session = sessions.find(s => s.chatId === chatId);
    return isGenerating || (session && session.isGenerating);
}

function scheduleWebSocketReconnect(chatId, ws) {
    const delay = Math.min(1000 * Math.pow(2, ws.reconnectCount || 0), 5000);
    ws.reconnectTimer = setTimeout(() => {
        ws.reconnectTimer = null;
        // 用户已切换会话或连接已被替换时不再重连，避免产生孤儿 socket
        if (wsMap.get(chatId) !== ws) return;
        const nextWs = connectWebSocket(chatId, true);
        nextWs.reconnectCount = (ws.reconnectCount || 0) + 1;
    }, delay);
}

let scrollTimeout = null;
let scrollPauseTimeout = null;
let autoScrollDisableTimer = null;
// scrollTop position set by our own scrollToBottom(); used to recognize and
// ignore the scroll event our programmatic scroll triggers, so content that
// keeps growing (e.g. streaming code blocks) can't unpin auto-follow.
let programmaticScrollTop = null;
let programmaticScrollTimer = null;

function isAtBottom() {
    const container = document.getElementById('messagesContainer');
    const threshold = 100;
    return container.scrollHeight - container.scrollTop - container.clientHeight < threshold;
}

function disableAutoScroll() {
    autoScrollDisabled = true;
    if (autoScrollDisableTimer) clearTimeout(autoScrollDisableTimer);
    autoScrollDisableTimer = setTimeout(() => {
        autoScrollDisableTimer = null;
        // Re-pin whenever the view is back at the bottom. Gating this on
        // !isGenerating left auto-follow stuck off for the whole rest of a
        // generation once it was disabled (a wheel over a streaming code block
        // disables it), so the streaming tail never resumed following.
        if (isAtBottom()) autoScrollDisabled = false;
    }, 5000);
}

// Re-pin auto-follow when the view is back at the bottom; unpin when it has
// left the bottom (user scrolled up).
function syncAutoScrollState() {
    if (isAtBottom()) {
        autoScrollDisabled = false;
    } else {
        disableAutoScroll();
    }
}

// Force re-pin; used when a session is loaded / cleared and the view is
// reset to the bottom.
function resetAutoScroll() {
    autoScrollDisabled = false;
    if (autoScrollDisableTimer) {
        clearTimeout(autoScrollDisableTimer);
        autoScrollDisableTimer = null;
    }
}

function shouldAutoScroll() {
    if (skipAutoScroll || autoScrollDisabled) {
        return false;
    }
    return true;
}

function scrollToBottom() {
    const container = document.getElementById('messagesContainer');
    const prev = container.scrollTop;
    container.scrollTop = container.scrollHeight;
    if (container.scrollTop === prev) {
        // Already at the bottom: no scroll event will fire, so no guard is
        // needed — clearing it immediately keeps real user scrolls responsive.
        if (programmaticScrollTimer) clearTimeout(programmaticScrollTimer);
        programmaticScrollTimer = null;
        programmaticScrollTop = null;
        return;
    }
    programmaticScrollTop = container.scrollTop;
    if (programmaticScrollTimer) clearTimeout(programmaticScrollTimer);
    programmaticScrollTimer = setTimeout(() => {
        programmaticScrollTimer = null;
        programmaticScrollTop = null;
    }, 200);
}

function scrollToBottomIfNeeded() {
    if (shouldAutoScroll()) {
        scrollToBottom();
    }
}

function formatTime(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now - date;
    
    if (diff < 60000) return t('chat.time_just_now');
    if (diff < 3600000) return t('chat.time_minutes_ago', { minutes: Math.floor(diff / 60000) });
    if (diff < 86400000) return t('chat.time_hours_ago', { hours: Math.floor(diff / 3600000) });
    if (diff < 604800000) return t('chat.time_days_ago', { days: Math.floor(diff / 86400000) });
    
    return date.toLocaleDateString();
}

function formatDuration(ms) {
    if (ms >= 60000) {
        const minutes = Math.floor(ms / 60000);
        const seconds = Math.floor((ms % 60000) / 1000);
        return seconds > 0 ? `${minutes}${t('chat.duration_min')}${seconds}${t('chat.duration_sec')}` : `${minutes}${t('chat.duration_min')}`;
    } else if (ms >= 1000) {
        return (ms / 1000).toFixed(1) + t('chat.duration_sec');
    } else {
        return ms + 'ms';
    }
}

function formatTokenCount(count) {
    if (count >= 1000000) {
        return (count / 1000000).toFixed(1) + 'M';
    } else if (count >= 1000) {
        return (count / 1000).toFixed(1) + 'K';
    }
    return count.toString();
}

function updateSendButtonState() {
    const btnSend = document.querySelector('.btn-send');
    if (isGenerating) {
        btnSend.classList.add('generating');
    } else {
        btnSend.classList.remove('generating');
    }
}

function getThinkingLevelLabel(level) {
    return typeof window.thinkingLevelLabel === 'function' ? window.thinkingLevelLabel(level) : level;
}

function onThinkingDropdownChange(level) {
    currentThinkingLevel = level;
    thinkingEnabled = level !== 'no_think' && level !== 'none' && level !== 'false' && level !== 'disabled';
    var levelSel = document.getElementById('thinkLevelSelect');
    if (levelSel && typeof window.thinkingLevelColor === 'function') {
        levelSel.style.color = window.thinkingLevelColor(level);
    }
    sessionThinkingState.set(currentChatSessionId, currentThinkingLevel || 'no_think');
    api('/chat/sessions/thinking-mode', {
        method: 'POST',
        body: new URLSearchParams({ chatId: currentChatSessionId, thinkingMode: currentThinkingLevel })
    }).catch(function() {});
}

function getSessionAttachReasoningValue(chatId) {
    chatId = chatId || currentChatSessionId;
    const override = sessionAttachReasoningState.get(chatId);
    return override != null ? override : !!window.attachReasoningContent;
}

function toggleAttachReasoning() {
    if (!currentChatSessionId) {
        showToast(t('chat.session_required_first'), 'warn');
        return;
    }
    const newVal = !getSessionAttachReasoningValue();
    sessionAttachReasoningState.set(currentChatSessionId, newVal);
    updateAttachReasoningButton();
    persistSessionRuntimeConfig();
}

function restoreSessionAttachReasoning(chatId, chatRuntimeConfig) {
    const value = chatRuntimeConfig && chatRuntimeConfig.attachReasoningContent;
    if (value != null) {
        sessionAttachReasoningState.set(chatId, value);
    } else {
        sessionAttachReasoningState.delete(chatId);
    }
}

function updateAttachReasoningButton() {
    const btn = document.getElementById('btnAttachReasoning');
    if (!btn) return;
    btn.classList.toggle('active', getSessionAttachReasoningValue());
}

function getRuntimeConfigState(chatId) {
    if (!chatId) return null;
    let state = sessionRuntimeConfigState.get(chatId);
    if (!state) {
        state = { systemPrompt: null, temperature: null, topP: null, maxTokens: 0, presencePenalty: null, frequencyPenalty: null };
        sessionRuntimeConfigState.set(chatId, state);
    }
    return state;
}

function getFullRuntimeConfig(chatId) {
    chatId = chatId || currentChatSessionId;
    const config = { attachReasoningContent: getSessionAttachReasoningValue(chatId) };
    const state = getRuntimeConfigState(chatId);
    if (state) {
        config.systemPrompt = state.systemPrompt || null;
        config.temperature = state.temperature;
        config.topP = state.topP;
        config.maxTokens = state.maxTokens;
        config.presencePenalty = state.presencePenalty;
        config.frequencyPenalty = state.frequencyPenalty;
    }
    return config;
}

function buildRequestRuntimeConfig() {
    const config = getFullRuntimeConfig();
    if (!config.systemPrompt && window.userSystemPrompt) {
        config.systemPrompt = window.userSystemPrompt;
    }
    // 未设置的采样参数按当前生效的默认值填充，保证请求始终携带侧边栏展示的值
    config.temperature = config.temperature != null ? config.temperature : RUNTIME_PARAM_DEFAULTS.temperature;
    config.topP = config.topP != null ? config.topP : RUNTIME_PARAM_DEFAULTS.topP;
    config.presencePenalty = config.presencePenalty != null ? config.presencePenalty : RUNTIME_PARAM_DEFAULTS.presencePenalty;
    config.frequencyPenalty = config.frequencyPenalty != null ? config.frequencyPenalty : RUNTIME_PARAM_DEFAULTS.frequencyPenalty;
    if (config.maxTokens != null && config.maxTokens > 0) {
        config.maxTokens = Math.min(Math.max(1, Math.round(config.maxTokens)), getRuntimeMaxContext());
    } else {
        // 0/null 表示采用服务端默认值：后端对 0 不设置 max_tokens
        config.maxTokens = 0;
    }
    return config;
}

function persistSessionRuntimeConfig(chatId) {
    chatId = chatId || currentChatSessionId;
    if (!chatId) return Promise.resolve();
    return api('/chat/sessions/runtime-config?chatId=' + encodeURIComponent(chatId), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(getFullRuntimeConfig(chatId))
    }).catch(function() {});
}

function scheduleRuntimePersist() {
    const chatId = currentChatSessionId;
    if (!chatId) return;
    if (runtimePersistTimer) clearTimeout(runtimePersistTimer);
    runtimePersistTimer = setTimeout(function() {
        runtimePersistTimer = null;
        // 定时器按发起时的会话持久化，避免切换会话后误写其他会话的配置
        persistSessionRuntimeConfig(chatId);
    }, 500);
}

function flushRuntimePersist() {
    if (runtimePersistTimer) {
        clearTimeout(runtimePersistTimer);
        runtimePersistTimer = null;
    }
    if (!currentChatSessionId) return;
    const payload = JSON.stringify(getFullRuntimeConfig(currentChatSessionId));
    navigator.sendBeacon(API_BASE + '/chat/sessions/runtime-config?chatId=' + encodeURIComponent(currentChatSessionId)
            + '&' + getSessionParam(),
        new Blob([payload], { type: 'application/json' }));
}
window.addEventListener('pagehide', flushRuntimePersist);

function restoreSessionRuntimeConfig(chatId, chatRuntimeConfig) {
    if (!chatRuntimeConfig) {
        sessionRuntimeConfigState.delete(chatId);
        return;
    }
    sessionRuntimeConfigState.set(chatId, {
        systemPrompt: chatRuntimeConfig.systemPrompt || null,
        temperature: chatRuntimeConfig.temperature != null ? chatRuntimeConfig.temperature : null,
        topP: chatRuntimeConfig.topP != null ? chatRuntimeConfig.topP : null,
        maxTokens: chatRuntimeConfig.maxTokens != null && chatRuntimeConfig.maxTokens > 0 ? chatRuntimeConfig.maxTokens : 0,
        presencePenalty: chatRuntimeConfig.presencePenalty != null ? chatRuntimeConfig.presencePenalty : null,
        frequencyPenalty: chatRuntimeConfig.frequencyPenalty != null ? chatRuntimeConfig.frequencyPenalty : null
    });
}

function getRuntimeMaxContext() {
    const n = (selectedChatModel && selectedChatModel.contentLength) ? Number(selectedChatModel.contentLength) : 65536;
    return Math.max(1, n);
}

function formatRuntimeBadge(field, val, isSet) {
    if (!isSet) {
        // 未设置时展示默认参数值；maxTokens 的 0 即服务端默认值
        return field === 'maxTokens' ? t('chat.runtime_default') : Number(RUNTIME_PARAM_DEFAULTS[field]).toFixed(2);
    }
    return field === 'maxTokens' ? Number(val).toLocaleString() : Number(val).toFixed(2);
}

function updateRuntimeRangeFill(field) {
    const els = RUNTIME_PARAM_ELEMENTS[field];
    const range = document.getElementById(els.range);
    if (!range) return;
    const min = Number(range.min);
    const max = Number(range.max);
    const pct = max > min ? ((Number(range.value) - min) / (max - min)) * 100 : 0;
    range.style.setProperty('--fill', Math.max(0, Math.min(100, pct)).toFixed(1) + '%');
}

function syncRuntimeSidebar() {
    const state = getRuntimeConfigState(currentChatSessionId);
    const promptEl = document.getElementById('runtimeSystemPrompt');
    if (promptEl) promptEl.value = (state && state.systemPrompt) || '';
    const maxCtx = getRuntimeMaxContext();
    Object.keys(RUNTIME_PARAM_ELEMENTS).forEach(function(field) {
        const els = RUNTIME_PARAM_ELEMENTS[field];
        const range = document.getElementById(els.range);
        const badge = document.getElementById(els.value);
        const def = RUNTIME_PARAM_DEFAULTS[field];
        const val = state ? state[field] : null;
        // maxTokens 为 0 表示采用服务端默认值
        const isSet = field === 'maxTokens' ? (val != null && Number(val) > 0) : (val != null && val !== '');
        if (range) {
            if (field === 'maxTokens') {
                range.max = maxCtx;
                range.value = isSet ? Math.min(Math.max(1, Number(val)), maxCtx) : 0;
            } else {
                range.value = isSet ? Math.min(Math.max(range.min, Number(val)), range.max) : def;
            }
            updateRuntimeRangeFill(field);
        }
        if (badge) {
            badge.textContent = formatRuntimeBadge(field, val, isSet);
            badge.classList.toggle('is-default', !isSet);
        }
    });
    const editableIds = ['runtimeSystemPrompt'];
    Object.keys(RUNTIME_PARAM_ELEMENTS).forEach(function(field) {
        editableIds.push(RUNTIME_PARAM_ELEMENTS[field].range);
    });
    editableIds.forEach(function(id) {
        const el = document.getElementById(id);
        if (el) el.disabled = !currentChatSessionId;
    });
}

function onRuntimePromptInput() {
    if (!currentChatSessionId) {
        syncRuntimeSidebar();
        return;
    }
    const el = document.getElementById('runtimeSystemPrompt');
    const state = getRuntimeConfigState(currentChatSessionId);
    state.systemPrompt = el.value.trim() || null;
    scheduleRuntimePersist();
}

function onRuntimeParamInput(field) {
    if (!currentChatSessionId) {
        syncRuntimeSidebar();
        return;
    }
    const els = RUNTIME_PARAM_ELEMENTS[field];
    const range = document.getElementById(els.range);
    if (!range) return;
    const num = Number(range.value);
    let val = null;
    if (!isNaN(num)) {
        if (field === 'maxTokens') {
            // 0 表示采用服务端默认值
            const maxCtx = getRuntimeMaxContext();
            val = Math.min(Math.max(0, Math.round(num)), maxCtx);
        } else {
            const limit = RUNTIME_PARAM_RANGES[field];
            val = Math.min(Math.max(limit.min, num), limit.max);
        }
    }
    getRuntimeConfigState(currentChatSessionId)[field] = val;
    updateRuntimeRangeFill(field);
    const isSet = field === 'maxTokens' ? (val != null && val > 0) : val !== null;
    const badge = document.getElementById(els.value);
    if (badge) {
        badge.textContent = formatRuntimeBadge(field, val, isSet);
        badge.classList.toggle('is-default', !isSet);
    }
    scheduleRuntimePersist();
}

function resetRuntimeDefaults() {
    if (!currentChatSessionId) {
        showToast(t('chat.session_required_first'), 'warn');
        return;
    }
    const state = getRuntimeConfigState(currentChatSessionId);
    state.systemPrompt = null;
    state.temperature = null;
    state.topP = null;
    state.maxTokens = 0;
    state.presencePenalty = null;
    state.frequencyPenalty = null;
    syncRuntimeSidebar();
    persistSessionRuntimeConfig();
}

function toggleRuntimeSidebar() {
    const sidebar = document.getElementById('runtimeSidebar');
    const btn = document.getElementById('btnRuntimeToggle');
    if (!sidebar) return;
    sidebar.classList.toggle('collapsed');
    if (btn) btn.classList.toggle('active', !sidebar.classList.contains('collapsed'));
    if (!sidebar.classList.contains('collapsed')) {
        syncRuntimeSidebar();
    }
}

function updateThinkButton() {
    var container = document.getElementById('thinkControlContainer');
    if (!container) return;
    var thinkConfig = selectedChatModel && selectedChatModel.thinkConfig;
    var hasBool = thinkConfig && thinkConfig.enableThinking && thinkConfig.enableThinkingParamName;
    var hasDepth = thinkConfig && thinkConfig.paramName;
    var supportThinking = hasBool || hasDepth;
    if (!supportThinking) {
        container.style.display = 'none';
        container.innerHTML = '';
        thinkingEnabled = false;
        currentThinkingLevel = 'no_think';
        return;
    }
    var LEVEL_ORDER = ['disabled', 'false', 'no_think', 'none', 'minimal', 'low', 'true', 'medium', 'high', 'xhigh', 'max', 'adaptive', 'enabled'];
    var depthLevels = thinkConfig.thinkingLevel || [];
    var levels;
    if (hasBool && hasDepth) {
        var seen = {};
        depthLevels.forEach(function(l) { if (l !== 'true') seen[l] = true; });
        seen['false'] = true;
        levels = LEVEL_ORDER.filter(function(l) { return seen[l]; });
        var remaining = depthLevels.filter(function(l) { return LEVEL_ORDER.indexOf(l) < 0 && l !== 'true'; });
        levels = levels.concat(remaining);
    } else if (hasDepth) {
        levels = LEVEL_ORDER.filter(function(l) { return depthLevels.indexOf(l) >= 0; });
        var remaining = depthLevels.filter(function(l) { return LEVEL_ORDER.indexOf(l) < 0; });
        levels = levels.concat(remaining);
    } else {
        levels = ['false', 'true'];
    }
    if (!levels.includes(currentThinkingLevel)) {
        currentThinkingLevel = levels[0];
    }
    container.innerHTML = '';
    var sel = document.createElement('select');
    sel.id = 'thinkLevelSelect';
    sel.className = 'think-level-select';
    sel.addEventListener('change', function() { onThinkingDropdownChange(this.value); });
    levels.forEach(function(l) {
        var opt = document.createElement('option');
        opt.value = l;
        opt.textContent = getThinkingLevelLabel(l);
        opt.style.color = typeof window.thinkingLevelColor === 'function' ? window.thinkingLevelColor(l) : '';
        if (l === currentThinkingLevel) opt.selected = true;
        sel.appendChild(opt);
    });
    sel.style.color = typeof window.thinkingLevelColor === 'function' ? window.thinkingLevelColor(currentThinkingLevel) : '';
    container.appendChild(sel);
    container.style.display = 'inline-flex';
}

async function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    sidebar.classList.toggle('collapsed');
    const isCollapsed = sidebar.classList.contains('collapsed');
    await saveUserContextField('sidebarCollapsed', isCollapsed);
}

function updateContextUsage() {
    const contextUsage = document.getElementById('contextUsage');
    const contextUsageText = document.getElementById('contextUsageText');
    const contextUsageFill = document.getElementById('contextUsageFill');
    if (!contextUsage || !selectedChatModel) {
        if (contextUsage) contextUsage.style.display = 'none';
        return;
    }
    const nCtx = selectedChatModel.contentLength || 0;
    if (nCtx <= 0) {
        contextUsage.style.display = 'none';
        return;
    }
    const percentage = Math.min((currentTotalTokens / nCtx) * 100, 100);
    contextUsageText.textContent = `${formatTokenCount(currentTotalTokens)} / ${formatTokenCount(nCtx)} (${percentage.toFixed(0)}%)`;
    contextUsageFill.style.width = percentage + '%';
    contextUsageFill.className = 'context-usage-fill';
    if (percentage < 60) {
        contextUsageFill.classList.add('green');
    } else if (percentage < 80) {
        contextUsageFill.classList.add('yellow');
    } else {
        contextUsageFill.classList.add('red');
    }
    const contextPromptSpeed = document.getElementById('contextPromptSpeed');
    const contextPredictedSpeed = document.getElementById('contextPredictedSpeed');
    if (contextPromptSpeed && currentPromptPerSecond != null) {
        contextPromptSpeed.textContent = `${t('chat.context_decode')}: ${currentPromptPerSecond.toFixed(1)} tok/s`;
        contextPromptSpeed.style.display = '';
    } else if (contextPromptSpeed) {
        contextPromptSpeed.style.display = 'none';
    }
    if (contextPredictedSpeed && currentPredictedPerSecond != null) {
        contextPredictedSpeed.textContent = `${t('chat.context_generate')}: ${currentPredictedPerSecond.toFixed(1)} tok/s`;
        contextPredictedSpeed.style.display = '';
    } else if (contextPredictedSpeed) {
        contextPredictedSpeed.style.display = 'none';
    }
    contextUsage.style.display = 'flex';
}

function displayStats(stats) {
    if (stats.totalTokens != null && stats.totalTokens > 0) {
        currentTotalTokens = stats.totalTokens;
    }
    // 一轮结束以本轮 stats 为准: 速度字段缺失时清空, 不沿用上轮旧值
    currentPromptPerSecond = stats.promptPerSecond != null ? stats.promptPerSecond : null;
    currentPredictedPerSecond = stats.predictedPerSecond != null ? stats.predictedPerSecond : null;
    updateContextUsage();
}

  function findLastTotalTokens() {
    // Prefer allMessages array for accuracy
    if (allMessages && allMessages.length > 0) {
        for (let i = allMessages.length - 1; i >= 0; i--) {
            const msg = allMessages[i];
            if (msg.role === 'assistant' && msg.stats) {
                if (msg.stats.totalTokens != null && msg.stats.totalTokens > 0) {
                    return msg.stats.totalTokens;
                }
            }
        }
    }
    // Fallback to DOM
    const container = document.getElementById('messagesContainer');
    if (!container) return 0;
    const messageEls = container.querySelectorAll('.message.assistant');
    for (let i = messageEls.length - 1; i >= 0; i--) {
        const el = messageEls[i];
        const statsEl = el.querySelector('.stats-details-popup');
        if (!statsEl) continue;
        const rows = statsEl.querySelectorAll('.stats-row');
        for (let j = rows.length - 1; j >= 0; j--) {
            const row = rows[j];
            const spans = row.querySelectorAll('span');
            if (spans.length >= 2 && spans[0].textContent.includes(t('chat.stats_total_tokens'))) {
                const val = parseInt(spans[1].textContent, 10);
                if (val > 0) return val;
            }
        }
    }
    return 0;
}

function initScrollDetection() {
    const container = document.getElementById('messagesContainer');
    let scrollRafId = null;
    // Last user-driven scrollTop; used to detect the direction of a user
    // scroll so that even a small upward scroll (inside isAtBottom()'s
    // threshold) stops auto-follow instead of being re-pinned by the
    // position check in syncAutoScrollState().
    let lastUserScrollTop = container.scrollTop;
    const onUserScroll = () => {
        if (scrollRafId) return;
        scrollRafId = requestAnimationFrame(() => {
            scrollRafId = null;
            const position = container.scrollTop;
            const delta = position - lastUserScrollTop;
            lastUserScrollTop = position;
            if (delta < 0) {
                // user scrolled up: stop following immediately
                disableAutoScroll();
            } else {
                syncAutoScrollState();
            }
        });
    };
    container.addEventListener('wheel', (e) => {
        if (e.deltaY < 0) {
            // user scrolled up: stop following immediately
            disableAutoScroll();
        } else {
            syncAutoScrollState();
        }
    }, { passive: true });
    container.addEventListener('touchmove', onUserScroll, { passive: true });
    container.addEventListener('scroll', () => {
        if (programmaticScrollTop != null) {
            if (Math.abs(container.scrollTop - programmaticScrollTop) < 2) {
                // scroll event caused by our own scrollToBottom()
                programmaticScrollTop = null;
                lastUserScrollTop = container.scrollTop;
                return;
            }
            programmaticScrollTop = null;
        }
        onUserScroll();
        // 兜底: 滚动到顶部附近时直接触发历史消息加载, 防止 IntersectionObserver
        // 漏触发导致长对话的前面消息一直不显示
        if (container.scrollTop < 150 && renderedMessageStart > 0 && !messageScrollLoading
                && typeof loadMoreMessages === 'function') {
            loadMoreMessages();
        }
    }, { passive: true });
}

document.addEventListener('click', function(e) {
    if (!e.target.closest('.message-stats') && typeof closeAllStatsPopups === 'function') {
        closeAllStatsPopups();
    }
});

// 页面卸载前标记所有 WebSocket，防止 onclose 弹出提示
window.addEventListener('beforeunload', () => {
    wsMap.forEach(ws => ws.pageIsUnloading = true);
});

