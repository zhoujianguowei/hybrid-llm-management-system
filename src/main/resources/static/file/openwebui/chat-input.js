let sessionCreatingFlag = false;

function sendMessage() {
    if (isGenerating) {
        showAlert(t('chat.wait_for_previous'), 'warning');
        return;
    }
    const message = document.getElementById('messageInput').value.trim();
    if (!message && !currentMediaList.length && !currentTextFileList.length) return;

    if (!selectedChatModel) {
        showAlert(t('chat.select_model_first'), 'warning');
        return;
    }

    if (sessionConfig) {
        if (currentMediaList.length > sessionConfig.maxAttachments) {
            showAlert(`${t('chat.media_limit_exceeded')}${sessionConfig.maxAttachments}${t('chat.unit_count')}`, 'warning');
            return;
        }
        if (currentTextFileList.length > sessionConfig.maxTextAttachments) {
            showAlert(`${t('chat.text_limit_exceeded')}${sessionConfig.maxTextAttachments}${t('chat.unit_count')}`, 'warning');
            return;
        }
    }

    if (!currentChatSessionId) {
        if (sessionCreatingFlag) {
            // 会话创建尚未完成时给出提示，避免点击发送无任何反馈
            showAlert(t('chat.creating_session'), 'warning');
            return;
        }
        sessionCreatingFlag = true;
        newChat().then(() => {
            sessionCreatingFlag = false;
            if (isGenerating) return;
            sendChatMessage(message);
        }).catch(() => {
            sessionCreatingFlag = false;
        });
    } else {
        sendChatMessage(message);
    }
}

function sendChatMessage(message) {
    const ws = getCurrentWs();
    if (!ws || ws.readyState !== WebSocket.OPEN) {
        showAlert(t('chat.connection_disconnected_refresh'), 'error');
        return;
    }
    createUserMessage(message, currentMediaList);

    const imageUrlList = currentMediaList.filter(m => m.type === 'image').map(m => m.path);
    const videoUrlList = currentMediaList.filter(m => m.type === 'video').map(m => m.path);
    const audioUrlList = currentMediaList.filter(m => m.type === 'audio').map(m => m.path);

    const requestData = {
        type: 'chat',
        sessionId: getCurrentSessionId(),
        apiConfigId: selectedApiConfigId,
        message: message,
        imageUrlList: imageUrlList,
        videoUrlList: videoUrlList,
        audioUrlList: audioUrlList,
        chatMediaTextList: currentTextFileList.map(f => ({ url: f.path, name: f.name, relativePath: f.relativePath || '' })),
        chatId: currentChatSessionId,
        chatModel: selectedChatModel,
        thinkingLevel: null,
        hybridThinking: (selectedChatModel && selectedChatModel.thinkConfig
                && (selectedChatModel.thinkConfig.paramName || selectedChatModel.thinkConfig.enableThinking))
            ? getChatThinkingLevel() : null,
        chatRuntimeConfig: buildRequestRuntimeConfig()
    };
    
    ws.send(JSON.stringify(requestData));

    lastUserMessage = message;
    updateSessionTitleOnFirstMessage(currentChatSessionId, message);
    
    
    document.getElementById('messageInput').value = '';
    autoResizeInput();
    removeImage();
    
    document.querySelector('.btn-send').style.display = 'none';
    document.getElementById('btnStop').style.display = 'flex';
    
    isRecovering = false;
}

function updateSessionTitleOnFirstMessage(chatId, message) {
    const session = sessions.find(s => s.chatId === chatId);
    if (session && session.title === 'chat.new_conversation' && message) {
        session.title = message.length > 10 ? message.substring(0, 10) : message;
        renderSessionList();
        renderPinnedList();
    }
}

function updateSessionMessageCount(chatId, count) {
    const session = sessions.find(s => s.chatId === chatId);
    if (session) {
        session.messageCount = (session.messageCount || 0) + count;
        renderSessionList();
    }
    const pinnedSession = pinnedSessions.find(s => s.chatId === chatId);
    if (pinnedSession) {
        pinnedSession.messageCount = (pinnedSession.messageCount || 0) + count;
        renderPinnedList();
    }
}

function stopGeneration() {
    const ws = getCurrentWs();
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'stop', chatId: currentChatSessionId }));
    }
    document.getElementById('btnStop').style.display = 'none';
    const btnSend = document.querySelector('.btn-send');
    btnSend.style.display = 'flex';
    btnSend.classList.add('generating');
}

function handleKeyPress(event) {
    if (window.sendShortcutKey === 'enter') {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            sendMessage();
        }
    } else {
        if (event.key === 'Enter' && event.ctrlKey) {
            event.preventDefault();
            sendMessage();
        }
    }
}

function updateInputPlaceholder() {
    const input = document.getElementById('messageInput');
    if (input) {
        if (window.sendShortcutKey === 'enter') {
            input.placeholder = t('chat.input_placeholder_enter');
        } else {
            input.placeholder = t('chat.input_placeholder_ctrl');
        }
    }
}

async function handleMediaSelect(event) {
    const files = Array.from(event.target.files);
    if (!files.length) return;
    if (!sessionConfigLoaded) {
        showAlert(t('chat.config_loading'), 'warning');
        event.target.value = '';
        return;
    }
    if (sessionConfig && currentMediaList.length + files.length > sessionConfig.maxAttachments) {
        showAlert(t('chat.media_limit', {count: sessionConfig.maxAttachments}), 'warning');
        event.target.value = '';
        return;
    }
    for (const file of files) {
        if (sessionConfig && file.size > sessionConfig.maxAttachmentSize * 1024 * 1024) {
            showAlert(`${t('chat.file_size_exceeded')}${sessionConfig.maxAttachmentSize}MB`, 'warning');
            continue;
        }
        let type = 'image';
        if (file.type.startsWith('video/')) type = 'video';
        else if (file.type.startsWith('audio/')) type = 'audio';
        const formData = new FormData();
        formData.append('file', file);
        formData.append('maxResolution', '1024');
        formData.append('quality', '0.85');
        try {
            const response = await fetch(`${API_BASE}/chat/media/upload?${getSessionParam()}`, {
                method: 'POST',
                body: formData
            });
            const result = await response.json();
            if (result.success) {
                currentMediaList.push({
                    type: type,
                    path: result.data,
                    previewUrl: URL.createObjectURL(file),
                    name: file.name
                });
                renderMediaPreview();
            } else {
                showAlert(result.msg || t('chat.file_upload_failed'), 'error');
            }
        } catch (error) {
            console.error('Media upload failed:', error);
            showAlert(t('chat.file_upload_failed'), 'error');
        }
    }
    event.target.value = '';
}

async function removeMediaItem(index) {
    const item = currentMediaList[index];
    if (item && item.path) {
        try {
            await api(`/chat/media/delete?url=${encodeURIComponent(item.path)}`, { method: 'POST' });
        } catch (error) {
            console.error('Failed to delete media:', error);
        }
    }
    currentMediaList.splice(index, 1);
    renderMediaPreview();
}

function removeImage() {
    currentMediaList = [];
    currentTextFileList = [];
    renderMediaPreview();
}

function openImageLightbox(src) {
    const existing = document.querySelector('.image-lightbox');
    if (existing) return;
    const lightbox = document.createElement('div');
    lightbox.className = 'image-lightbox';
    lightbox.onclick = closeImageLightbox;
    lightbox.innerHTML = `<img src="${src}" alt="preview">`;
    document.body.appendChild(lightbox);
    document.addEventListener('keydown', handleLightboxKeydown);
}

function handleLightboxKeydown(e) {
    if (e.key === 'Escape') {
        closeImageLightbox();
    }
}

function closeImageLightbox() {
    const lightbox = document.querySelector('.image-lightbox');
    if (lightbox) {
        lightbox.remove();
    }
    document.removeEventListener('keydown', handleLightboxKeydown);
}

function handleImageError(img) {
    img.outerHTML = '<div class="media-fallback"><i class="bi bi-x-lg"></i></div>';
}

function updateAttachButtons() {
    const btn = document.getElementById('btnAttachMedia');
    const input = document.getElementById('mediaInput');
    if (!selectedChatModel) {
        btn.disabled = true;
        btn.style.opacity = '0.4';
        btn.style.cursor = 'not-allowed';
        return;
    }
    btn.disabled = false;
    btn.style.opacity = '1';
    btn.style.cursor = 'pointer';
    const multi = selectedChatModel.multi || 0;
    const hasImage = !!(multi & 0x01);
    const hasVideo = !!(multi & 0x02);
    const hasAudio = !!(multi & 0x04);
    const accepts = [];
    if (hasImage) accepts.push('image/jpeg,image/jpg,image/png');
    if (hasVideo) accepts.push('video/mp4');
    if (hasAudio) accepts.push('audio/*');
    input.accept = accepts.join(',');
}

function handleAttachClick(event) {
    event.stopPropagation();
    const btn = document.getElementById('btnAttachMedia');
    if (btn.disabled) return;
    const dropdown = document.getElementById('attachDropdown');
    const items = [];
    items.push(`<div class="attach-dropdown-item" onclick="handleTextFileClick()"><i class="bi bi-file-earmark-text"></i> ${t('chat.upload_text_file')}</div>`);
    items.push(`<div class="attach-dropdown-item" onclick="handleFolderClick()"><i class="bi bi-folder"></i> ${t('chat.upload_folder')}</div>`);
    if (selectedChatModel && selectedChatModel.multi) {
        const multi = selectedChatModel.multi;
        if (multi & 0x01) items.push(`<div class="attach-dropdown-item" onclick="handleMediaFileClick('image')"><i class="bi bi-image"></i> ${t('chat.upload_image')}</div>`);
        if (multi & 0x02) items.push(`<div class="attach-dropdown-item" onclick="handleMediaFileClick('video')"><i class="bi bi-camera-video"></i> ${t('chat.upload_video')}</div>`);
        if (multi & 0x04) items.push(`<div class="attach-dropdown-item" onclick="handleMediaFileClick('audio')"><i class="bi bi-music-note-beamed"></i> ${t('chat.upload_audio')}</div>`);
    }
    dropdown.innerHTML = items.join('');
    dropdown.classList.toggle('active');
}

function handleMediaFileClick(type) {
    document.getElementById('attachDropdown').classList.remove('active');
    const input = document.getElementById('mediaInput');
    if (type === 'image') input.accept = 'image/jpeg,image/jpg,image/png,image/gif,image/webp';
    else if (type === 'video') input.accept = 'video/mp4,video/webm,video/ogg';
    else if (type === 'audio') input.accept = 'audio/*';
    input.click();
}

function handleTextFileClick() {
    document.getElementById('attachDropdown').classList.remove('active');
    document.getElementById('textInput').click();
}

function handleFolderClick() {
    document.getElementById('attachDropdown').classList.remove('active');
    document.getElementById('folderInput').click();
}

document.addEventListener('click', function(e) {
    if (!e.target.closest('.attach-button-group')) {
        document.getElementById('attachDropdown').classList.remove('active');
    }
});

async function handlePasteImage(event) {
    const clipboardData = event.clipboardData;
    const clipboardFiles = clipboardData.files;

    // 1. 优先处理从 IDEA/文件夹复制粘贴的真实文件对象
    if (clipboardFiles && clipboardFiles.length > 0) {
        const allExts = [];
        for (let exts of Object.values(TEXT_FILE_EXTENSIONS)) allExts.push(...exts);
        const imageExts = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.tiff'];
        const textFiles = [];
        const imageFileObjs = [];
        for (let i = 0; i < clipboardFiles.length; i++) {
            const file = clipboardFiles[i];
            const ext = '.' + file.name.split('.').pop().toLowerCase();
            if (imageExts.includes(ext)) {
                imageFileObjs.push(file);
            } else if (allExts.includes(ext)) {
                textFiles.push(file);
            }
        }
        // 如果没有可处理的文件，放行让后续逻辑处理纯文本粘贴
        if (imageFileObjs.length === 0 && textFiles.length === 0) {
            return;
        }
        event.preventDefault();
        if (imageFileObjs.length > 0) {
            if (!selectedChatModel || !(selectedChatModel.multi & 0x01)) {
                showAlert(t('chat.image_not_supported'), 'warning');
                return;
            }
            const maxMediaTotal = sessionConfig ? sessionConfig.maxAttachments : 10;
            if (currentMediaList.length + imageFileObjs.length > maxMediaTotal) {
                showAlert(`${t('chat.media_limit_exceeded')}${maxMediaTotal}${t('chat.unit_count')}`, 'warning');
                return;
            }
            for (const file of imageFileObjs) {
                await uploadPastedImageFile(file);
            }
        }
        if (textFiles.length > 0) {
            const maxTextTotal = sessionConfig ? sessionConfig.maxTextAttachments : 10;
            if (currentTextFileList.length + textFiles.length > maxTextTotal) {
                showAlert(`${t('chat.text_limit_exceeded')}${maxTextTotal}${t('chat.unit_count')}`, 'warning');
                return;
            }
            for (const file of textFiles) {
                await uploadPastedTextFile(file);
            }
        }
        return;
    }

    // 2. 处理图片粘贴（截图、网页右键复制图片等）
    const items = clipboardData.items;
    const imageFiles = [];
    for (const item of items) {
        if (item.type.startsWith('image/')) {
            const file = item.getAsFile();
            if (file) imageFiles.push(file);
        }
    }

    // 3. 上传截图/网页复制的图片
    if (imageFiles.length > 0) {
        event.preventDefault();
        if (!selectedChatModel || !(selectedChatModel.multi & 0x01)) {
            showAlert(t('chat.image_not_supported'), 'warning');
            return;
        }
        if (!sessionConfigLoaded) {
        showAlert(t('chat.config_loading'), 'warning');
            return;
        }
        if (sessionConfig && currentMediaList.length + imageFiles.length > sessionConfig.maxAttachments) {
        showAlert(`${t('chat.media_limit_exceeded')}${sessionConfig.maxAttachments}${t('chat.unit_count')}`, 'warning');
            return;
        }
        for (const file of imageFiles) {
            await uploadPastedImageFile(file);
        }
        return;
    }

    // 4. 纯文本粘贴：不做任何处理，让浏览器默认行为将文本插入输入框
}

async function uploadPastedImageFile(file) {
    if (sessionConfig && file.size > sessionConfig.maxAttachmentSize * 1024 * 1024) {
            showAlert(`${t('chat.file_size_exceeded')}${sessionConfig.maxAttachmentSize}MB`, 'warning');
            return;
        }
        const formData = new FormData();
        formData.append('file', file);
        formData.append('maxResolution', '1024');
        formData.append('quality', '0.85');
        try {
            const response = await fetch(`${API_BASE}/chat/media/upload?${getSessionParam()}`, {
                method: 'POST',
                body: formData
            });
            const result = await response.json();
            if (result.success) {
                currentMediaList.push({
                    type: 'image',
                    path: result.data,
                    previewUrl: URL.createObjectURL(file),
                    name: file.name
                });
                renderMediaPreview();
            } else {
                showAlert(result.msg || t('chat.image_upload_failed'), 'error');
            }
        } catch (error) {
            console.error('Paste image upload failed:', error);
            showAlert(t('chat.image_upload_failed'), 'error');
        }
}

async function uploadPastedTextFile(file) {
    const maxTextSize = sessionConfig ? sessionConfig.maxTextAttachmentSize * 1024 : 10 * 1024 * 1024;
    if (file.size > maxTextSize) {
        showAlert(t('chat.file_size_limit'), 'warning');
        return;
    }
    const formData = new FormData();
    formData.append('file', file);
    try {
        const response = await fetch(`${API_BASE}/chat/media/upload?${getSessionParam()}`, {
            method: 'POST',
            body: formData
        });
        const result = await response.json();
        if (result.success) {
            currentTextFileList.push({ path: result.data, name: file.name, relativePath: '' });
            renderMediaPreview();
        } else {
            showAlert(result.msg || `${t('chat.file_upload_failed')}: ${file.name}`, 'error');
        }
    } catch (error) {
        console.error('Paste text file upload failed:', error);
        showAlert(`${t('chat.file_upload_failed')}: ${file.name}`, 'error');
    }
}

function autoResizeInput() {
    const input = document.getElementById('messageInput');
    const maxHeight = window.innerHeight / 3;
    input.style.height = 'auto';
    requestAnimationFrame(() => {
        const newHeight = Math.min(input.scrollHeight, maxHeight);
        input.style.height = newHeight + 'px';
        input.style.overflowY = input.scrollHeight > maxHeight ? 'auto' : 'hidden';
    });
}

function handleTextFileSelect(event) {
    const files = Array.from(event.target.files);
    if (!files.length) return;
    event.target.value = '';
    const allExts = [];
    for (let exts of Object.values(TEXT_FILE_EXTENSIONS)) allExts.push(...exts);
    const maxTextSize = sessionConfig ? sessionConfig.maxTextAttachmentSize * 1024 : 10 * 1024 * 1024;
    const maxTextTotal = sessionConfig ? sessionConfig.maxTextAttachments : 10;
    if (currentTextFileList.length + files.length > maxTextTotal) {
        showAlert(`${t('chat.text_limit_exceeded')}${maxTextTotal}${t('chat.unit_count')}`, 'warning');
        return;
    }
    for (const file of files) {
        const ext = '.' + file.name.split('.').pop().toLowerCase();
        if (!allExts.includes(ext)) {
            showAlert(`${t('chat.unsupported_file_type')}: ${file.name}`, 'warning');
            continue;
        }
        if (file.size > maxTextSize) {
            showAlert(`${t('chat.file_size_exceeded')}${sessionConfig ? sessionConfig.maxTextAttachmentSize : 10240}KB: ${file.name}`, 'warning');
            continue;
        }
        const formData = new FormData();
        formData.append('file', file);
        fetch(`${API_BASE}/chat/media/upload?${getSessionParam()}`, { method: 'POST', body: formData })
            .then(r => r.json())
            .then(result => {
                if (result.success) {
                    currentTextFileList.push({ path: result.data, name: file.name, relativePath: '' });
                    renderMediaPreview();
                } else {
                    showAlert(result.msg || t('chat.file_upload_failed'), 'error');
                }
            })
            .catch(() => showAlert(t('chat.file_upload_failed'), 'error'));
    }
}

function removeTextFileItem(index) {
    const item = currentTextFileList[index];
    if (item && item.path) {
        try {
            api(`/chat/media/delete?url=${encodeURIComponent(item.path)}`, { method: 'POST' });
        } catch (error) {
            console.error('Failed to delete text file:', error);
        }
    }
    currentTextFileList.splice(index, 1);
    renderMediaPreview();
}

function renderMediaPreview() {
    const container = document.getElementById('mediaPreview');
    if (!currentMediaList.length && !currentTextFileList.length) {
        container.style.display = 'none';
        return;
    }
    container.style.display = 'flex';
    container.style.flexDirection = 'column';
    let html = '';
    if (currentTextFileList.length) {
        html += '<div class="preview-row preview-text-row">';
        html += currentTextFileList.map((item, i) => {
            const titleText = item.relativePath ? item.relativePath + '/' + item.name : item.name;
            return `<div class="text-file-preview-item" data-path="${escapeAttr(item.path)}" data-name="${escapeAttr(item.name)}" onclick="previewTextFileFromData(this)"><i class="bi bi-file-earmark-text"></i><span class="text-file-name" title="${escapeAttr(titleText)}">${escapeHtml(item.name)}</span><i class="bi bi-eye text-file-preview-icon"></i><button class="btn-remove-text-file" onclick="event.stopPropagation(); removeTextFileItem(${i})"><i class="bi bi-x"></i></button></div>`;
        }).join('');
        html += '</div>';
    }
    if (currentMediaList.length) {
        html += '<div class="preview-row preview-media-row">';
        html += currentMediaList.map((item, i) => {
            let preview = '';
            if (item.type === 'image') {
                preview = `<img src="${escapeAttr(item.previewUrl)}" alt="${escapeAttr(item.name)}" data-src="${escapeAttr(item.previewUrl)}" onclick="openImageLightboxFromData(this)">`;
            } else if (item.type === 'video') {
                preview = `<div class="media-thumb video-thumb"><i class="bi bi-camera-video"></i></div>`;
            } else if (item.type === 'audio') {
                preview = `<div class="media-thumb audio-thumb"><i class="bi bi-music-note-beamed"></i></div>`;
            }
            return `<div class="media-preview-item">${preview}<button class="btn-remove-media" onclick="removeMediaItem(${i})"><i class="bi bi-x"></i></button></div>`;
        }).join('');
        html += '</div>';
    }
    container.innerHTML = html;
}

function previewTextFileFromData(el) {
    const url = el.dataset.path || '';
    const name = el.dataset.name || '';
    previewTextFile(url, name);
}

function openImageLightboxFromData(el) {
    openImageLightbox(el.dataset.src || '');
}

async function previewTextFile(url, fileName) {
    event.stopPropagation();
    const modal = document.getElementById('textPreviewModal');
    const titleEl = document.getElementById('textPreviewTitle');
    const contentEl = document.getElementById('textPreviewContent');
    titleEl.innerHTML = `<i class="bi bi-file-earmark-text"></i> ${escapeHtml(fileName)}`;
    contentEl.textContent = t('chat.loading');
    modal.classList.add('active');
    try {
        const resp = await fetch(`${API_BASE}/chat/media/text?url=${encodeURIComponent(url)}&${getSessionParam()}`);
        const result = await resp.json();
        if (result.success) {
            contentEl.textContent = result.data;
        } else {
            contentEl.textContent = t('chat.load_failed') + ': ' + (result.msg || t('chat.unknown_error'));
        }
    } catch (e) {
        contentEl.textContent = t('chat.load_failed') + ': ' + e.message;
    }
}

function closeTextPreviewModal() {
    document.getElementById('textPreviewModal').classList.remove('active');
}
