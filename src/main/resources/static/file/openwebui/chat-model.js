async function loadApiConfigs() {
    try {
        const result = await api('/chat/system-setting/api-configs', { method: 'POST' });
        if (result.success) {
            apiConfigs = result.data;
        }
    } catch (error) {
        console.error('Load API configs failed:', error);
    }
}

async function loadChatModels() {
    try {
        const result = await api('/chat/model/list', { method: 'POST' });
        if (result.success) {
            chatModels = result.data;
            renderModelSelect();
        }
    } catch (error) {
        console.error('Load chat models failed:', error);
    }
}

function renderModelSelect() {
    const dropdown = document.getElementById('modelDropdown');
    const nameSpan = document.getElementById('modelSelectorName');

    const currentModelName = selectedChatModel ? selectedChatModel.modelName : null;
    let activeIndex = 0;
    if (currentModelName) {
        const found = chatModels.findIndex(m => m.modelName === currentModelName);
        if (found >= 0) {
            activeIndex = found;
            selectedChatModel = chatModels[found];
        }
    }

    dropdown.innerHTML = `<div class="model-list-scroll">${chatModels.map((model, index) => {
        const activeClass = index === activeIndex ? 'active' : '';
        return `<div class="model-item ${activeClass}" data-index="${index}" onclick="selectModel(${index})" onmouseenter="showModelTooltip(event, ${index})" onmouseleave="hideModelTooltip()">
            <div class="model-item-name">${escapeHtml(model.modelName)}</div>
        </div>`;
    }).join('')}</div><div class="model-detail-tooltip" id="modelTooltip"></div>`;

    if (chatModels.length > 0) {
        if (!selectedChatModel) {
            selectedChatModel = chatModels[0];
        }
        selectedApiConfigId = selectedChatModel.openApiLLMConfig ? selectedChatModel.openApiLLMConfig.baseUrl : null;
        nameSpan.textContent = selectedChatModel.modelName;
        updateAttachButtons();
        updateContextUsage();
        updateThinkButton();
    } else {
        nameSpan.textContent = t('chat.no_available_models');
    }
}

function showModelTooltip(event, index) {
    const model = chatModels[index];
    const tooltip = document.getElementById('modelTooltip');
    if (!tooltip) return;

    const supportThinking = model.thinkConfig && model.thinkConfig.paramName;
    tooltip.innerHTML = `
        <div class="tooltip-name">${escapeHtml(model.modelName)}</div>
        <div class="tooltip-row"><span class="tooltip-label">${t('chat.model_context')}</span><span>${model.contentLength || '-'}</span></div>
        <div class="tooltip-row"><span class="tooltip-label">${t('chat.model_image')}</span><span class="tooltip-tag ${(model.multi & 0x01) ? 'support' : 'no-support'}">${(model.multi & 0x01) ? t('chat.supported') : t('chat.not_supported')}</span></div>
        <div class="tooltip-row"><span class="tooltip-label">${t('chat.model_video')}</span><span class="tooltip-tag ${(model.multi & 0x02) ? 'support' : 'no-support'}">${(model.multi & 0x02) ? t('chat.supported') : t('chat.not_supported')}</span></div>
        <div class="tooltip-row"><span class="tooltip-label">${t('chat.model_audio')}</span><span class="tooltip-tag ${(model.multi & 0x04) ? 'support' : 'no-support'}">${(model.multi & 0x04) ? t('chat.supported') : t('chat.not_supported')}</span></div>
        <div class="tooltip-row"><span class="tooltip-label">${t('chat.model_thinking')}</span><span class="tooltip-tag ${supportThinking ? 'support' : 'no-support'}">${supportThinking ? t('chat.supported') : t('chat.not_supported')}</span></div>
    `;

    const item = event.currentTarget;
    const dropdown = document.querySelector('.model-dropdown');
    const itemRect = item.getBoundingClientRect();
    const dropdownRect = dropdown.getBoundingClientRect();
    tooltip.style.top = (itemRect.top - dropdownRect.top) + 'px';
    tooltip.style.left = (itemRect.right - dropdownRect.left) + 'px';
    tooltip.style.display = 'block';
}

function hideModelTooltip() {
    const tooltip = document.getElementById('modelTooltip');
    if (tooltip) tooltip.style.display = 'none';
}

function toggleModelDropdown() {
    const dropdown = document.getElementById('modelDropdown');
    dropdown.classList.toggle('open');
}

function selectModel(index, skipCheck) {
    if (isGenerating && !skipCheck) {
        showAlert(t('chat.cannot_switch_model'), 'warning');
        document.getElementById('modelDropdown').classList.remove('open');
        return;
    }
    selectedChatModel = chatModels[index] || null;
    if (selectedChatModel && selectedChatModel.openApiLLMConfig) {
        selectedApiConfigId = selectedChatModel.openApiLLMConfig.baseUrl;
    }
    document.getElementById('modelSelectorName').textContent = selectedChatModel ? selectedChatModel.modelName : '';
    document.querySelectorAll('.model-item').forEach((item, i) => {
        item.classList.toggle('active', i === index);
    });
    document.getElementById('modelDropdown').classList.remove('open');
    updateAttachButtons();
    updateContextUsage();
    updateThinkButton();
    if (typeof syncRuntimeSidebar === 'function') syncRuntimeSidebar();
}

document.addEventListener('click', (e) => {
    const selector = document.getElementById('modelSelector');
    if (selector && !selector.contains(e.target)) {
        document.getElementById('modelDropdown').classList.remove('open');
    }
});
