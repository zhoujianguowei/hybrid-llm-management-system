function showApiConfigModal() {
    if (!isAdmin) return;
    loadApiConfigList();
    switchConfigTab('openapi');
    document.getElementById('systemConfigModal').classList.add('active');
}

function closeSystemConfigModal() {
    document.getElementById('systemConfigModal').classList.remove('active');
}

function switchConfigTab(tab) {
    document.querySelectorAll('.config-tab').forEach(t => t.classList.remove('active'));
    document.querySelector(`.config-tab[data-tab="${tab}"]`).classList.add('active');
    document.getElementById('tabOpenapi').style.display = tab === 'openapi' ? 'block' : 'none';
    document.getElementById('tabModel').style.display = tab === 'model' ? 'block' : 'none';
    document.getElementById('tabSession').style.display = tab === 'session' ? 'block' : 'none';
    document.getElementById('btnAddConfig').style.display = tab === 'openapi' ? '' : 'none';
    document.getElementById('btnAddModelFunc').style.display = tab === 'model' ? '' : 'none';
    if (tab === 'model') {
        loadModelFuncConfigList();
    } else if (tab === 'session') {
        loadSessionConfigList();
    }
}

async function loadApiConfigList() {
    try {
        const result = await api('/chat/system-setting/api-configs', { method: 'POST' });
        if (result.success) {
            renderApiConfigList(result.data);
        }
    } catch (error) {
        console.error('Load API config list failed:', error);
    }
}

function renderApiConfigList(configs) {
    const container = document.getElementById('apiConfigList');
    if (configs.length === 0) {
        container.innerHTML = '<div class="empty-state"><i class="bi bi-cloud"></i><p>' + t('config.no_api') + '</p></div>';
        return;
    }

    container.innerHTML = configs.map(config => `
        <div class="api-config-item">
            <div class="api-config-info">
                <h4><i class="bi bi-hdd-network"></i> ${config.baseUrl}</h4>
                <div class="config-tags">
                    <span class="config-tag tag-type">${config.apiKey ? t('config.key_set') : t('config.key_unset')}</span>
                </div>
            </div>
            <div class="api-config-actions">
                <button class="btn-edit" onclick="editConfig('${config.id}')" title="${t('config.edit')}"><i class="bi bi-pencil"></i></button>
                <button class="btn-delete" onclick="deleteConfig('${config.id}')" title="${t('config.delete')}"><i class="bi bi-trash3"></i></button>
            </div>
        </div>
    `).join('');
  }

  function showNewConfigForm() {
    document.getElementById('configFormTitle').textContent = t('config.add_api');
    document.getElementById('configForm').reset();
    document.getElementById('configId').value = '';
    document.getElementById('configBaseUrl').value = '';
    document.getElementById('configApiKey').value = '';
    const testResult = document.getElementById('configTestResult');
    testResult.style.display = 'none';
    testResult.className = 'config-test-result';
    document.getElementById('configFormModal').classList.add('active');
}

  function editConfig(id) {
    const config = apiConfigs.find(c => c.id === id);
    if (!config) return;

    document.getElementById('configFormTitle').textContent = t('config.edit_api');
    document.getElementById('configId').value = config.id || '';
    document.getElementById('configBaseUrl').value = config.baseUrl || '';
    document.getElementById('configApiKey').value = config.apiKey || '';
    const testResult = document.getElementById('configTestResult');
    testResult.style.display = 'none';
    testResult.className = 'config-test-result';
    document.getElementById('configFormModal').classList.add('active');
}

async function testConfigForm() {
    const baseUrl = document.getElementById('configBaseUrl').value.trim();
    const apiKey = document.getElementById('configApiKey').value;
    const testResult = document.getElementById('configTestResult');
    const btn = document.getElementById('btnTestConfig');

    if (!baseUrl) {
        showAlert(t('config.fill_api_url'), 'warning');
        return;
    }

    btn.disabled = true;
    btn.innerHTML = '<i class="bi bi-arrow-repeat spinner-rotate"></i> ' + t('config.testing');
    testResult.style.display = 'block';
    testResult.className = 'config-test-result';
    testResult.innerHTML = '<i class="bi bi-arrow-repeat spinner-rotate"></i> ' + t('config.test_connecting');

    try {
        const result = await api('/chat/openapi/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ baseUrl: baseUrl, apiKey: apiKey })
        });

        if (result.success && result.data) {
            const resp = result.data;
            if (resp.errorMsg) {
                testResult.className = 'config-test-result test-error';
                testResult.innerHTML = '<i class="bi bi-exclamation-triangle"></i> ';
                testResult.appendChild(document.createTextNode(resp.errorMsg));
            } else if (resp.modelNames && resp.modelNames.length > 0) {
                testResult.className = 'config-test-result test-success';
                testResult.innerHTML = '<i class="bi bi-check-circle"></i> ' + t('config.connect_success', {count: resp.modelNames.length}) + '<br><span style="opacity:0.8;margin-top:4px;display:block;">' + t('config.model_list', {models: ''}) + '</span>';
                const modelSpan = testResult.querySelector('span');
                modelSpan.appendChild(document.createTextNode(resp.modelNames.join(', ')));
            } else {
                testResult.className = 'config-test-result test-warning';
                testResult.innerHTML = '<i class="bi bi-info-circle"></i> ' + t('config.connect_no_model');
            }
        } else {
            testResult.className = 'config-test-result test-error';
            testResult.innerHTML = '<i class="bi bi-exclamation-triangle"></i> ' + t('config.test_request_failed');
        }
    } catch (error) {
        testResult.className = 'config-test-result test-error';
        testResult.innerHTML = '<i class="bi bi-exclamation-triangle"></i> ' + t('config.test_failed') + ': ' + error.message;
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="bi bi-plug"></i> ' + t('config.test_connection');
    }
}

function closeConfigFormModal() {
    document.getElementById('configFormModal').classList.remove('active');
}

async function saveConfig() {
    const config = {
        id: document.getElementById('configId').value || '',
        baseUrl: document.getElementById('configBaseUrl').value,
        apiKey: document.getElementById('configApiKey').value
    };

    if (!config.baseUrl) {
        showAlert(t('config.fill_api_url'), 'warning');
        return;
    }

    try {
        const result = await api('/chat/system-setting/api-configs/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });

      if (result.success) {
            closeConfigFormModal();
            loadApiConfigs();
            loadApiConfigList();
            loadChatModels();
        } else {
            showAlert(result.msg || t('config.save_failed'), 'error');
        }
    } catch (error) {
        console.error('Save config failed:', error);
        showAlert(t('config.save_failed'), 'error');
    }
}

async function deleteConfig(configId) {
    const action = await showDialog({ type: 'warn', title: t('config.delete_title'), message: t('config.delete_confirm'), buttons: [{ label: t('user.confirm_delete'), value: 'yes', primary: true }, { label: t('user.cancel'), value: 'cancel' }] }).catch(() => null);
    if (action !== 'yes') return;

    try {
        const result = await api(`/chat/system-setting/api-configs/delete?configId=${configId}`, { method: 'POST' });
        
        if (result.success) {
            loadApiConfigs();
            loadApiConfigList();
            loadChatModels();
        }
    } catch (error) {
        console.error('Delete config failed:', error);
        showAlert(t('config.delete_failed'), 'error');
    }
}

async function loadModelFuncConfigList() {
    try {
        const result = await api('/chat/system-setting/model-func-configs', { method: 'POST' });
        if (result.success) {
            renderModelFuncConfigList(result.data);
        }
    } catch (error) {
        console.error('Load model func configs failed:', error);
    }
}

function renderModelFuncConfigList(configs) {
    const container = document.getElementById('modelFuncConfigList');
    if (configs.length === 0) {
        container.innerHTML = '<div class="empty-state"><i class="bi bi-cpu"></i><p>' + t('config.no_model_config') + '</p></div>';
        return;
    }
    container.innerHTML = configs.map(config => {
        const funcTags = [];
        if (config.func & 0x01) funcTags.push('<span class="config-tag tag-feature">' + t('config.image') + '</span>');
        if (config.func & 0x02) funcTags.push('<span class="config-tag tag-feature">' + t('config.video') + '</span>');
        if (config.func & 0x04) funcTags.push('<span class="config-tag tag-feature">' + t('config.audio') + '</span>');
        if (config.thinkParamName) funcTags.push('<span class="config-tag tag-thinking">' + t('config.thinking') + '</span>');
        return `
        <div class="api-config-item">
            <div class="api-config-info">
                <h4><i class="bi bi-cpu"></i> ${config.regex}</h4>
                <div class="config-tags">
                    <span class="config-tag tag-type">${config.type === 'exact' ? t('config.exact_match') : t('config.regex_match')}</span>
                    ${config.overrideLocal ? '<span class="config-tag tag-type">' + t('config.override_local') + '</span>' : ''}
                    ${funcTags.join('') || '<span class="config-tag tag-inactive">' + t('config.no_feature') + '</span>'}
                </div>
            </div>
            <div class="api-config-actions">
                <button class="btn-move" onclick="moveModelFunc('${config.id}', true)" title="${t('config.move_up')}"><i class="bi bi-caret-up"></i></button>
                <button class="btn-move" onclick="moveModelFunc('${config.id}', false)" title="${t('config.move_down')}"><i class="bi bi-caret-down"></i></button>
                <button class="btn-edit" onclick="editModelFunc('${config.id}')" title="${t('config.edit')}"><i class="bi bi-pencil"></i></button>
                <button class="btn-delete" onclick="deleteModelFunc('${config.id}')" title="${t('config.delete')}"><i class="bi bi-trash3"></i></button>
            </div>
        </div>`;
    }).join('');
}

function toggleRegexHelp() {
    showDialog({
        title: '<i class="bi bi-info-circle"></i> ' + t('dialog.regex_help_title'),
        html: getRegexHelpHtml(),
        type: 'info',
        width: '480px',
        buttons: [
            { label: t('dialog.confirm'), value: 'confirm' }
        ]
    });
}

function showNewModelFuncForm() {
    document.getElementById('modelFuncFormTitle').textContent = t('config.add_model');
    document.getElementById('modelFuncId').value = '';
    document.getElementById('modelFuncType').value = 'exact';
    document.getElementById('modelFuncRegex').value = '';
    document.getElementById('modelFuncImage').checked = false;
    document.getElementById('modelFuncVideo').checked = false;
    document.getElementById('modelFuncAudio').checked = false;
    document.getElementById('modelFuncVisibility').value = 'PUBLIC';
    document.getElementById('modelFuncOverrideLocal').checked = false;
    document.getElementById('thinkModeSelect').value = '';
    document.getElementById('thinkModeFields').style.display = 'none';
    document.getElementById('modelFuncFormModal').classList.add('active');
}

async function editModelFunc(id) {
    try {
        const result = await api('/chat/system-setting/model-func-configs', { method: 'POST' });
        if (!result.success) return;
        const item = result.data.find(c => c.id === id);
        if (!item) return;
        document.getElementById('modelFuncFormTitle').textContent = t('config.edit_model');
        document.getElementById('modelFuncId').value = item.id;
        document.getElementById('modelFuncType').value = item.type;
        document.getElementById('modelFuncRegex').value = item.regex;
        document.getElementById('modelFuncImage').checked = !!(item.func & 0x01);
        document.getElementById('modelFuncVideo').checked = !!(item.func & 0x02);
        document.getElementById('modelFuncAudio').checked = !!(item.func & 0x04);
        document.getElementById('modelFuncVisibility').value = item.visibility || 'PUBLIC';
        document.getElementById('modelFuncOverrideLocal').checked = !!item.overrideLocal;
        const thinkParam = item.thinkParamName || '';
        const enableThinking = item.enableThinking;
        if (thinkParam || enableThinking) {
            if (thinkParam === 'thinking_mode') {
                document.getElementById('thinkModeSelect').value = 'thinking_mode';
                document.getElementById('thinkModeFields').style.display = 'flex';
                onThinkModeChange();
                setSelectedThinkLevels(item.thinkingLevel);
            } else if (thinkParam === 'reasoning_effort' && enableThinking) {
                document.getElementById('thinkModeSelect').value = 'hybrid';
                document.getElementById('thinkBoolParam').value = item.enableThinkingParamName || 'enable_thinking';
                document.getElementById('thinkModeFields').style.display = 'flex';
                onThinkModeChange();
                setSelectedThinkLevels(item.thinkingLevel);
            } else if (thinkParam === 'reasoning_effort') {
                document.getElementById('thinkModeSelect').value = 'multi';
                document.getElementById('thinkModeFields').style.display = 'flex';
                onThinkModeChange();
                setSelectedThinkLevels(item.thinkingLevel);
            } else {
                document.getElementById('thinkModeSelect').value = 'single';
                var boolParam = thinkParam === 'thinking' ? 'thinking' : 'enable_thinking';
                document.getElementById('thinkBoolParam').value = boolParam;
                document.getElementById('thinkModeFields').style.display = 'flex';
                onThinkModeChange();
            }
        } else {
            document.getElementById('thinkModeSelect').value = '';
            document.getElementById('thinkModeFields').style.display = 'none';
        }
        document.getElementById('modelFuncFormModal').classList.add('active');
    } catch (error) {
        showAlert(t('config.load_failed'), 'error');
    }
}

function closeModelFuncFormModal() {
    document.getElementById('modelFuncFormModal').classList.remove('active');
}

async function saveModelFuncConfig() {
    const type = document.getElementById('modelFuncType').value;
    const regex = document.getElementById('modelFuncRegex').value.trim();
    if (!regex) {
        showAlert(t('config.enter_model_name'), 'warning');
        return;
    }
    let func = 0;
    if (document.getElementById('modelFuncImage').checked) func |= 0x01;
    if (document.getElementById('modelFuncVideo').checked) func |= 0x02;
    if (document.getElementById('modelFuncAudio').checked) func |= 0x04;
    const config = {
        id: document.getElementById('modelFuncId').value,
        type: type,
        regex: regex,
        func: func,
        visibility: document.getElementById('modelFuncVisibility').value || null,
        overrideLocal: document.getElementById('modelFuncOverrideLocal').checked
    };
    const mode = document.getElementById('thinkModeSelect').value;
    if (mode === 'single') {
        var boolParam = document.getElementById('thinkBoolParam').value;
        config.thinkParamName = boolParam;
        config.thinkingLevel = boolParam === 'thinking' ? ['no_think', 'think'] : ['false', 'true'];
        config.enableThinking = true;
        config.enableThinkingParamName = boolParam;
    } else if (mode === 'multi') {
        var levels = getSelectedThinkLevels();
        var onCount = levels.filter(function(l) { return THINK_LEVELS_OFF.indexOf(l) === -1; }).length;
        if (onCount < 1) {
            showAlert(t('config.think_level_on_required'), 'error');
            return;
        }
        config.thinkParamName = 'reasoning_effort';
        config.thinkingLevel = levels;
    } else if (mode === 'hybrid') {
        var boolParam = document.getElementById('thinkBoolParam').value;
        var levels = getSelectedThinkLevels();
        if (levels.length < 1) {
            showAlert(t('config.think_level_required'), 'error');
            return;
        }
        config.thinkParamName = 'reasoning_effort';
        config.thinkingLevel = levels;
        config.enableThinking = true;
        config.enableThinkingParamName = boolParam;
    } else if (mode === 'thinking_mode') {
        var levels = getSelectedThinkLevels();
        if (levels.length < 2) {
            showAlert(t('config.think_level_min_two'), 'error');
            return;
        }
        config.thinkParamName = 'thinking_mode';
        config.thinkingLevel = levels;
    } else {
        config.thinkParamName = '';
        config.thinkingLevel = [];
        config.enableThinking = false;
        config.enableThinkingParamName = '';
    }
    try {
        const result = await api('/chat/system-setting/model-func-configs/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });
      if (result.success) {
            closeModelFuncFormModal();
            loadModelFuncConfigList();
            loadApiConfigs();
            loadChatModels();
        } else {
            showAlert(result.msg || t('config.save_failed'), 'error');
        }
    } catch (error) {
        showAlert(t('config.save_failed') + ': ' + error.message, 'error');
    }
}

  async function deleteModelFunc(configId) {
    const action = await showDialog({ type: 'warn', title: t('config.delete_model_title'), message: t('config.delete_model_confirm'), buttons: [{ label: t('user.confirm_delete'), value: 'yes', primary: true }, { label: t('user.cancel'), value: 'cancel' }] }).catch(() => null);
    if (action !== 'yes') return;
    try {
        const result = await api('/chat/system-setting/model-func-configs/delete?configId=' + encodeURIComponent(configId), { method: 'POST' });
        if (result.success) {
            loadModelFuncConfigList();
            loadApiConfigs();
            loadChatModels();
        }
    } catch (error) {
        showAlert(t('config.delete_failed') + ': ' + error.message, 'error');
    }
}

async function moveModelFunc(configId, up) {
    try {
        const result = await api('/chat/system-setting/model-func-configs/move?configId=' + encodeURIComponent(configId) + '&up=' + up, { method: 'POST' });
        if (result.success) {
            loadModelFuncConfigList();
        }
    } catch (error) {
        showAlert(t('config.operation_failed') + ': ' + error.message, 'error');
    }
}

async function loadSessionConfigList() {
    try {
        const result = await api('/chat/system-setting/session-configs', { method: 'POST' });
        if (result.success) {
            renderSessionConfigList(result.data);
        }
    } catch (error) {
        console.error('Load session config list failed:', error);
    }
}

function renderSessionConfigList(configs) {
    const container = document.getElementById('sessionConfigList');
    if (configs.length === 0) {
        container.innerHTML = '<div class="empty-state"><i class="bi bi-chat-dots"></i><p>' + t('config.no_session') + '</p></div>';
        return;
    }
    container.innerHTML = configs.map(config => {
        const roleNames = { admin: t('config.admin'), user: t('config.user'), guest: t('config.guest') };
        return `
        <div class="api-config-item">
            <div class="api-config-info">
                <h4><i class="bi bi-shield-check"></i> ${roleNames[config.role] || config.role}</h4>
                <div class="session-config-grid">
                    <span>${t('config.attachment', {size: config.maxAttachmentSize, count: config.maxAttachments})}</span>
                    <span>${t('config.text', {size: config.maxTextAttachmentSize, count: config.maxTextAttachments})}</span>
                    <span>${t('config.max_message', {count: config.maxMessageCount})}</span>
                </div>
            </div>
            <div class="api-config-actions">
                <button class="btn-edit" onclick="editSessionConfig('${config.id}')" title="${t('config.edit')}"><i class="bi bi-pencil"></i></button>
            </div>
        </div>`;
    }).join('');
}

async function editSessionConfig(id) {
    try {
        const result = await api('/chat/system-setting/session-configs', { method: 'POST' });
        if (!result.success) return;
        const item = result.data.find(c => c.id === id);
        if (!item) return;
        document.getElementById('sessionConfigFormTitle').textContent = t('config.edit_session');
        document.getElementById('sessionConfigId').value = item.id;
        document.getElementById('sessionConfigRole').value = item.role;
        document.getElementById('sessionConfigRole').disabled = true;
        document.getElementById('sessionConfigMaxSize').value = item.maxAttachmentSize;
        document.getElementById('sessionConfigMaxAttachments').value = item.maxAttachments;
        document.getElementById('sessionConfigMaxTextSize').value = item.maxTextAttachmentSize;
        document.getElementById('sessionConfigMaxTextAttachments').value = item.maxTextAttachments;
        document.getElementById('sessionConfigMaxMessages').value = item.maxMessageCount;
        document.getElementById('sessionConfigPinnedLimit').value = item.pinnedSessionLimit;
        document.getElementById('sessionConfigFormModal').classList.add('active');
    } catch (error) {
        showAlert(t('config.load_failed'), 'error');
    }
}

function closeSessionConfigFormModal() {
    document.getElementById('sessionConfigFormModal').classList.remove('active');
}

async function saveSessionConfig() {
    const role = document.getElementById('sessionConfigRole').value;
    const maxSize = parseInt(document.getElementById('sessionConfigMaxSize').value);
    const maxAttachments = parseInt(document.getElementById('sessionConfigMaxAttachments').value);
    const maxTextSize = parseInt(document.getElementById('sessionConfigMaxTextSize').value);
    const maxTextAttachments = parseInt(document.getElementById('sessionConfigMaxTextAttachments').value);
    const maxMessages = parseInt(document.getElementById('sessionConfigMaxMessages').value);
    const pinnedLimit = parseInt(document.getElementById('sessionConfigPinnedLimit').value);
    if (!maxSize || !maxAttachments || !maxTextSize || !maxTextAttachments || !maxMessages || !pinnedLimit) {
        showAlert(t('config.fill_all'), 'warning');
        return;
    }
    const config = {
        id: document.getElementById('sessionConfigId').value,
        role: role,
        maxAttachmentSize: maxSize,
        maxAttachments: maxAttachments,
        maxTextAttachmentSize: maxTextSize,
        maxTextAttachments: maxTextAttachments,
        maxMessageCount: maxMessages,
        pinnedSessionLimit: pinnedLimit
    };
    try {
        const result = await api('/chat/system-setting/session-configs/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });
      if (result.success) {
            closeSessionConfigFormModal();
            loadSessionConfigList();
            await refreshCurrentSessionConfig();
        } else {
            showAlert(result.msg || t('config.save_failed'), 'error');
        }
    } catch (error) {
        showAlert(t('config.save_failed') + ': ' + error.message, 'error');
    }
}

async function refreshCurrentSessionConfig() {
    try {
        const result = await api('/chat/system-setting/session-configs', { method: 'POST' });
        if (result.success && result.data && result.data.length > 0) {
            const userResult = await fetch(`${API_BASE}/file/user/current?sessionId=${getCurrentSessionId()}`);
            const userData = await userResult.json();
            if (userData.success && userData.data) {
                const role = userData.data.role;
                const config = result.data.find(c => c.role === role);
                if (config) {
                    sessionConfig = config;
                }
            }
        }
    } catch (error) {
        console.error('Refresh session config failed:', error);
    }
}

async function loadSessionConfig() {
    try {
        const userResult = await fetch(`${API_BASE}/file/user/current?sessionId=${getCurrentSessionId()}`);
        const userData = await userResult.json();
        if (userData.success && userData.data) {
            renderUserProfile(userData.data);
        }
        const result = await api('/chat/system-setting/session-configs', { method: 'POST' });
        if (result.success && result.data.length > 0 && userData.data) {
            const role = userData.data.role;
            const config = result.data.find(c => c.role === role);
            if (config) {
                sessionConfig = config;
            }
        }
    } catch (error) {
        console.error('Load session config failed:', error);
    }
    sessionConfigLoaded = true;
}

// 自定义思考模式可选取的等级（关闭+强度档）
const THINK_LEVEL_OPTIONS = ['no_think', 'none', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max'];
// 关闭思考的等级标识
const THINK_LEVELS_OFF = ['no_think', 'none'];
// 等级排序序号
const THINK_LEVEL_ORDER = {
    'no_think': 0, 'none': 1, 'minimal': 2, 'low': 3, 'medium': 4, 'high': 5, 'xhigh': 6, 'max': 7
};
// thinking_mode 三态值（从低到高：disabled < adaptive < enabled）
const THINK_MODE_VALUES = ['disabled', 'adaptive', 'enabled'];
const THINK_MODE_ORDER = {
    'disabled': 0, 'adaptive': 1, 'enabled': 2
};

// 思考模式下拉框 hover 显示各模式详细信息
function setupThinkModeTips() {
    var sel = document.getElementById('thinkModeSelect');
    if (!sel || typeof t !== 'function' || typeof hasMsgKey !== 'function') return;
    Array.prototype.forEach.call(sel.options, function(opt) {
        if (!opt.value) return;
        var key = 'config.think_mode_' + opt.value + '_desc';
        if (hasMsgKey(key)) opt.title = t(key);
    });
    var cur = sel.options[sel.selectedIndex];
    if (cur && cur.value) sel.title = cur.title;
}

function onThinkModeChange() {
    var mode = document.getElementById('thinkModeSelect').value;
    var sel = document.getElementById('thinkModeSelect');
    var descKey = 'config.think_mode_' + mode + '_desc';
    sel.title = mode && typeof hasMsgKey === 'function' && hasMsgKey(descKey) ? t(descKey) : '';
    var fields = document.getElementById('thinkModeFields');
    var boolField = document.getElementById('thinkBoolField');
    var levelField = document.getElementById('thinkLevelField');

    fields.style.display = mode ? 'flex' : 'none';
    boolField.style.display = 'none';
    levelField.style.display = 'none';

    if (mode === 'single') {
        boolField.style.display = 'block';
    } else if (mode === 'multi') {
        levelField.style.display = 'block';
        renderThinkLevelOptions(false);
    } else if (mode === 'hybrid') {
        boolField.style.display = 'block';
        levelField.style.display = 'block';
        renderThinkLevelOptions(true);
    } else if (mode === 'thinking_mode') {
        levelField.style.display = 'block';
        renderThinkLevelOptionsForThinkingMode();
    }
}

function renderThinkLevelOptions(isHybrid) {
    var container = document.getElementById('thinkLevelOptions');
    if (!container) return;
    var options = isHybrid
        ? THINK_LEVEL_OPTIONS.filter(function(l) { return THINK_LEVELS_OFF.indexOf(l) === -1; })
        : THINK_LEVEL_OPTIONS;
    container.innerHTML = options.map(function(level) {
        return '<label class="form-check form-check-inline"><input class="form-check-input" type="checkbox" value="' + level + '"> ' + level + '</label>';
    }).join('');
}

function renderThinkLevelOptionsForThinkingMode() {
    var container = document.getElementById('thinkLevelOptions');
    if (!container) return;
    container.innerHTML = THINK_MODE_VALUES.map(function(val) {
        return '<label class="form-check form-check-inline"><input class="form-check-input" type="checkbox" value="' + val + '"> ' + val + '</label>';
    }).join('');
}

function getSelectedThinkLevels() {
    var checked = Array.from(document.querySelectorAll('#thinkLevelOptions input[type=checkbox]:checked'))
        .map(function(cb) { return cb.value; });
    return checked.sort(function(a, b) {
        var oa = THINK_LEVEL_ORDER[a] ?? THINK_MODE_ORDER[a] ?? 99;
        var ob = THINK_LEVEL_ORDER[b] ?? THINK_MODE_ORDER[b] ?? 99;
        return oa - ob;
    });
}

function setSelectedThinkLevels(levels) {
    var set = new Set(levels || []);
    document.querySelectorAll('#thinkLevelOptions input[type=checkbox]').forEach(function(cb) {
        cb.checked = set.has(cb.value);
    });
}

function renderUserProfile(user) {
    const displayName = user.nickname && user.nickname.trim() ? user.nickname : user.username;
    const nameEl = document.getElementById('currentUser');
    if (nameEl) nameEl.textContent = displayName;
    isAdmin = user.role === UserRoleEnum.admin;
    const btnConfig = document.querySelector('.btn-config');
    if (btnConfig) btnConfig.style.display = isAdmin ? '' : 'none';
}

window.onload = async function() {
    if (window.__chatInitialized) return;
    window.__chatInitialized = true;
    initChatApp();
};

window.initChatApp = async function() {
    if (window.__chatInitialized) return;
    window.__chatInitialized = true;
    initScrollDetection();
    setupThinkModeTips();
    loadApiConfigs();
    await loadSessionConfig();
    // 加载发送快捷键设置
    const chatCtx = (currentUserData && currentUserData.userContext && currentUserData.userContext.chatContext) || {};
    window.sendShortcutKey = chatCtx.sendShortcutKey || 'ctrl+enter';
    window.attachReasoningContent = !!chatCtx.attachReasoningContent;
    window.userSystemPrompt = chatCtx.systemPrompt || '';
    if (typeof updateAttachReasoningButton === 'function') updateAttachReasoningButton();
    if (typeof updateInputPlaceholder === 'function') updateInputPlaceholder();
    loadChatModels().then(() => {
        loadSessionList();
    });
};
