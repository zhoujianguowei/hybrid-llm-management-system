async function showUserSettings() {
    if (!currentUserData) {
        try {
            const r = await commonApi('/file/user/current');
            if (r.success && r.data) { currentUserData = r.data; currentUser = r.data; }
        } catch (e) { return; }
    }
    const modal = document.getElementById('userSettingsModal');
    const roleText = currentUserData.role === 'admin' ? t('user.role.admin') : (currentUserData.role === 'user' ? t('user.role.user') : t('user.role.guest'));
    const roleEl = document.getElementById('settingsRoleDisplay');
    roleEl.textContent = roleText;
    roleEl.className = 'header-role-tag' + (currentUserData.role === 'admin' ? ' role-admin' : currentUserData.role === 'user' ? ' role-user' : ' role-guest');
    document.getElementById('settingsRegisterTimeDisplay').textContent = currentUserData.registerTime ? new Date(currentUserData.registerTime).toLocaleString(getLang() === 'zh' ? 'zh-CN' : 'en-US') : '-';
    const loginStatus = currentUserData.loginStatus === 'online' ? t('user.status.online') : t('user.status.offline');
    const loginEl = document.getElementById('settingsLoginStatusDisplay');
    loginEl.textContent = loginStatus;
    loginEl.className = 'info-value' + (currentUserData.loginStatus === 'online' ? ' status-online' : ' status-offline');
    const statusText = currentUserData.status === 'block' ? t('user.status.blocked') : t('user.status.normal');
    const statusEl = document.getElementById('settingsStatusDisplay');
    statusEl.textContent = statusText;
    statusEl.className = 'info-value' + (currentUserData.status === 'block' ? ' status-block' : ' status-normal');
    document.getElementById('settingsRegisterEndDisplay').textContent = currentUserData.registerEnd ? new Date(currentUserData.registerEnd).toLocaleString(getLang() === 'zh' ? 'zh-CN' : 'en-US') : '-';
    document.getElementById('settingsNickname').value = currentUserData.nickname || currentUserData.username;
    document.getElementById('settingsPassword').value = '';
    document.getElementById('settingsConfirmPassword').value = '';
    const pErr = document.getElementById('settingsPasswordError');
    const cErr = document.getElementById('settingsConfirmError');
    if (pErr) pErr.textContent = '';
    if (cErr) cErr.textContent = '';
    modal.classList.add('active');
}

async function showChatSettings() {
    if (!currentUserData) return;
    try {
        const result = await commonApi('/file/user/current');
        if (result.success && result.data) {
            currentUserData = result.data;
        }
    } catch (err) {
        console.error('Refresh user data failed:', err);
    }
    const modal = document.getElementById('chatSettingsModal');
    const chatCtx = (currentUserData.userContext && currentUserData.userContext.chatContext) || {};
    const retentionDays = chatCtx.sessionRetentionDays || 365;
    const retentionEl = document.getElementById('chatSettingsSessionRetention');
    if (retentionEl) {
        retentionEl.value = retentionDays;
    }
    const shortcutKey = chatCtx.sendShortcutKey || 'ctrl+enter';
    const radios = document.querySelectorAll('input[name="sendShortcutKey"]');
    radios.forEach(r => r.checked = r.value === shortcutKey);
    const attachReasoningEl = document.getElementById('chatSettingsAttachReasoning');
    if (attachReasoningEl) {
        attachReasoningEl.checked = !!chatCtx.attachReasoningContent;
    }
    const systemPromptEl = document.getElementById('chatSettingsSystemPrompt');
    if (systemPromptEl) {
        systemPromptEl.value = chatCtx.systemPrompt || '';
    }
    modal.classList.add('active');
}

function closeChatSettingsModal() {
    document.getElementById('chatSettingsModal').classList.remove('active');
}

async function saveChatSettings() {
    const retentionEl = document.getElementById('chatSettingsSessionRetention');
    const retentionDays = retentionEl ? parseInt(retentionEl.value) : 365;
    const currentRetention = (currentUserData.userContext && currentUserData.userContext.chatContext && currentUserData.userContext.chatContext.sessionRetentionDays) || 365;
    const retentionChanged = retentionDays !== currentRetention;

    const selectedRadio = document.querySelector('input[name="sendShortcutKey"]:checked');
    const shortcutKey = selectedRadio ? selectedRadio.value : 'ctrl+enter';
    const currentShortcut = (currentUserData.userContext && currentUserData.userContext.chatContext && currentUserData.userContext.chatContext.sendShortcutKey) || 'ctrl+enter';
    const shortcutChanged = shortcutKey !== currentShortcut;

    const attachReasoningEl = document.getElementById('chatSettingsAttachReasoning');
    const attachReasoning = attachReasoningEl ? attachReasoningEl.checked : false;
    const currentAttach = !!(currentUserData.userContext && currentUserData.userContext.chatContext && currentUserData.userContext.chatContext.attachReasoningContent);
    const attachChanged = attachReasoning !== currentAttach;

    const systemPromptEl = document.getElementById('chatSettingsSystemPrompt');
    const systemPrompt = systemPromptEl ? systemPromptEl.value.trim() : '';
    const currentPrompt = (currentUserData.userContext && currentUserData.userContext.chatContext && currentUserData.userContext.chatContext.systemPrompt) || '';
    const promptChanged = systemPrompt !== currentPrompt;

    if (!retentionChanged && !shortcutChanged && !attachChanged && !promptChanged) {
        showToast(t('common.no_changes'), 'success');
        closeChatSettingsModal();
        return;
    }

    try {
        if (!currentUserData.userContext) currentUserData.userContext = { chatContext: {} };
        if (!currentUserData.userContext.chatContext) currentUserData.userContext.chatContext = {};
        currentUserData.userContext.chatContext.sessionRetentionDays = retentionDays;
        currentUserData.userContext.chatContext.sendShortcutKey = shortcutKey;
        currentUserData.userContext.chatContext.attachReasoningContent = attachReasoning;
        currentUserData.userContext.chatContext.systemPrompt = systemPrompt || null;
        await commonApi('/chat/context', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(currentUserData.userContext)
        });
        window.sendShortcutKey = shortcutKey;
        window.attachReasoningContent = attachReasoning;
        window.userSystemPrompt = systemPrompt;
        if (typeof updateInputPlaceholder === 'function') updateInputPlaceholder();
        showToast(t('common.save_success'), 'success');
        closeChatSettingsModal();
    } catch (err) {
        console.error('Save chat settings error:', err);
        showToast(t('common.operation_failed'), 'error');
    }
}

function closeUserSettingsModal() {
    document.getElementById('userSettingsModal').classList.remove('active');
}

  function validateSettingsPassword() {
    var password = document.getElementById('settingsPassword').value;
    var confirmPassword = document.getElementById('settingsConfirmPassword').value;
    var pwErr = document.getElementById('settingsPasswordError');
    var cpwErr = document.getElementById('settingsConfirmError');
    if (password.length > 0) {
      if (password.length < 8) {
        pwErr.textContent = t('user.password_short');
      } else if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
        pwErr.textContent = t('user.password_format');
      } else {
        pwErr.textContent = '';
      }
    } else {
      pwErr.textContent = '';
    }
    if (password.length > 0 && confirmPassword.length > 0 && password !== confirmPassword) {
      cpwErr.textContent = t('user.password_mismatch');
    } else {
      cpwErr.textContent = '';
    }
  }

async function saveSettings() {
    const nickname = document.getElementById('settingsNickname').value;
    const password = document.getElementById('settingsPassword').value;
    const confirmPassword = document.getElementById('settingsConfirmPassword').value;

    if (nickname.length > 8) { showToast(t('user.nickname_too_long'), 'error'); return; }
    if (password.length > 0 && password.length < 8) {
        showToast(t('user.password_short'), 'error');
        return;
    }
    if (password.length > 20) { showToast(t('user.password_long'), 'error'); return; }
    if (password.length > 0 && (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password))) { showToast(t('user.password_format'), 'error'); return; }
    if (password && password !== confirmPassword) {
        showToast(t('user.password_mismatch'), 'error');
        return;
    }

    const nicknameChanged = nickname && nickname !== (currentUserData.nickname || currentUserData.username);
    if (!nicknameChanged && !password) {
        showToast(t('common.no_changes'), 'success');
        closeUserSettingsModal();
        return;
    }

    try {
        const params = new URLSearchParams();
        if (nickname) params.append('nickname', nickname);
        if (password) params.append('password', password);
        if (confirmPassword) params.append('confirmPassword', confirmPassword);
        const result = await commonApi('/file/user/update-self', { method: 'POST', body: params });
        if (result.success) {
            if (result.msgKey === 'user.modify_relogin') {
                logout();
                return;
            }
            if (nickname) {
                currentUserData.nickname = nickname;
                document.getElementById('currentUser').textContent = nickname;
            }
            showToast(result.data, 'success');
            closeUserSettingsModal();
        } else {
            showToast(resultMsg(result, 'common.modify_failed'), 'error');
        }
    } catch (err) {
        console.error('Save settings error:', err);
        showToast(t('common.operation_failed'), 'error');
    }
}

async function fetchCurrentUser() {
    if (currentUserData) return;
    if (typeof _fetchUserPromise !== 'undefined' && _fetchUserPromise) {
        await _fetchUserPromise;
        if (currentUserData) return;
    }
    try {
        const currentUserResult = await commonApi('/file/user/current');
        if (currentUserResult.success && currentUserResult.data) {
            currentUserData = currentUserResult.data;
            currentUser = currentUserResult.data;
            document.getElementById('currentUser').textContent = currentUserData.nickname || currentUserData.username;
        }
    } catch (err) { 
        console.error('Fetch current user error:', err); 
    }
}

function showBlockedCurrentUserModal() {
    const el = document.getElementById('blockedCurrentUserModal');
    if (!el) return;
    const username = (currentUser && currentUser.username) || (currentUserData && currentUserData.username) || '';
    document.getElementById('blockedUsername').value = username;
    document.getElementById('blockedPassword').value = '';
    document.getElementById('blockedReason').value = '';
    document.getElementById('blockedSubmitResult').style.display = 'none';
    el.classList.add('active');
}

function closeBlockedCurrentUserModal() {
    const el = document.getElementById('blockedCurrentUserModal');
    if (el) el.classList.remove('active');
}

async function submitBlockedUnblock() {
    const password = document.getElementById('blockedPassword').value;
    const reason = document.getElementById('blockedReason').value;
    const resultEl = document.getElementById('blockedSubmitResult');
    if (!password) {
        resultEl.textContent = t('common.please_enter_password');
        resultEl.className = 'unblock-result fail';
        resultEl.style.display = 'block';
        return;
    }
    const username = document.getElementById('blockedUsername').value;
    try {
        const params = new URLSearchParams({ username, password, reason });
        const response = await fetch((window.API_BASE || '/command') + '/file/user/request-unblock', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params
        });
        const result = await response.json();
        if (result.success) {
            showToast(result.data || t('common.unblock_sent'), 'success');
            setTimeout(function() {
                localStorage.removeItem('sessionId');
                window.location.href = (window.STATIC_BASE || '/command/static/file') + '/login.html';
            }, 1500);
        } else {
            resultEl.textContent = result.msg || t('common.operation_failed');
            resultEl.className = 'unblock-result fail';
            resultEl.style.display = 'block';
        }
    } catch (err) {
        resultEl.textContent = t('common.network_error');
        resultEl.className = 'unblock-result fail';
        resultEl.style.display = 'block';
    }
}
