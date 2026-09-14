const TEXT_FILE_EXTENSIONS = {
    '通用': ['.txt', '.md', '.csv', '.log', '.tex', '.rst'],
    'Web': ['.html', '.htm', '.css', '.scss', '.less', '.xml', '.json', '.yaml', '.yml', '.vue', '.svg'],
    'Java系': ['.java', '.kt', '.kts', '.scala', '.groovy', '.gradle', '.properties'],
    'JS系': ['.js', '.ts', '.jsx', '.tsx'],
    'Python': ['.py'],
    'C/C++': ['.c', '.cpp', '.cc', '.cxx', '.h', '.hpp'],
    '脚本': ['.go', '.rs', '.rb', '.php', '.swift', '.sh', '.bash', '.bat', '.sql', '.r', '.pl', '.lua', '.hs', '.ex', '.exs', '.clj', '.fs', '.fsx'],
    '配置': ['.toml', '.ini', '.cfg', '.conf'],
    '其他': ['.dart', '.pot', '.po', '.v', '.sv', '.asm', '.s']
};
const FOLDER_IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.tiff'];

let pendingFolderFiles = [];
let selectedFolderExts = new Set();
let folderFileTree = {};

function getSelectedExtensions() {
    return Array.from(selectedFolderExts);
}

function getAllSupportedExts() {
    const all = [];
    for (let exts of Object.values(TEXT_FILE_EXTENSIONS)) all.push(...exts);
    if (modelSupportsImage()) {
        for (let ext of FOLDER_IMAGE_EXTENSIONS) all.push(ext);
    }
    return all;
}

function modelSupportsImage() {
    return selectedChatModel && (selectedChatModel.multi & 0x01);
}

function getMaxTextFileSize() {
    return sessionConfig ? sessionConfig.maxTextAttachmentSize * 1024 : 10 * 1024 * 1024;
}

function isImageExt(ext) {
    return FOLDER_IMAGE_EXTENSIONS.includes(ext);
}

function getFileMaxSize(ext) {
    if (isImageExt(ext)) {
        return sessionConfig ? sessionConfig.maxAttachmentSize * 1024 * 1024 : 10 * 1024 * 1024;
    }
    return getMaxTextFileSize();
}

function buildFolderTree(files) {
    const tree = {};
    const allExts = getAllSupportedExts();
    for (const file of files) {
        const ext = '.' + file.name.split('.').pop().toLowerCase();
        if (!allExts.includes(ext) || file.size > getFileMaxSize(ext)) continue;
        const parts = (file.webkitRelativePath || file.name).split('/');
        let current = tree;
        let currentPath = '';
        for (let i = 0; i < parts.length; i++) {
            if (i === parts.length - 1) {
                if (!current._files) current._files = [];
                current._files.push({
                    file: file,
                    name: parts[i],
                    path: file.webkitRelativePath || file.name,
                    ext: ext,
                    size: file.size,
                    selected: true,
                    isImage: isImageExt(ext)
                });
            } else {
                currentPath = currentPath ? currentPath + '/' + parts[i] : parts[i];
                if (!current[parts[i]]) {
                    current[parts[i]] = { _files: [], collapsed: false, _path: currentPath };
                }
                current = current[parts[i]];
            }
        }
    }
    return tree;
}

function getAllFilesInNode(node) {
    let result = [];
    result = result.concat(node._files || []);
    for (const key of Object.keys(node)) {
        if (key !== '_files' && key !== 'collapsed' && key !== '_path') {
            result = result.concat(getAllFilesInNode(node[key]));
        }
    }
    return result;
}

function countFilesInTree(node) {
    return getAllFilesInNode(node).length;
}

function setAllNodesCollapsed(node, collapsed) {
    for (const key of Object.keys(node)) {
        if (key !== '_files' && key !== 'collapsed' && key !== '_path' && typeof node[key] === 'object') {
            node[key].collapsed = collapsed;
            setAllNodesCollapsed(node[key], collapsed);
        }
    }
}

function getFileIconClass(ext) {
    if (FOLDER_IMAGE_EXTENSIONS.includes(ext)) return 'bi-file-earmark-image';
    if (['.java', '.kt', '.kts', '.scala', '.groovy'].includes(ext)) return 'bi-file-earmark-fill';
    if (['.js', '.ts', '.jsx', '.tsx'].includes(ext)) return 'bi-file-earmark-js';
    if (['.py'].includes(ext)) return 'bi-file-earmark-py';
    if (['.html', '.htm', '.vue'].includes(ext)) return 'bi-file-earmark-html';
    if (['.css', '.scss', '.less'].includes(ext)) return 'bi-file-earmark-css';
    if (['.json', '.xml', '.yaml', '.yml', '.toml', '.ini', '.cfg', '.conf'].includes(ext)) return 'bi-file-earmark-code';
    if (['.md', '.txt', '.rst', '.tex'].includes(ext)) return 'bi-file-earmark-text';
    if (['.sql'].includes(ext)) return 'bi-file-earmark-code';
    if (['.log'].includes(ext)) return 'bi-file-earmark-text';
    if (['.c', '.cpp', '.cc', '.cxx', '.h', '.hpp'].includes(ext)) return 'bi-file-earmark-code';
    return 'bi-file-earmark';
}

function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + 'B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB';
    return (bytes / (1024 * 1024)).toFixed(1) + 'MB';
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function updateFolderFileCount() {
    const allFiles = getAllFilesInNode(folderFileTree);
    const selected = allFiles.filter(f => f.selected).length;
    const countEl = document.getElementById('folderFileCount');
    if (countEl) countEl.textContent = t('chat.folder_selected') + ' ' + selected + ' / ' + t('chat.folder_total') + ' ' + allFiles.length + ' ' + t('chat.folder_files');
    const btnEl = document.getElementById('folderUploadBtn');
    if (btnEl) btnEl.innerHTML = t('chat.folder_confirm_upload') + ' (' + selected + ')';
}

function renderExtFilterDropdown() {
    const container = document.getElementById('folderExtDropdownList');
    if (!container) return;
    const GROUP_I18N = {
        '通用': 'chat.file_ext_general',
        'Web': 'chat.file_ext_web',
        'Java系': 'chat.file_ext_java',
        'JS系': 'chat.file_ext_js',
        'Python': 'chat.file_ext_python',
        'C/C++': 'chat.file_ext_cpp',
        '脚本': 'chat.file_ext_script',
        '配置': 'chat.file_ext_config',
        '其他': 'chat.file_ext_other'
    };
    let html = '';
    for (let [group, exts] of Object.entries(TEXT_FILE_EXTENSIONS)) {
        html += '<div class="folder-ext-group"><span class="folder-ext-group-name">' + t(GROUP_I18N[group] || group) + '</span><div class="folder-ext-items">';
        for (let ext of exts) {
            const checked = selectedFolderExts.has(ext);
            html += '<label class="folder-ext-item ' + (checked ? 'active' : '') + '" data-ext="' + ext + '">';
            html += '<span class="folder-ext-cb"><i class="bi ' + (checked ? 'bi-check-square-fill' : 'bi-square') + '"></i></span>' + ext + '</label>';
        }
        html += '</div></div>';
    }
    if (modelSupportsImage()) {
        html += '<div class="folder-ext-group"><span class="folder-ext-group-name">' + t('chat.file_ext_image') + '</span><div class="folder-ext-items">';
        for (let ext of FOLDER_IMAGE_EXTENSIONS) {
            const checked = selectedFolderExts.has(ext);
            html += '<label class="folder-ext-item ' + (checked ? 'active' : '') + '" data-ext="' + ext + '">';
            html += '<span class="folder-ext-cb"><i class="bi ' + (checked ? 'bi-check-square-fill' : 'bi-square') + '"></i></span>' + ext + '</label>';
        }
        html += '</div></div>';
    }
    container.innerHTML = html;
}

function renderExtFilterTrigger() {
    const trigger = document.getElementById('folderExtTrigger');
    if (!trigger) return;
    let total = Object.values(TEXT_FILE_EXTENSIONS).reduce(function(s, exts) { return s + exts.length; }, 0);
    if (modelSupportsImage()) {
        total += FOLDER_IMAGE_EXTENSIONS.length;
    }
    const selected = selectedFolderExts.size;
    if (selected === total) {
        trigger.innerHTML = '<i class="bi bi-funnel-fill"></i> ' + t('chat.file_type') + ': <b>' + t('chat.all') + '</b> <span class="folder-ext-badge">' + total + t('chat.unit_types') + '</span> <i class="bi bi-chevron-down"></i>';
    } else {
        trigger.innerHTML = '<i class="bi bi-funnel-fill"></i> ' + t('chat.file_type') + ': <b>' + t('chat.selected') + ' ' + selected + '</b> <span class="folder-ext-badge">' + selected + '/' + total + '</span> <i class="bi bi-chevron-down"></i>';
    }
}

function toggleExtDropdown() {
    const panel = document.getElementById('folderExtDropdown');
    panel.classList.toggle('open');
    const search = document.getElementById('folderExtSearch');
    if (panel.classList.contains('open') && search) {
        setTimeout(function() { search.focus(); }, 50);
    }
}

function filterExtBySearch(query) {
    const q = query.toLowerCase().trim();
    document.querySelectorAll('.folder-ext-group').forEach(function(group) {
        const items = group.querySelectorAll('.folder-ext-item');
        let hasVisible = false;
        items.forEach(function(item) {
            const ext = item.getAttribute('data-ext');
            item.style.display = (!q || ext.includes(q)) ? '' : 'none';
            if (item.style.display !== 'none') hasVisible = true;
        });
        group.style.display = hasVisible ? '' : 'none';
    });
}

function showFolderFilterModal() {
    selectedFolderExts = new Set();
    for (let exts of Object.values(TEXT_FILE_EXTENSIONS)) {
        for (let ext of exts) selectedFolderExts.add(ext);
    }
    if (modelSupportsImage()) {
        for (let ext of FOLDER_IMAGE_EXTENSIONS) selectedFolderExts.add(ext);
    }
    renderExtFilterDropdown();
    renderExtFilterTrigger();
    document.getElementById('folderExtDropdown').classList.remove('open');
    document.getElementById('folderExtSearch').value = '';
    filterExtBySearch('');
    renderFileTree();
    document.getElementById('folderFilterModal').classList.add('active');
}

function closeFolderFilterModal() {
    document.getElementById('folderFilterModal').classList.remove('active');
    pendingFolderFiles = [];
    folderFileTree = {};
}

function toggleFilterType(ext) {
    if (selectedFolderExts.has(ext)) {
        selectedFolderExts.delete(ext);
    } else {
        selectedFolderExts.add(ext);
    }
    renderExtFilterDropdown();
    renderExtFilterTrigger();
    filterFileTreeByExts();
    renderFileTree();
}

function toggleAllFilterExts(check) {
    if (check) {
        selectedFolderExts = new Set();
        for (let exts of Object.values(TEXT_FILE_EXTENSIONS)) {
            for (let ext of exts) selectedFolderExts.add(ext);
        }
        if (modelSupportsImage()) {
            for (let ext of FOLDER_IMAGE_EXTENSIONS) selectedFolderExts.add(ext);
        }
    } else {
        selectedFolderExts = new Set();
    }
    renderExtFilterDropdown();
    renderExtFilterTrigger();
    filterFileTreeByExts();
    renderFileTree();
}

function filterFileTreeByExts() {
    const allExts = getAllSupportedExts();
    const filtered = pendingFolderFiles.filter(f => {
        const ext = '.' + f.name.split('.').pop().toLowerCase();
        return allExts.includes(ext) && f.size <= getFileMaxSize(ext) && selectedFolderExts.has(ext);
    });
    folderFileTree = {};
    for (const file of filtered) {
        const ext = '.' + file.name.split('.').pop().toLowerCase();
        const parts = (file.webkitRelativePath || file.name).split('/');
        let current = folderFileTree;
        let currentPath = '';
        for (let i = 0; i < parts.length; i++) {
            if (i === parts.length - 1) {
                if (!current._files) current._files = [];
                current._files.push({
                    file: file,
                    name: parts[i],
                    path: file.webkitRelativePath || file.name,
                    ext: ext,
                    size: file.size,
                    selected: true,
                    isImage: isImageExt(ext)
                });
            } else {
                currentPath = currentPath ? currentPath + '/' + parts[i] : parts[i];
                if (!current[parts[i]]) {
                    current[parts[i]] = { _files: [], collapsed: false, _path: currentPath };
                }
                current = current[parts[i]];
            }
        }
    }
}

function renderFileTree() {
    const container = document.getElementById('folderFileTree');
    if (!container) return;
    const folders = Object.keys(folderFileTree).filter(function(k) { return k !== '_files' && k !== 'collapsed' && k !== '_path'; });
    const files = folderFileTree._files || [];
    if (!folders.length && !files.length) {
        container.innerHTML = '<div class="folder-tree-empty"><i class="bi bi-inbox"></i><p>' + t('chat.no_matching_files') + '</p></div>';
        updateFolderFileCount();
        return;
    }
    container.innerHTML = renderTreeNode(folderFileTree, 0);
    updateFolderFileCount();
}

function renderTreeNode(node, depth) {
    let html = '';
    const folders = Object.keys(node).filter(function(k) { return k !== '_files' && k !== 'collapsed' && k !== '_path'; });
    const files = node._files || [];
    for (const folder of folders.sort()) {
        const child = node[folder];
        const childCount = countFilesInTree(child);
        const collapseClass = child.collapsed ? ' collapsed' : '';
        const allFiles = getAllFilesInNode(child);
        const checkedCount = allFiles.filter(function(f) { return f.selected; }).length;
        const checkAll = allFiles.length > 0 && checkedCount === allFiles.length;
        const checkSome = checkedCount > 0 && !checkAll;
        const nodeId = child._path || folder;
        const indent = 12 + depth * 16;
        html += '<div class="folder-tree-node">';
        html += '<div class="folder-tree-folder' + collapseClass + '" style="padding-left:' + indent + 'px" data-node-id="' + nodeId.replace(/"/g, '&quot;') + '">';
        html += '<span class="folder-tree-expand"><i class="bi bi-chevron-right"></i></span>';
        html += '<span class="folder-tree-cb"><input type="checkbox" ' + (checkAll ? 'checked' : '') + (checkSome ? ' data-indeterminate="1"' : '') + '></span>';
        html += '<i class="bi bi-folder' + (checkAll ? '-fill' : '') + '"></i> ';
        html += '<span class="folder-tree-name">' + escapeHtml(folder) + '</span>';
        html += '<span class="folder-tree-count">(' + childCount + (checkAll ? '' : (checkedCount > 0 ? ' | ' + checkedCount : '')) + ')</span>';
        html += '</div>';
        html += '<div class="folder-tree-children' + collapseClass + '">' + renderTreeNode(child, depth + 1) + '</div>';
        html += '</div>';
    }
    for (const f of files) {
        const indent = 12 + (depth + 1) * 16;
        html += '<div class="folder-tree-file" style="padding-left:' + indent + 'px" data-file-path="' + f.path.replace(/"/g, '&quot;') + '">';
        html += '<span class="folder-tree-cb"><input type="checkbox" ' + (f.selected ? 'checked' : '') + '></span>';
        html += '<i class="bi ' + getFileIconClass(f.ext) + '"></i> ';
        html += '<span class="folder-tree-name">' + escapeHtml(f.name) + '</span>';
        html += '<span class="folder-tree-size">' + formatFileSize(f.size) + '</span>';
        html += '</div>';
    }
    return html;
}

// Event delegation for tree interactions
document.addEventListener('DOMContentLoaded', function() {
    const container = document.getElementById('folderFileTree');
    if (container) {
        container.addEventListener('click', function(e) {
            // Folder expand/collapse
            const expandBtn = e.target.closest('.folder-tree-expand');
            if (expandBtn) {
                e.preventDefault();
                e.stopPropagation();
                const folderRow = expandBtn.closest('.folder-tree-folder');
                const nodeId = folderRow.getAttribute('data-node-id');
                const node = findNodeByPath(folderFileTree, nodeId);
                if (node) {
                    node.collapsed = !node.collapsed;
                    renderFileTree();
                }
                return;
            }
            // Folder checkbox toggle
            const folderCb = e.target.closest('.folder-tree-cb');
            if (folderCb && folderCb.closest('.folder-tree-folder')) {
                e.preventDefault();
                e.stopPropagation();
                const folderRow = folderCb.closest('.folder-tree-folder');
                const nodeId = folderRow.getAttribute('data-node-id');
                const node = findNodeByPath(folderFileTree, nodeId);
                if (node) {
                    const files = getAllFilesInNode(node);
                    const allSelected = files.length > 0 && files.every(function(f) { return f.selected; });
                    files.forEach(function(f) { f.selected = !allSelected; });
                    renderFileTree();
                }
                return;
            }
            // File checkbox toggle
            const fileCb = e.target.closest('.folder-tree-cb');
            if (fileCb && fileCb.closest('.folder-tree-file')) {
                e.preventDefault();
                e.stopPropagation();
                const fileRow = fileCb.closest('.folder-tree-file');
                const filePath = fileRow.getAttribute('data-file-path');
                const allFiles = getAllFilesInNode(folderFileTree);
                const fileObj = allFiles.find(function(f) { return f.path === filePath; });
                if (fileObj) {
                    fileObj.selected = !fileObj.selected;
                    renderFileTree();
                }
                return;
            }
        });
    }
    // Extension filter click delegation
    const extDropdown = document.getElementById('folderExtDropdownList');
    if (extDropdown) {
        extDropdown.addEventListener('click', function(e) {
            const item = e.target.closest('.folder-ext-item');
            if (item) {
                e.preventDefault();
                e.stopPropagation();
                const ext = item.getAttribute('data-ext');
                toggleFilterType(ext);
            }
        });
    }
});

function findNodeByPath(tree, folderPath) {
    for (const key of Object.keys(tree)) {
        if (key !== '_files' && key !== 'collapsed' && key !== '_path') {
            if (tree[key]._path === folderPath) return tree[key];
            const found = findNodeByPath(tree[key], folderPath);
            if (found) return found;
        }
    }
    return null;
}

function toggleAllFolderFiles(check) {
    const allFiles = getAllFilesInNode(folderFileTree);
    allFiles.forEach(function(f) { f.selected = check; });
    renderFileTree();
}

function expandAllFolderNodes() {
    setAllNodesCollapsed(folderFileTree, false);
    renderFileTree();
}

function collapseAllFolderNodes() {
    setAllNodesCollapsed(folderFileTree, true);
    renderFileTree();
}

function handleFolderSelect(event) {
    const files = Array.from(event.target.files);
    if (!files.length) return;
    event.target.value = '';
    pendingFolderFiles = files;
    folderFileTree = buildFolderTree(files);
    showFolderFilterModal();
}

function confirmFolderUpload() {
    const allFiles = getAllFilesInNode(folderFileTree);
    const selected = allFiles.filter(function(f) { return f.selected; });
    if (!selected.length) {
        showAlert(t('chat.select_at_least_one_file'), 'warning');
        return;
    }
    const imageFiles = selected.filter(function(f) { return f.isImage; });
    const textFiles = selected.filter(function(f) { return !f.isImage; });

    if (imageFiles.length > 0) {
        const maxMediaTotal = sessionConfig ? sessionConfig.maxAttachments : 10;
        if (currentMediaList.length + imageFiles.length > maxMediaTotal) {
            showAlert(t('chat.media_limit_detail', {added: currentMediaList.length, selected: imageFiles.length, limit: maxMediaTotal}), 'warning');
            return;
        }
    }
    if (textFiles.length > 0) {
        const maxTextTotal = sessionConfig ? sessionConfig.maxTextAttachments : 10;
        if (currentTextFileList.length + textFiles.length > maxTextTotal) {
            showAlert(t('chat.text_limit_detail', {added: currentTextFileList.length, selected: textFiles.length, limit: maxTextTotal}), 'warning');
            return;
        }
    }

    closeFolderFilterModal();
    let uploaded = 0;
    const total = selected.length;
    for (const fileObj of selected) {
        const file = fileObj.file;
        const webkitRelativePath = file.webkitRelativePath || file.name;
        const pathParts = webkitRelativePath.split('/');
        const relativePath = pathParts.length > 2 ? pathParts.slice(1, -1).join('/') : '';
        const formData = new FormData();
        formData.append('file', file);
        if (fileObj.isImage) {
            formData.append('maxResolution', '1024');
            formData.append('quality', '0.85');
        }
        fetch(API_BASE + '/chat/media/upload?' + getSessionParam(), { method: 'POST', body: formData })
            .then(function(r) { return r.json(); })
            .then(function(result) {
                uploaded++;
                if (result.success) {
                    if (fileObj.isImage) {
                        currentMediaList.push({
                            type: 'image',
                            path: result.data,
                            previewUrl: URL.createObjectURL(file),
                            name: file.name
                        });
                    } else {
                        currentTextFileList.push({ path: result.data, name: file.name, relativePath: relativePath });
                    }
                    renderMediaPreview();
                } else {
                    showAlert(result.msg || t('chat.file_upload_failed'), 'error');
                }
                if (uploaded === total) {
                    showAlert(t('chat.upload_complete'), 'success');
                }
            })
            .catch(function() { showAlert(t('chat.file_upload_failed'), 'error'); });
    }
}