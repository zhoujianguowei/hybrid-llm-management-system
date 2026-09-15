var toastTimer = null;

// HTML 属性上下文转义（同时转义单引号，防止属性注入）
function escapeAttr(str) {
    return String(str == null ? '' : str)
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

(function() {
    var _originalFetch = window.fetch;

    function extractRequestedSessionId(args) {
        var opts = args[1] || {};
        var headers = opts.headers;
        var fromHeader = null;
        if (headers) {
            if (typeof headers.get === 'function') {
                fromHeader = headers.get('X-Session-Id');
            } else {
                for (var k in headers) {
                    if (Object.prototype.hasOwnProperty.call(headers, k) && k.toLowerCase() === 'x-session-id') {
                        fromHeader = headers[k];
                        break;
                    }
                }
            }
        }
        if (fromHeader) return fromHeader;
        var urlArg = args[0];
        var url = typeof urlArg === 'string' ? urlArg : (urlArg && urlArg.url) || '';
        try {
            var m = String(url).match(/[?&]sessionId=([^&]+)/);
            if (m && m[1]) return decodeURIComponent(m[1]);
        } catch (e) {}
        return null;
    }

    function redirectToLogin() {
        window.location.href = (window.API_BASE || '/command') + '/static/file/login.html';
    }

    function clearSessionAndRedirect() {
        localStorage.removeItem('sessionId');
        if (typeof showDialog === 'function') {
            showDialog({ type: 'info', title: t('common.session_expired_title'), message: t('common.session_expired_message'), buttons: [], duration: 2000 }).finally(function() { redirectToLogin(); });
        } else {
            redirectToLogin();
        }
    }

    // 并发 401 去重：同一时刻仅执行一次校验/登出流程，避免多个"会话过期"弹窗叠加
    var sessionExpiryFlow = null;

    // 返回 Promise：resolve 表示保留当前 session（401 与当前登录态无关），reject 表示 session 已失效
    function handleSessionExpired(requestedSessionId) {
        var currentSessionId = localStorage.getItem('sessionId');
        if (requestedSessionId && currentSessionId && requestedSessionId !== currentSessionId) {
            // 401 来自过期的旧 session（如旧标签页/页面内存旧值），不清除当前有效 session
            console.warn('Stale session expired in request, keeping current session in storage');
            return Promise.reject(new Error('Session expired'));
        }
        if (!currentSessionId) {
            redirectToLogin();
            return Promise.reject(new Error('Session expired'));
        }
        // 请求未携带可识别的 session（requestedSessionId 为空）或与当前相同：
        // 401 不一定代表当前存储的 session 失效（如无 session 的请求、应用重启前的在途请求等），
        // 先向后端校验当前 session，确认失效才清除登录态，避免误删仍有效的 session。
        if (sessionExpiryFlow) return sessionExpiryFlow;
        var validateUrl = (window.API_BASE || '/command') + '/file/validate?sessionId=' + encodeURIComponent(currentSessionId);
        sessionExpiryFlow = _originalFetch(validateUrl, { credentials: 'same-origin' })
            .then(function(r) { return r.json(); })
            .then(function(result) {
                if (result && result.success) {
                    console.warn('401 received but stored session still valid; keeping session');
                    return;
                }
                clearSessionAndRedirect();
            })
            .catch(function() {
                // 校验请求失败（网络异常等），不销毁可能有效的 session，仅跳转登录页
                redirectToLogin();
            })
            .then(function() {
                sessionExpiryFlow = null;
            });
        return sessionExpiryFlow;
    }

    window.fetch = function() {
        var args = arguments;
        var requestedSessionId = extractRequestedSessionId(args);
        return _originalFetch.apply(this, args).then(function(response) {
            if (response.status === 401) {
                return handleSessionExpired(requestedSessionId).then(function() {
                    throw new Error('Session expired');
                });
            }
            var ct = response.headers.get('content-type');
            if (ct && ct.indexOf('application/json') !== -1 && response.ok) {
                return response.clone().text().then(function(body) {
                    var data = JSON.parse(body);
                    if (data.code === 401) {
                        return handleSessionExpired(requestedSessionId).then(function() {
                            throw new Error('Session expired');
                        });
                    }
                    var res = new Response(body, {
                        status: response.status,
                        statusText: response.statusText,
                        headers: response.headers
                    });
                    return res;
                });
            }
            return response;
        });
    };
})();

// 根据 ResultModel 的 msgKey + params 渲染本地化消息，未配置时回退到 msg 或默认文案
window.resultMsg = function(result, fallbackKey) {
    if (!result) return '';
    var localized = result.msgKey ? t(result.msgKey, result.params) : '';
    if (result.msgKey && localized === result.msgKey) {
        // msgKey 未命中（t 返回原 key），回退到服务端 message
        return result.msg || (fallbackKey ? t(fallbackKey) : '');
    }
    return localized || result.msg || (fallbackKey ? t(fallbackKey) : '');
};

function fileBrowserFetch(url) {
    var headers = { 'X-Session-Id': localStorage.getItem('sessionId') || '' };
    return fetch(url, { credentials: 'same-origin', headers: headers }).then(function(r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
    });
}

async function safeApiCall(url, options = {}) {
    const sessionId = localStorage.getItem('sessionId');
    if (!sessionId) {
        window.location.href = '/command/static/file/login.html';
        throw new Error('No session');
    }
    const headers = options.headers || {};
    headers['X-Session-Id'] = sessionId;
    let controller = null;
    let timer = null;
    if (options.timeout) {
        controller = new AbortController();
        timer = setTimeout(() => controller.abort(), options.timeout);
    }
    const response = await fetch(url, {...options, headers, signal: controller ? controller.signal : undefined}).catch(async (err) => {
        if (timer) clearTimeout(timer);
        if (err.name === 'AbortError') throw new Error(t('common.request_timeout'));
        throw err;
    });
    if (timer) clearTimeout(timer);
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
        const data = await response.json();
        if (data.code === 403) {
            if (typeof showPermissionDenied === 'function') {
                showPermissionDenied(data.msgKey);
            }
            throw new Error('Permission denied');
        }
        if (data.code === 7001) {
            var errorMsg = resultMsg(data, 'common.business_failed');
            if (data.msgKey === 'config.optional_validation_failed') {
                showToast(t('common.optional_config_failed'), 'error');
            } else {
                var lines = errorMsg.split('\n').filter(function(l) { return l.trim(); });
                var detailHtml = '';
                lines.forEach(function(line) {
                    detailHtml += '<div style="margin-bottom:6px;"><span style="color:#ef4444;font-weight:600;">' + line + '</span></div>';
                });
                if (typeof showDialog === 'function') {
                    showDialog({ type: 'error', title: t('common.config_validate_failed'), html: detailHtml, buttons: [{ label: t('common.confirm'), value: 'confirm', primary: true }] });
                } else {
                    showToast(t('common.config_validate_failed') + '：' + errorMsg, 'error');
                }
            }
            throw new Error('Business logic error');
        }
        if (data.code === 7002) {
            var errors = data.data || [];
            var detailHtml = '';
            errors.forEach(function(err) {
                var parts = err.split('||');
                if (parts.length === 2) {
                    var field = parts[0].replace(/^Field: /, '');
                    var detail = parts[1].replace(/^Detail: /, '');
                    detailHtml += '<div style="margin-bottom:6px;"><span style="color:#ef4444;font-weight:600;">' + field + '</span><span style="color:var(--text-secondary);margin-left:6px;">' + detail + '</span></div>';
                } else {
                    detailHtml += '<div style="margin-bottom:6px;color:var(--text-secondary);">' + err + '</div>';
                }
            });
            if (typeof showDialog === 'function') {
                showDialog({ type: 'error', title: t('common.param_validate_failed'), html: detailHtml, buttons: [{ label: t('common.confirm'), value: 'confirm', primary: true }] });
            } else {
                showToast(t('common.param_validate_failed') + '：' + errors.join('; '), 'error');
            }
            throw new Error('Validation failed');
        }
        return data;
    } else {
        throw new Error('Expected JSON response');
    }
}

async function commonApi(path, options) {
    options = options || {};
    var sessionId = localStorage.getItem('sessionId');
    if (!sessionId) {
        window.location.href = (window.API_BASE || '/command') + '/static/file/login.html';
        throw new Error('No session');
    }
    var headers = {};
    var method = (options.method || 'GET').toUpperCase();
    if (method !== 'GET' && method !== 'HEAD' && !(options.body instanceof FormData)) {
        headers['Content-Type'] = options.headers && options.headers['Content-Type'] === 'application/json'
            ? 'application/json' : 'application/x-www-form-urlencoded';
    }
    headers['X-Session-Id'] = sessionId;
    var url = (window.API_BASE || '/command') + path + (path.indexOf('?') !== -1 ? '&' : '?') + 'sessionId=' + encodeURIComponent(sessionId);
    var response = await fetch(url, Object.assign({}, options, {headers: Object.assign({}, options.headers, headers)}));
    var ct = response.headers.get('content-type');
    if (ct && ct.indexOf('application/json') !== -1) {
        var data = await response.json();
        if (data.code === 402) { throw new Error('User blocked'); }
        if (data.code === 403) {
            if (typeof showPermissionDenied === 'function') {
                showPermissionDenied(data.msgKey);
            }
            throw new Error('Permission denied');
        }
        if (data.code === 7002) {
            var errors = data.data || [];
            var detailHtml = '';
            errors.forEach(function(err) {
                var parts = err.split('||');
                if (parts.length === 2) {
                    var field = parts[0].replace(/^Field: /, '');
                    var detail = parts[1].replace(/^Detail: /, '');
                    detailHtml += '<div style="margin-bottom:6px;"><span style="color:#ef4444;font-weight:600;">' + field + '</span><span style="color:var(--text-secondary);margin-left:6px;">' + detail + '</span></div>';
                } else {
                    detailHtml += '<div style="margin-bottom:6px;color:var(--text-secondary);">' + err + '</div>';
                }
            });
            if (typeof showDialog === 'function') {
                showDialog({ type: 'error', title: t('common.param_validate_failed'), html: detailHtml, buttons: [{ label: t('common.confirm'), value: 'confirm', primary: true }] });
            } else {
                showToast(t('common.param_validate_failed') + '：' + errors.join('; '), 'error');
            }
            throw new Error('Validation failed');
        }
        return data;
    }
    throw new Error('Expected JSON response');
}

function filterRecentData(data, minutes) {
    var now = Date.now();
    var cutoff = now - minutes * 60 * 1000;
    return data.filter(function(item) { return (item.key || item[0]) >= cutoff; });
}

function formatTime(timestamp) {
    return new Date(timestamp).toTimeString().split(' ')[0];
}

function showToast(message, type, duration) {
    // 统一使用 #toast 元素样式(顶部居中浅色/深色提示条),简单提示不再走对话框样式
    var toast = document.getElementById('toast');
    if (!toast) return;
    if (typeof toastTimer !== 'undefined' && toastTimer) clearTimeout(toastTimer);
    toast.textContent = message;
    toast.className = 'toast';
    if (type === 'error') toast.classList.add('error');
    else if (type === 'success') toast.classList.add('success');
    toast.classList.add('active');
    toast.onclick = function() {
        toast.classList.remove('active', 'error', 'success');
        toastTimer = null;
    };
    toastTimer = setTimeout(function() {
        toast.classList.remove('active', 'error', 'success');
        toastTimer = null;
    }, duration || 3000);
}

function showLoading(text) {
    var overlay = document.getElementById('globalLoadingOverlay');
    if (overlay) {
        var msg = overlay.querySelector('div:last-child');
        if (msg) msg.textContent = text || t('common.loading');
        overlay.classList.add('active');
    }
}

function hideLoading() {
    var overlay = document.getElementById('globalLoadingOverlay');
    if (overlay) overlay.classList.remove('active');
}
