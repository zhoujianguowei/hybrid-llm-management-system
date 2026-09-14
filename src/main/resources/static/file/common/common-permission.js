(function() {
    var overlayHtml = '<div class="permission-denied-overlay" id="permissionDeniedOverlay">' +
        '<i class="bi bi-shield-lock"></i>' +
        '<h2>' + (typeof t === 'function' ? t('common.permission_denied_title') : 'Permission Denied') + '</h2>' +
        '<p id="permissionDeniedMsg">' + (typeof t === 'function' ? t('common.permission_denied_msg') : 'You do not have permission.') + '</p>' +
        '</div>';
    document.body.insertAdjacentHTML('beforeend', overlayHtml);
})();

async function checkAdminPermission(onDenied) {
    if (typeof fetchCurrentUser === 'function') {
        try {
            await fetchCurrentUser();
            if (currentUserData && currentUserData.role !== 'admin') {
                if (typeof onDenied === 'function') onDenied();
                return false;
            }
        } catch(e) {}
    }
    return true;
}

function showPermissionDenied(rejectKey) {
    var msgEl = document.getElementById('permissionDeniedMsg');
    if (msgEl) {
        var text = (rejectKey && typeof t === 'function' && t(rejectKey) !== rejectKey) ? t(rejectKey) : t('common.permission_denied_msg');
        msgEl.textContent = text;
    }
    var overlay = document.getElementById('permissionDeniedOverlay');
    if (overlay) overlay.classList.add('active');
    var app = document.getElementById('appContainer');
    if (app) app.style.display = 'none';
}
