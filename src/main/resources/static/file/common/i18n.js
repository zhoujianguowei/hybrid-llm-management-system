(function() {
    var STORAGE_KEY = 'appLang';
    var currentLang = 'zh';

    function detectLang() {
        var saved = localStorage.getItem(STORAGE_KEY);
        if (saved === 'zh' || saved === 'en') {
            return saved;
        }
        if (saved) {
            localStorage.removeItem(STORAGE_KEY);
        }
        var navLang = navigator.language || navigator.userLanguage || '';
        var langs = navigator.languages || [navLang];
        for (var i = 0; i < langs.length; i++) {
            if (langs[i].toLowerCase().startsWith('zh')) {
                return 'zh';
            }
        }
        return 'en';
    }

    currentLang = detectLang();

    window.getLang = function() {
        return currentLang;
    };

    window.setLang = function(lang) {
        if (lang !== 'zh' && lang !== 'en') return;
        currentLang = lang;
        localStorage.setItem(STORAGE_KEY, lang);
        document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
        if (typeof window.onLangChange === 'function') {
            window.onLangChange(lang);
        }
        window.dispatchEvent(new CustomEvent('langchange', { detail: { lang } }));
        window.location.reload();
    };

    window.hasMsgKey = function(key) {
        if (typeof MESSAGES === 'undefined' || !MESSAGES[currentLang]) return false;
        return !!((MESSAGES[currentLang] && MESSAGES[currentLang][key])
            || (MESSAGES.en && MESSAGES.en[key])
            || (MESSAGES.zh && MESSAGES.zh[key]));
    };

    window.t = function(key, params) {
        if (typeof MESSAGES === 'undefined' || !MESSAGES[currentLang]) return key;
        var val = MESSAGES[currentLang][key] || MESSAGES.en[key] || MESSAGES.zh[key] || key;
        if (params) {
            var keys = Object.keys(params);
            for (var i = 0; i < keys.length; i++) {
                val = val.split('{' + keys[i] + '}').join(params[keys[i]]);
            }
        }
        return val;
    };

    function applyI18n() {
        document.querySelectorAll('[data-i18n]').forEach(function(el) {
            var key = el.getAttribute('data-i18n');
            var attr = el.getAttribute('data-i18n-attr') || 'textContent';
            // Skip text overwrite if this element has a child with data-i18n to avoid clobbering nested translations;
            // attribute-only targets (title/placeholder/data-tip) are safe on elements with translated children
            if ((attr === 'textContent' || attr === 'html') && el.querySelector('[data-i18n]')) {
                return;
            }
            var paramsStr = el.getAttribute('data-i18n-params');
            var params = null;
            if (paramsStr) { try { params = JSON.parse(paramsStr); } catch(e) { params = null; } }
            var val = window.t(key, params);
            if (attr === 'placeholder') {
                el.placeholder = val;
            } else if (attr === 'title') {
                el.title = val;
            } else if (attr === 'data-tip') {
                el.setAttribute('data-tip', val);
            } else if (attr === 'html') {
                el.innerHTML = val;
            } else {
                el.textContent = val;
            }
        });
        var titleEl = document.querySelector('[data-i18n-title]');
        if (titleEl) {
            document.title = window.t(titleEl.getAttribute('data-i18n-title'));
        }
    }

    window.applyI18n = applyI18n;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', applyI18n);
    } else {
        setTimeout(applyI18n, 0);
    }
})();
