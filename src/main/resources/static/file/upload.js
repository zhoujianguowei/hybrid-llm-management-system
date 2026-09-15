let uploadFileQueue = [];
const MAX_CONCURRENT_UPLOADS = 3;
let activeUploads = 0;
let currentListTab = 'active';
let conflictChoice = '';
let renderUploadListRAF = null;
let renderUploadListDirty = false;
const lastProgressUpdateTimeMap = new Map();
let cachedUploadTasks = null;
let lastUploadedDataHash = '';
let lastUploadListFetchTime = 0;
let isUploadModalOpen = false;

function showUploadModal() {
    const section = document.getElementById('uploadFileSection');
    if (isRootView) {
        section.classList.add('hidden');
    } else {
        section.classList.remove('hidden');
        document.getElementById('uploadTargetPath').value = currentPath || '/';
    }
    document.getElementById('uploadModal').classList.add('active');
    isUploadModalOpen = true;
    lastUploadListFetchTime = 0;
    lastUploadedDataHash = '';
    loadUploadTasks();
    renderUploadListSync();
}

function closeUploadModal() {
    document.getElementById('uploadModal').classList.remove('active');
    isUploadModalOpen = false;
}

function setupUploadDragDrop() {
    const area = document.getElementById('uploadFileArea');
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(ev => {
        area.addEventListener(ev, e => { e.preventDefault(); e.stopPropagation(); }, false);
    });
    area.addEventListener('dragenter', () => area.classList.add('dragover'));
    area.addEventListener('dragover', () => area.classList.add('dragover'));
    area.addEventListener('dragleave', () => area.classList.remove('dragover'));
    area.addEventListener('drop', e => {
        area.classList.remove('dragover');
        const files = Array.from(e.dataTransfer.files);
        if (files.length > 0) { addFilesToQueue(files); }
    });
}

async function loadUploadTasks(force) {
    const now = Date.now();
    if (!force && now - lastUploadListFetchTime < 1000) return;
    lastUploadListFetchTime = now;
    try {
        const result = await api('/file/upload/list');
        if (result.success) {
            const dataStr = JSON.stringify(result.data || []);
            const dataChanged = dataStr !== lastUploadedDataHash;
            lastUploadedDataHash = dataStr;
            cachedUploadTasks = result.data;
            updateUploadBadge();
            if (dataChanged && isUploadModalOpen) renderUploadList();
        }
    } catch (err) { console.error('Load upload tasks error:', err); }
}

function switchUploadListTab(tab) {
    if (currentListTab === tab) return;
    currentListTab = tab;
    document.querySelectorAll('#uploadModal .upload-tab').forEach(el => el.classList.toggle('active', el.dataset.tab === tab));
    const progressing = document.getElementById('uploadListProgressing');
    const completed = document.getElementById('uploadListCompleted');
    if (tab === 'active') {
        progressing.style.display = '';
        completed.style.display = 'none';
    } else {
        progressing.style.display = 'none';
        completed.style.display = '';
    }
    renderUploadListSync();
}

function updateUploadBadge() {
    const badge = document.getElementById('uploadToolbarBadge');
    const activeTotal = countProgressing();
    if (activeTotal > 0) { badge.textContent = activeTotal; badge.style.display = ''; }
    else { badge.style.display = 'none'; }
}

function buildProgressingItems() {
    const items = [];
    uploadFileQueue.forEach((q, i) => {
        if (!q._deleted && q.status !== 'COMPLETED') {
            items.push(makeQueueItem(q, i));
        }
    });
    (cachedUploadTasks || []).forEach(t => {
        if (t.status !== 'COMPLETED' && !uploadFileQueue.some(q => !q._deleted && q.taskId === t.taskId)) {
            items.push(makeTaskItem(t));
        }
    });
    return items;
}

function buildCompletedItems() {
    const items = [];
    (cachedUploadTasks || []).forEach(t => {
        if (t.status === 'COMPLETED') {
            items.push(makeTaskItem(t));
        }
    });
    items.sort((a, b) => (b.time || 0) - (a.time || 0));
    if (items.length > 100) {
        items.length = 100;
    }
    uploadFileQueue.forEach((q, i) => {
        if (!q._deleted && q.status === 'COMPLETED' && !(cachedUploadTasks || []).some(t => t.taskId === q.taskId)) {
            items.push(makeQueueItem(q, i));
        }
    });
    items.sort((a, b) => (b.time || 0) - (a.time || 0));
    return items;
}

function countProgressing() {
    let count = 0;
    uploadFileQueue.forEach(q => { if (!q._deleted && q.status !== 'COMPLETED') count++; });
    (cachedUploadTasks || []).forEach(t => {
        if (t.status !== 'COMPLETED' && !uploadFileQueue.some(q => !q._deleted && q.taskId === t.taskId)) count++;
    });
    return count;
}

function countCompleted() {
    let serverCount = 0;
    (cachedUploadTasks || []).forEach(t => { if (t.status === 'COMPLETED') serverCount++; });
    let queueCount = 0;
    uploadFileQueue.forEach(q => {
        if (!q._deleted && q.status === 'COMPLETED' && !(cachedUploadTasks || []).some(t => t.taskId === q.taskId)) queueCount++;
    });
    return Math.min(serverCount, 100) + queueCount;
}

function updateTabCounts() {
    document.getElementById('activeTabCount').textContent = countProgressing();
    document.getElementById('completedTabCount').textContent = countCompleted();
    updateUploadBadge();
}

function renderUploadListImpl() {
    if (!isUploadModalOpen) return;
    updateTabCounts();

    const visibleTab = currentListTab;
    const progressingContainer = document.getElementById('uploadListProgressing');
    const completedContainer = document.getElementById('uploadListCompleted');

    if (visibleTab === 'active') {
        const items = buildProgressingItems();
        progressingContainer.innerHTML = items.length === 0
            ? '<div class="upload-list-empty">' + t('upload.no_active') + '</div>'
            : items.map(item => item.html).join('');
    } else {
        const items = buildCompletedItems();
        completedContainer.innerHTML = items.length === 0
            ? '<div class="upload-list-empty">' + t('upload.no_completed') + '</div>'
            : items.map(item => item.html).join('');
    }
}

function renderUploadList() {
    if (renderUploadListRAF) return;
    if (!isUploadModalOpen) return;
    renderUploadListDirty = true;
    renderUploadListRAF = requestAnimationFrame(() => {
        renderUploadListRAF = null;
        if (renderUploadListDirty) {
            renderUploadListDirty = false;
            renderUploadListImpl();
        }
    });
}

function renderUploadListSync() {
    renderUploadListImpl();
}

function escapeHtml(str) {
    return String(str).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'})[c]);
}

function makeQueueItem(q, idx) {
    const name = escapeHtml(q.fileName);
    let icon = 'bi bi-file';
    if (q.fileName.match(/\.(png|jpg|jpeg|gif|bmp|webp|svg)$/i)) icon = 'bi bi-file-image';
    else if (q.fileName.match(/\.(zip|rar|7z|tar|gz)$/i)) icon = 'bi bi-file-zip';

    let statusText = '', statusClass = '', progressHtml = '', actionsHtml = '';
    if (q.status === 'PENDING') {
        statusText = t('upload.pending');
        statusClass = '';
    } else if (q.status === 'PROCESSING') {
        statusText = q.progress + '%';
        statusClass = 'uploading';
        progressHtml = '<div class="item-progress"><div class="progress-bar"><div class="progress-fill" id="ulProgress_' + idx + '" style="width:' + (q.progress || 0) + '%"></div></div></div>';
    } else if (q.status === 'COMPLETED' || q.status === 'success') {
        statusText = t('upload.completed');
        statusClass = 'done';
        actionsHtml = '<div class="item-actions"><button class="btn-task-delete" onclick="deleteWithAnimation(event, \'queue\', ' + idx + ', this)" title="' + t('upload.delete_record') + '"><i class="bi bi-trash3"></i></button></div>';
    } else if (q.status === 'FAILED') {
        statusText = q.errorMsg || t('upload.failed');
        statusClass = 'error';
        actionsHtml = '<div class="item-actions"><button class="btn-task-delete" onclick="deleteWithAnimation(event, \'queue\', ' + idx + ', this)" title="' + t('upload.delete_record') + '"><i class="bi bi-trash3"></i></button></div>';
    } else if (q.status === 'CANCELLED') {
        statusText = t('upload.cancelled');
        statusClass = 'cancelled';
        actionsHtml = '<div class="item-actions"><button class="btn-task-delete" onclick="deleteWithAnimation(event, \'queue\', ' + idx + ', this)" title="' + t('upload.delete_record') + '"><i class="bi bi-trash3"></i></button></div>';
    }

    if ((q.status === 'PENDING' || q.status === 'PROCESSING') && !actionsHtml) {
        actionsHtml = '<div class="item-actions"><button class="btn-action" onclick="cancelUploadFile(' + idx + ')">' + t('upload.cancel') + '</button></div>';
    }

    return {
        time: Date.now(),
        html: '<div class="upload-list-item" id="ulItem_' + idx + '"><div class="item-icon"><i class="' + icon + '"></i></div><div class="item-body"><div class="item-name" title="' + name + '">' + name + '</div><div class="item-info-row"><span>' + formatSize(q.fileSize) + '</span><span class="item-status ' + statusClass + '" id="ulStatus_' + idx + '">' + statusText + '</span></div>' + progressHtml + '</div>' + actionsHtml + '</div>'
    };
}

function makeTaskItem(task) {
    const name = escapeHtml(task.fileName || '');
    let statusText = '', statusClass = '', actionsHtml = '';
    if (task.status === 'COMPLETED' || task.status === 'success') {
        statusText = t('upload.completed');
        statusClass = 'done';
        actionsHtml = '<div class="item-actions"><button class="btn-task-delete" onclick="deleteWithAnimation(event, \'server\', \'' + task.taskId + '\', this)" title="' + t('upload.delete_record') + '"><i class="bi bi-trash3"></i></button></div>';
    } else if (task.status === 'FAILED') {
        statusText = t('upload.failed') + ': ' + (task.errorMessage || t('upload.unknown_error'));
        statusClass = 'error';
        actionsHtml = '<div class="item-actions"><button class="btn-task-delete" onclick="deleteWithAnimation(event, \'server\', \'' + task.taskId + '\', this)" title="' + t('upload.delete_record') + '"><i class="bi bi-trash3"></i></button></div>';
    } else if (task.status === 'CANCELLED') {
        statusText = t('upload.cancelled');
        statusClass = 'cancelled';
        actionsHtml = '<div class="item-actions"><button class="btn-task-delete" onclick="deleteWithAnimation(event, \'server\', \'' + task.taskId + '\', this)" title="' + t('upload.delete_record') + '"><i class="bi bi-trash3"></i></button></div>';
    } else if (task.status === 'PENDING' || task.status === 'PROCESSING') {
        statusText = '<span class="loading"></span> ' + t('upload.uploading') + ' ' + (task.progress || 0) + '%';
        statusClass = 'uploading';
        actionsHtml = '<div class="item-actions"><button class="btn-action" onclick="cancelServerTask(\'' + task.taskId + '\')">' + t('upload.cancel') + '</button></div>';
    } else {
        statusText = task.status || t('upload.unknown');
        statusClass = 'error';
        actionsHtml = '<div class="item-actions"><button class="btn-task-delete" onclick="deleteWithAnimation(event, \'server\', \'' + task.taskId + '\', this)" title="' + t('upload.delete_record') + '"><i class="bi bi-trash3"></i></button></div>';
    }

    let icon = 'bi bi-file';
    if (task.fileName && task.fileName.match(/\.(png|jpg|jpeg|gif|bmp|webp|svg)$/i)) icon = 'bi bi-file-image';
    else if (task.fileName && task.fileName.match(/\.(zip|rar|7z|tar|gz)$/i)) icon = 'bi bi-file-zip';

    return {
        time: task.completedTime || task.createdTime || 0,
        html: '<div class="upload-list-item"><div class="item-icon"><i class="' + icon + '"></i></div><div class="item-body"><div class="item-name" title="' + name + '">' + name + '</div><div class="item-info-row"><span>' + (task.totalSize ? formatSize(task.totalSize) : '') + '</span><span class="item-status ' + statusClass + '">' + statusText + '</span></div></div>' + actionsHtml + '</div>'
    };
}

function handleFileSelect(event) {
    const files = Array.from(event.target.files);
    if (files.length > 0) { addFilesToQueue(files); }
    event.target.value = '';
}

async function addFilesToQueue(files) {
    const targetPath = document.getElementById('uploadTargetPath').value;

    if (!conflictChoice) {
        const result = await showDialog({
            title: t('upload.conflict_title'),
            html: t('upload.conflict_desc'),
            type: 'warn',
            buttons: [
                { label: t('upload.skip'), value: 'skip' },
                { label: t('upload.overwrite'), value: 'overwrite', primary: true }
            ]
        });
        conflictChoice = result;
    }

    const fileNames = files.map(f => f.name);
    let existsMap = {};
    if (conflictChoice === 'skip') {
        try {
            const params = new URLSearchParams();
            params.append('targetPath', targetPath);
            fileNames.forEach(name => params.append('fileNames', name));
            const result = await api('/file/upload/check-exists', { method: 'POST', body: params });
            if (result.success) { existsMap = result.data || {}; }
        } catch (err) { console.error('Check exists error:', err); }
    }

    for (const file of files) {
        if (conflictChoice === 'skip' && existsMap[file.name]) continue;
        const existingItem = uploadFileQueue.find(item => item.fileName === file.name && item.fileSize === file.size && (item.status === 'PENDING' || item.status === 'PROCESSING'));
        if (existingItem) continue;
        uploadFileQueue.push({
            file: file,
            fileName: file.name,
            fileSize: file.size,
            taskId: null,
            uploadedSize: 0,
            totalChunks: 0,
            chunkSize: 5 * 1024 * 1024,
            progress: 0,
            status: 'PENDING',
            controller: null,
            errorMsg: ''
        });
    }
    renderUploadList();
    uploadNextFromQueue();
}

function updateUploadListItem(idx) {
    const item = uploadFileQueue[idx];
    if (!item) return;
    const now = Date.now();
    const lastTime = lastProgressUpdateTimeMap.get(item) || 0;
    if (item.status === 'PROCESSING' && item.progress > 0 && item.progress < 100 && now - lastTime < 1000) {
        return;
    }
    lastProgressUpdateTimeMap.set(item, now);
    const statusEl = document.getElementById('ulStatus_' + idx);
    const progressEl = document.getElementById('ulProgress_' + idx);
    if (statusEl) {
        if (item.status === 'PROCESSING') {
            statusEl.textContent = item.progress + '%';
            statusEl.className = 'item-status uploading';
        } else if (item.status === 'COMPLETED') {
            statusEl.textContent = t('upload.completed');
            statusEl.className = 'item-status done';
        } else if (item.status === 'FAILED') {
            statusEl.textContent = item.errorMsg || t('upload.failed');
            statusEl.className = 'item-status error';
        } else if (item.status === 'CANCELLED') {
            statusEl.textContent = t('upload.cancelled');
            statusEl.className = 'item-status cancelled';
        }
    }
    if (progressEl) {
        progressEl.style.width = item.progress + '%';
    }
}

function uploadNextFromQueue() {
    while (activeUploads < MAX_CONCURRENT_UPLOADS) {
        const pendingIndex = uploadFileQueue.findIndex(item => !item._deleted && item.status === 'PENDING');
        if (pendingIndex === -1) break;
        activeUploads++;
        const item = uploadFileQueue[pendingIndex];
        item.status = 'PROCESSING';
        updateUploadListItem(pendingIndex);
        uploadSingleFile(item, pendingIndex).finally(() => {
            activeUploads--;
            uploadNextFromQueue();
        });
    }
    if (activeUploads === 0) {
        if (uploadFileQueue.some(item => !item._deleted && item.status === 'COMPLETED')) {
            if (!isRootView) { navigateTo(currentPath); }
            setTimeout(() => loadUploadTasks(true), 500);
        }
        renderUploadList();
    }
}

async function uploadSingleFile(item, index) {
    const targetPath = document.getElementById('uploadTargetPath').value;
    item.chunkSize = 5 * 1024 * 1024;
    item.totalChunks = Math.ceil(item.file.size / item.chunkSize);
    item.uploadedSize = 0;
    item.progress = 0;

    try {
        item.controller = new AbortController();
        const signal = item.controller.signal;

        const initResult = await api('/file/upload/init', {
            method: 'POST',
            body: 'fileName=' + encodeURIComponent(item.fileName) + '&targetPath=' + encodeURIComponent(targetPath) + '&totalSize=' + item.fileSize,
            signal: signal
        });
        if (!initResult.success) {
            item.status = 'FAILED';
            item.errorMsg = initResult.msg || t('upload.init_failed');
            updateUploadListItem(index);
            renderUploadList();
            return;
        }

        item.taskId = initResult.data.taskId;
        await uploadNextChunk(item, index, signal);
    } catch (err) {
        if (err.name === 'AbortError') {
            item.status = 'CANCELLED';
        } else if (item.status !== 'CANCELLED') {
            item.status = 'FAILED';
            item.errorMsg = t('upload.upload_failed');
        }
        updateUploadListItem(index);
        renderUploadList();
    }
}

async function uploadNextChunk(item, index, signal) {
    if (signal.aborted) {
        item.status = 'CANCELLED';
        await cancelUploadTask(item.taskId);
        updateUploadListItem(index);
        renderUploadList();
        return;
    }

    const chunkIndex = Math.floor(item.uploadedSize / item.chunkSize);
    if (chunkIndex >= item.totalChunks) {
        await completeUploadTask(item, index);
        return;
    }

    const start = chunkIndex * item.chunkSize;
    const end = Math.min(start + item.chunkSize, item.fileSize);
    let chunkBlob;
    try {
        chunkBlob = new Blob([await item.file.slice(start, end).arrayBuffer()]);
    } catch (err) {
        item.status = 'FAILED';
        item.errorMsg = t('upload.file_changed');
        updateUploadListItem(index);
        renderUploadList();
        return;
    }

    try {
        const formData = new FormData();
        formData.append('taskId', item.taskId);
        formData.append('chunkIndex', chunkIndex);
        formData.append('chunkSize', end - start);
        formData.append('offset', start);
        formData.append('totalChunks', item.totalChunks);
        formData.append('file', chunkBlob);

        const response = await fetch(API_BASE + '/file/upload/chunk?' + getSessionParam(), {
            method: 'POST',
            body: formData,
            signal: signal
        });

        if (!response.ok) {
            item.status = 'FAILED';
            item.errorMsg = 'HTTP ' + response.status;
            updateUploadListItem(index);
            renderUploadList();
            return;
        }

        const result = await response.json();

        if (result.success) {
            const serverStatus = result.data && result.data.status;
            if (serverStatus === 'CANCELLED' || serverStatus === 'FAILED') {
                item.status = serverStatus;
                if (serverStatus === 'FAILED') item.errorMsg = t('upload.upload_interrupted');
                updateUploadListItem(index);
                renderUploadList();
                return;
            }
            if (serverStatus === 'COMPLETED') {
                await completeUploadTask(item, index);
                return;
            }
            const actualChunkSize = end - start;
            item.uploadedSize = Math.min(item.uploadedSize + actualChunkSize, item.fileSize);
            item.progress = Math.min(99, Math.round((item.uploadedSize / item.fileSize) * 100));
            updateUploadListItem(index);

            if (item.uploadedSize >= item.fileSize) {
                await completeUploadTask(item, index);
            } else if (!signal.aborted) {
                await uploadNextChunk(item, index, signal);
            }
        } else {
            item.status = 'FAILED';
            item.errorMsg = result.msg || t('upload.chunk_failed');
            updateUploadListItem(index);
            renderUploadList();
        }
    } catch (err) {
        if (err.name === 'AbortError') {
            item.status = 'CANCELLED';
            await cancelUploadTask(item.taskId);
        } else {
            item.status = 'FAILED';
            item.errorMsg = t('upload.upload_failed');
        }
        updateUploadListItem(index);
        renderUploadList();
    }
}

async function completeUploadTask(item, index) {
    try {
        const result = await api('/file/upload/complete', {
            method: 'POST',
            body: 'taskId=' + encodeURIComponent(item.taskId)
        });
        if (result.success) {
            item.status = 'COMPLETED';
            item.progress = 100;
        } else {
            item.status = 'FAILED';
            item.errorMsg = result.msg || t('upload.complete_failed');
        }
    } catch (err) {
        item.status = 'FAILED';
        item.errorMsg = t('upload.complete_failed');
    }
    updateUploadListItem(index);
    loadUploadTasks(true);
}

async function cancelUploadFile(index) {
    const item = uploadFileQueue[index];
    if (!item) return;
    if (item.controller) {
        item.controller.abort();
    }
    if (item.taskId) {
        await cancelUploadTask(item.taskId);
    }
    item.status = 'CANCELLED';
    updateUploadListItem(index);
    loadUploadTasks(true);
}

async function cancelUploadTask(taskId) {
    try {
        await api('/file/upload/cancel', {
            method: 'POST',
            body: 'taskId=' + encodeURIComponent(taskId)
        });
    } catch (err) { }
}

async function cancelServerTask(taskId) {
    await cancelUploadTask(taskId);
    loadUploadTasks(true);
}

async function deleteUploadFile(index) {
    const item = uploadFileQueue[index];
    if (!item) return;
    if (item.taskId) {
        try {
            await api('/file/upload/delete', {
                method: 'POST',
                body: 'taskIds=' + encodeURIComponent(item.taskId)
            });
        } catch (err) { }
        loadUploadTasks(true);
    }
    item._deleted = true;
    renderUploadList();
}

async function deleteTask(taskId) {
    try {
        await api('/file/upload/delete', {
            method: 'POST',
            body: 'taskIds=' + encodeURIComponent(taskId)
        });
    } catch (err) { }
    loadUploadTasks(true);
}

function deleteWithAnimation(event, type, id, element) {
    if (event) { event.stopPropagation(); event.preventDefault(); }
    if (type === 'queue') deleteUploadFile(id);
    else deleteTask(id);
}
