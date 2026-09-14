var dialogInstance = null;

function showDialog(options) {
    if (dialogInstance) {
        dialogInstance.close();
        dialogInstance = null;
    }

    var title = options.title || '';
    var message = options.message || '';
    var html = options.html || '';
    var type = options.type || 'info';
    // toast mode: non-blocking notification (transparent overlay, clicks pass through)
    var toast = options.toast === true;
    var buttons = options.buttons;
    if (buttons === undefined) {
        buttons = [
            { label: t('dialog.confirm'), value: 'confirm', primary: true }
        ];
    }
    if (toast) buttons = [];
    var duration = options.duration || 2000;
    var width = options.width || '420px';

    var overlay = document.createElement('div');
    overlay.className = 'confirm-modal active' + (toast ? ' toast-mode' : '');

    var box = document.createElement('div');
    box.className = 'confirm-dialog';
    if (width !== '420px') box.style.maxWidth = width;

    var iconMap = {
        'info': '<span class="dialog-icon info"><i class="bi bi-info-circle-fill"></i></span>',
        'warn': '<span class="dialog-icon warn"><i class="bi bi-exclamation-triangle-fill"></i></span>',
        'error': '<span class="dialog-icon error"><i class="bi bi-x-octagon-fill"></i></span>',
        'success': '<span class="dialog-icon success"><i class="bi bi-check-circle-fill"></i></span>'
    };
    var iconHtml = iconMap[type] || iconMap['info'];

    var titleEl = document.createElement('h3');
    if (title.indexOf('<') >= 0) titleEl.innerHTML = title;
    else titleEl.textContent = title;

    box.innerHTML = iconHtml;
    box.appendChild(titleEl);

    if (html) {
        var body = document.createElement('p');
        body.innerHTML = html;
        box.appendChild(body);
    } else if (message) {
        var body = document.createElement('p');
        body.textContent = message;
        box.appendChild(body);
    }

    if (buttons.length > 0) {
        var footer = document.createElement('div');
        footer.className = 'btn-group';

        buttons.forEach(function(btn) {
            var btnEl = document.createElement('button');
            if (btn.primary) {
                btnEl.className = 'btn btn-confirm';
                if (type === 'error') btnEl.classList.add('btn-confirm-danger');
            } else {
                btnEl.className = 'btn btn-cancel';
            }
            btnEl.textContent = btn.label;

            btnEl.addEventListener('click', function(e) {
                e.stopPropagation();
                resolveDialog(btn.value);
            });

            footer.appendChild(btnEl);
        });

        box.appendChild(footer);
    }

    overlay.appendChild(box);
    document.body.appendChild(overlay);

    if (!toast) {
        overlay.addEventListener('click', function(e) {
            if (e.target === overlay) rejectDialog();
        });
    }

    var resolved = false;

    function resolveDialog(value) {
        if (resolved) return;
        resolved = true;
        if (autoTimer) clearTimeout(autoTimer);
        dialogInstance = null;
        if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
        requestAnimationFrame(function() { deferred.resolve(value); });
    }

    function rejectDialog() {
        if (resolved) return;
        resolved = true;
        if (autoTimer) clearTimeout(autoTimer);
        dialogInstance = null;
        if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
        requestAnimationFrame(function() { deferred.reject(); });
    }

    // Toasts never reject: replaced/closed toasts settle silently
    function closeSilently() {
        if (resolved) return;
        resolved = true;
        if (autoTimer) clearTimeout(autoTimer);
        dialogInstance = null;
        if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
        requestAnimationFrame(function() { deferred.resolve('closed'); });
    }

    var autoTimer = null;
    if (buttons.length === 0) {
        autoTimer = setTimeout(function() {
            resolveDialog('ok');
        }, duration);
    }

    dialogInstance = { close: toast ? closeSilently : rejectDialog };

    var deferred = {};
    var promise = new Promise(function(resolve, reject) {
        deferred.resolve = resolve;
        deferred.reject = reject;
    });
    return promise;
}

function getRegexHelpHtml() {
    return '<div class="regex-section">' +
        '<div class="regex-section-title"><i class="bi bi-code-slash"></i> ' + t('dialog.regex_basic') + '</div>' +
        '<div class="regex-item"><code>*</code><span class="regex-desc">' + t('dialog.regex_match_zero_more') + '</span></div>' +
        '<div class="regex-item"><code>+</code><span class="regex-desc">' + t('dialog.regex_match_one_more') + '</span></div>' +
        '<div class="regex-item"><code>.</code><span class="regex-desc">' + t('dialog.regex_any_char') + '</span></div>' +
        '<div class="regex-item"><code>^</code><span class="regex-desc">' + t('dialog.regex_start') + '</span></div>' +
        '<div class="regex-item"><code>$</code><span class="regex-desc">' + t('dialog.regex_end') + '</span></div>' +
        '<div class="regex-item"><code>[]</code><span class="regex-desc">' + t('dialog.regex_char_class') + '</span></div>' +
        '<div class="regex-item"><code>|</code><span class="regex-desc">' + t('dialog.regex_or') + '</span></div>' +
        '</div>' +
        '<div class="regex-section">' +
        '<div class="regex-section-title"><i class="bi bi-shield-exclamation"></i> ' + t('dialog.regex_escape') + '</div>' +
        '<div class="regex-item"><code>\\</code><span class="regex-desc">' + t('dialog.regex_escape_desc') + '</span></div>' +
        '<div class="regex-item"><code>\\.</code><span class="regex-desc">' + t('dialog.regex_literal_dot') + '</span></div>' +
        '</div>' +
        '<div class="regex-section">' +
        '<div class="regex-section-title"><i class="bi bi-lightbulb"></i> ' + t('dialog.regex_examples') + '</div>' +
        '<div class="regex-item"><code>^qwen.*</code><span class="regex-desc">' + t('dialog.regex_qwen') + '</span></div>' +
        '<div class="regex-item"><code>.*:latest$</code><span class="regex-desc">' + t('dialog.regex_latest') + '</span></div>' +
        '<div class="regex-item"><code>.*Qwen3\\.6.*</code><span class="regex-desc">' + t('dialog.regex_qwen3') + '</span></div>' +
        '<div class="regex-item"><code>llama3[^:]*8b</code><span class="regex-desc">' + t('dialog.regex_llama') + '</span></div>' +
        '<div class="regex-item"><code>^(qwen|llama|mistral).*</code><span class="regex-desc">' + t('dialog.regex_multi') + '</span></div>' +
        '</div>';
}
