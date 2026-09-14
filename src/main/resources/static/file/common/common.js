window.API_BASE = '/command';
window.STATIC_BASE = '/command/static/file';
var currentUser = null;
var currentUserData = null;
var _fetchUserPromise = null;

// 主题切换
window.setTheme = function(theme) {
    document.body.className = document.body.className.replace(/theme-\w+\s*/g, '');
    if (theme !== 'light') { document.body.classList.add('theme-' + theme); }
    localStorage.setItem('fileManagerTheme', theme);
    document.querySelectorAll('.theme-option').forEach(function(opt) { opt.classList.remove('active'); });
    var active = document.querySelector('.theme-option[data-theme="' + theme + '"]');
    if (active) active.classList.add('active');
};

window.toggleThemeDropdown = function() {
    var dd = document.getElementById('themeDropdown');
    if (dd) dd.classList.toggle('active');
};

function loadTheme() {
    const theme = localStorage.getItem('fileManagerTheme') || 'light';
    document.body.className = document.body.className.replace(/theme-\w+\s*/g, '');
    if (theme !== 'light') { document.body.classList.add('theme-' + theme); }
    document.querySelectorAll('.theme-option').forEach(opt => opt.classList.remove('active'));
    const activeOption = document.querySelector('.theme-option[data-theme="' + theme + '"]');
    if (activeOption) { activeOption.classList.add('active'); }
}

document.addEventListener('click', function(e) {
    if (!e.target.closest('.theme-selector')) {
        const dropdown = document.getElementById('themeDropdown');
        if (dropdown) dropdown.classList.remove('active');
    }
    if (!e.target.closest('.lang-selector')) {
        const dd = document.getElementById('langDropdown');
        if (dd) dd.classList.remove('active');
    }
});

loadTheme();

// 语言切换下拉
window.toggleLangDropdown = function() {
    var dd = document.getElementById('langDropdown');
    if (dd) dd.classList.toggle('active');
};

function updateLangUI() {
    if (typeof getLang !== 'function') return;
    var lang = getLang();
    var label = document.getElementById('langCurrentLabel') || document.getElementById('currentLangLabel');
    if (label) label.textContent = lang === 'zh' ? '中文' : 'English';
    document.querySelectorAll('.lang-option').forEach(function(o) { o.classList.remove('active'); });
    var active = document.querySelector('.lang-option[data-lang="' + lang + '"]');
    if (active) active.classList.add('active');
}

window.onLangChange = function(lang) {
    updateLangUI();
};

window.selectLang = function(lang) {
    if (typeof setLang === 'function') setLang(lang);
    var dd = document.getElementById('langDropdown');
    if (dd) dd.classList.remove('active');
};

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', updateLangUI);
} else {
    updateLangUI();
}

// 思考等级显示：i18n 文案 + 颜色区分（值本身仍为原始值，仅影响展示）
var THINK_LEVEL_I18N_KEYS = {
    'false': 'model.thinking_off',
    'true': 'model.thinking_on',
    'no_think': 'model.thinking_no_think',
    'non_think': 'model.thinking_non_think',
    'none': 'model.thinking_none',
    'minimal': 'model.thinking_minimal',
    'low': 'model.thinking_low',
    'medium': 'model.thinking_medium',
    'high': 'model.thinking_high',
    'xhigh': 'model.thinking_xhigh',
    'max': 'model.thinking_max',
    'think': 'model.thinking_think',
    'disabled': 'model.thinking_disabled',
    'adaptive': 'model.thinking_adaptive',
    'enabled': 'model.thinking_enabled'
};
var THINK_LEVEL_COLORS = {
    'false': '#94a3b8', 'no_think': '#94a3b8', 'non_think': '#94a3b8', 'none': '#94a3b8', 'disabled': '#94a3b8',
    'true': '#16a34a', 'think': '#16a34a', 'enabled': '#16a34a',
    'minimal': '#0d9488',
    'low': '#16a34a',
    'medium': '#2563eb',
    'high': '#f59e0b',
    'xhigh': '#ea580c',
    'max': '#dc2626',
    'adaptive': '#7c3aed'
};
window.thinkingLevelLabel = function(level) {
    if (level === null || level === undefined || level === '') return level;
    var norm = (level === true) ? 'true' : (level === false) ? 'false' : level;
    var lang = (typeof window.getLang === 'function') ? window.getLang() : 'zh';
    if (lang === 'en') {
        // 英文：仅 on/off 显示文案，其余等级显示原始值（如 xhigh）
        if (norm === 'true') return window.t('model.thinking_on');
        if (norm === 'false') return window.t('model.thinking_off');
        return level;
    }
    var key = THINK_LEVEL_I18N_KEYS[level];
    return key ? window.t(key) : level;
};
window.thinkingLevelColor = function(level) {
    return THINK_LEVEL_COLORS[level] || '#64748b';
};
window.thinkingLevelBadgeHtml = function(level) {
    if (level === null || level === undefined || level === '' || level === '--') return null;
    var fg = level === 'high' ? '#1f2937' : '#fff';
    var safe = String(window.thinkingLevelLabel(level))
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    return '<span class="badge" style="background-color:' + window.thinkingLevelColor(level) + ';color:' + fg + ';">' + safe + '</span>';
};

// Toast/Loading 已在 common-api.js 中定义

// 退出登录
async function logout() {
    try { await commonApi('/file/logout'); } catch (e) {}
    localStorage.removeItem('sessionId');
    window.location.href = STATIC_BASE + '/login.html';
}

var _userShareHtmlLoaded = false;
function loadUserShareHtml() {
    if (_userShareHtmlLoaded) {
        return;
    }
    _userShareHtmlLoaded = true;
    // 尽早获取用户信息，与 HTML 请求并行
    if (typeof commonApi === 'function') {
        _fetchUserPromise = commonApi('/file/user/current').then(function(r) {
            if (r.success && r.data) {
                currentUserData = r.data;
                currentUser = r.data;
                var el = document.getElementById('currentUser');
                if (el) el.textContent = r.data.nickname || r.data.username;
            }
        }).catch(function(err){ console.error('Fetch user data error:', err); });
    }
    fetch(STATIC_BASE + '/user_share.html').then(function(r) { return r.text(); }).then(function(html) {
        document.body.insertAdjacentHTML('beforeend', html);
        if (typeof window.applyI18n === 'function') window.applyI18n();
    });
}
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadUserShareHtml);
} else {
    loadUserShareHtml();
}

// API 辅助 — commonApi 已移至 common-api.js
// 获取当前用户 — 在 user_share.js 加载后由页面自行调用
