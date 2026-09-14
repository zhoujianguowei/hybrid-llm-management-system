// Requires common-api.js to be loaded first (provides fileBrowserFetch, showDialog)
var _fileBrowserModal = null;
var _fileBrowserTargetId = '';
var _fileBrowserMode = 'file';
var _fileBrowserSelectedPath = '';
var _fileBrowserCurrentDir = '';
var _fileBrowserStack = [];
var _fileBrowserFullList = [];
var _fileBrowserIsRoot = false;
var _useBootstrapModal = typeof bootstrap !== 'undefined' && typeof bootstrap.Modal === 'function';
var _fileBrowserMixedMode = false;
var _fileBrowserOnConfirm;
var _fileBrowserOnApply;

function showFileBrowserModal() {
    var el = document.getElementById('fileBrowserModal');
    if (_useBootstrapModal) {
        if (!_fileBrowserModal) {
            _fileBrowserModal = new bootstrap.Modal(el);
        }
        _fileBrowserModal.show();
    } else {
        el.classList.add('active');
    }
}

function hideFileBrowserModal(done) {
    if (_useBootstrapModal && _fileBrowserModal) {
        var el = document.getElementById('fileBrowserModal');
        var handler = function() {
            el.removeEventListener('hidden.bs.modal', handler);
            if (typeof done === 'function') done();
        };
        el.addEventListener('hidden.bs.modal', handler);
        _fileBrowserModal.hide();
    } else {
        document.getElementById('fileBrowserModal').classList.remove('active');
        if (typeof done === 'function') done();
    }
}

function openFileBrowser(targetId, mode, mixedMode) {
    _fileBrowserTargetId = targetId;
    _fileBrowserMode = mode || 'file';
    _fileBrowserMixedMode = mixedMode === true;
    _fileBrowserSelectedPath = '';
    _fileBrowserCurrentDir = '';
    _fileBrowserStack = [];
    _fileBrowserFullList = [];
    _fileBrowserIsRoot = false;
    var filterInput = document.getElementById('fileBrowserFilter');
    if (filterInput) filterInput.value = '';
    document.getElementById('fileBrowserConfirmBtn').disabled = true;
    document.getElementById('fileBrowserSelectedPath').textContent = t('browser.unselected');
    document.getElementById('fileBrowserTitle').innerHTML = _fileBrowserMixedMode
        ? '<i class="bi bi-folder2-open"></i> ' + t('browser.select_path')
        : (mode === 'directory'
            ? '<i class="bi bi-folder2-open"></i> ' + t('browser.select_dir')
            : '<i class="bi bi-file-earmark"></i> ' + t('browser.select_file'));
    showFileBrowserModal();
    var existingInput = document.getElementById(_fileBrowserTargetId);
    if (existingInput && existingInput.value && existingInput.value.trim()) {
        var existingPath = existingInput.value.trim().replace(/\\/g, '/');
        var fallback = function() { loadRootDirectories(); };
        if (mode === 'file') {
            var idx = existingPath.lastIndexOf('/');
            var dirPath = idx > 0 ? existingPath.substring(0, idx) : '';
            if (dirPath) {
                loadDirectory(dirPath, fallback);
            } else {
                fallback();
            }
        } else {
            loadDirectory(existingPath, fallback);
        }
    } else {
        loadRootDirectories();
    }
}

function loadRootDirectories() {
    setFileBrowserContent('<div class="file-browser-loading"><i class="bi bi-arrow-repeat spinner"></i> ' + t('browser.loading') + '</div>');
    fileBrowserFetch('/command/file/roots?rememberPath=false').then(function(result) {
        if (result && result.data && result.data.length > 0) {
            _fileBrowserCurrentDir = '';
            _fileBrowserIsRoot = true;
            renderFileBrowserBreadcrumb([]);
            document.getElementById('fileBrowserCurrentPath').value = '';
            var dirs = result.data.map(function(p) {
            if (typeof p === 'string') return { name: p, path: p, isDirectory: true };
            p = Object.assign({}, p); p.name = p.path || p.name || ''; return p;
        });
            _fileBrowserFullList = dirs;
            applyFileFilter();
            document.getElementById('fileBrowserConfirmBtn').disabled = true;
            document.getElementById('fileBrowserSelectedPath').textContent = _fileBrowserMixedMode ? t('browser.please_select_path') : t(_fileBrowserMode === 'directory' ? 'browser.please_select_dir' : 'browser.please_select_file');
        } else {
            setFileBrowserContent('<div class="file-browser-empty"><i class="bi bi-exclamation-circle"></i> ' + t('browser.no_root') + '</div>');
        }
    }).catch(function() {
        setFileBrowserContent('<div class="file-browser-empty"><i class="bi bi-exclamation-circle"></i> ' + t('browser.load_failed') + '</div>');
    });
}

function loadDirectory(path, onError) {
    setFileBrowserContent('<div class="file-browser-loading"><i class="bi bi-arrow-repeat spinner"></i> ' + t('browser.loading') + '</div>');
    fileBrowserFetch('/command/file/list?path=' + encodeURIComponent(path) + '&rememberPath=false').then(function(result) {
        if (result && result.data) {
            _fileBrowserCurrentDir = path;
            _fileBrowserIsRoot = false;
            document.getElementById('fileBrowserCurrentPath').value = path;
            var parts = path.replace(/\\/g, '/').split('/').filter(Boolean);
            renderFileBrowserBreadcrumb(parts);
            _fileBrowserFullList = result.data.files || result.data;
            var filterInput = document.getElementById('fileBrowserFilter');
            if (filterInput) filterInput.value = '';
            applyFileFilter();
            if (_fileBrowserMode === 'directory' || _fileBrowserMixedMode) {
                _fileBrowserSelectedPath = path;
                document.getElementById('fileBrowserConfirmBtn').disabled = false;
                document.getElementById('fileBrowserSelectedPath').textContent = path;
            } else {
                _fileBrowserSelectedPath = '';
                document.getElementById('fileBrowserConfirmBtn').disabled = true;
                document.getElementById('fileBrowserSelectedPath').textContent = t('browser.unselected');
            }
        } else {
            setFileBrowserContent('<div class="file-browser-empty"><i class="bi bi-exclamation-circle"></i> ' + t('browser.dir_empty') + '</div>');
        }
    }).catch(function() {
        if (typeof onError === 'function') {
            onError();
        } else {
            setFileBrowserContent('<div class="file-browser-empty"><i class="bi bi-exclamation-circle"></i> ' + t('browser.load_failed') + '</div>');
        }
    });
}

function filterFileList() {
    applyFileFilter();
}

function applyFileFilter() {
    var filterVal = (document.getElementById('fileBrowserFilter').value || '').toLowerCase();
    var filtered = _fileBrowserFullList;
    if (filterVal) {
        filtered = _fileBrowserFullList.filter(function(item) {
            return item.name && item.name.toLowerCase().indexOf(filterVal) >= 0;
        });
    }
    renderFileList(filtered, _fileBrowserIsRoot);
}

function renderFileBrowserBreadcrumb(parts) {
    const container = document.getElementById('fileBrowserBreadcrumb');
    let html = '<nav aria-label="breadcrumb"><ol class="breadcrumb mb-0">';
    html += '<li class="breadcrumb-item" onclick="loadRootDirectories()"><i class="bi bi-hdd"></i></li>';
    let accumulated = '';
    for (let i = 0; i < parts.length; i++) {
        accumulated += '/' + parts[i];
        const isLast = i === parts.length - 1;
        html += '<li class="breadcrumb-item' + (isLast ? ' active' : '') + '" onclick="' + (isLast ? '' : 'loadDirectory(\'' + escapeQuotes(accumulated) + '\')') + '">' + escapeHtml(parts[i]) + '</li>';
    }
    html += '</ol></nav>';
    container.innerHTML = html;
}

function renderFileList(items, isRoot) {
    const container = document.getElementById('fileBrowserContent');
    if (!items || items.length === 0) {
        container.innerHTML = '<div class="file-browser-empty"><i class="bi bi-folder-open"></i> ' + t('browser.empty_dir') + '</div>';
        return;
    }
    let html = '<ul class="file-browser-list">';
    if (!isRoot && _fileBrowserCurrentDir) {
        html += '<li onclick="goToParentDir()"><span class="file-icon"><i class="bi bi-arrow-return-left"></i></span><span class="file-name">..</span><span class="file-size"></span></li>';
    }
    items.sort(function(a, b) {
        if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
        return a.name.localeCompare(b.name);
    });
    for (let i = 0; i < items.length; i++) {
        var item = items[i];
        var icon = item.isDirectory ? '<i class="bi bi-folder"></i>' : getFileIcon(item.name);
        var sizeStr = item.isDirectory ? '' : formatFileSize(item.size);
        var clickAttr = item.isDirectory
            ? 'onclick="loadDirectory(\'' + escapeQuotes(item.path) + '\')"'
            : 'onclick="selectFileItem(\'' + escapeQuotes(item.path) + '\', \'' + escapeQuotes(item.name) + '\')"';
        var selectedClass = (_fileBrowserSelectedPath === item.path) ? ' selected' : '';
        html += '<li class="' + selectedClass + '" data-path="' + item.path.replace(/"/g, '&quot;') + '" data-isdir="' + item.isDirectory + '" ' + clickAttr + '>';
        html += '<span class="file-icon' + (item.isDirectory ? ' dir' : '') + '">' + icon + '</span>';
        html += '<span class="file-name">' + escapeHtml(item.name) + '</span>';
        html += '<span class="file-size"' + (item.isDirectory ? ' style="color:var(--text-secondary);font-size:0.75rem;"' : '') + '>' + (item.isDirectory ? t('browser.directory') : sizeStr) + '</span>';
        html += '</li>';
    }
    html += '</ul>';
    container.innerHTML = html;
}

function goToParentDir() {
    if (!_fileBrowserCurrentDir) {
        loadRootDirectories();
        return;
    }
    var path = _fileBrowserCurrentDir.replace(/\\/g, '/');
    var idx = path.lastIndexOf('/');
    if (idx <= 0) {
        loadRootDirectories();
    } else {
        var parent = path.substring(0, idx);
        if (parent.match(/^[A-Za-z]:$/)) {
            loadRootDirectories();
        } else {
            loadDirectory(parent);
        }
    }
}

function selectFileItem(path, name) {
    if (_fileBrowserMode === 'directory') return;
    _fileBrowserSelectedPath = path;
    document.querySelectorAll('#fileBrowserContent .file-browser-list li').forEach(function(li) {
        li.classList.remove('selected');
    });
    var selectedLi = document.querySelector('#fileBrowserContent .file-browser-list li[data-path="' + path.replace(/"/g, '&quot;') + '"]');
    if (selectedLi) selectedLi.classList.add('selected');
    document.getElementById('fileBrowserSelectedPath').textContent = path;
    document.getElementById('fileBrowserConfirmBtn').disabled = false;
}

function confirmFileBrowserSelection() {
    if (!_fileBrowserSelectedPath) {
        if (_fileBrowserMode === 'directory' && _fileBrowserCurrentDir) {
            _fileBrowserSelectedPath = _fileBrowserCurrentDir;
        } else {
            return;
        }
    }
    if (typeof _fileBrowserOnConfirm === 'function') {
        _fileBrowserOnConfirm(_fileBrowserSelectedPath);
    } else {
        applyFileBrowserSelection();
    }
}

function applyFileBrowserSelection() {
    document.getElementById(_fileBrowserTargetId).value = _fileBrowserSelectedPath;
    var statusEl = document.getElementById('status-' + _fileBrowserTargetId);
    if (statusEl) {
        statusEl.innerHTML = '&#10003;';
        statusEl.className = 'path-status valid';
    }
    var input = document.getElementById(_fileBrowserTargetId);
    if (input) {
        input.classList.remove('config-path-invalid');
        // 程序赋值不会触发 input 事件,这里手动派发,让监听者(如 draft 路径校验、启动按钮状态)同步更新
        input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    if (typeof _fileBrowserOnApply === 'function') _fileBrowserOnApply();
    hideFileBrowserModal();
}

function getFileIcon(filename) {
    var ext = filename.toLowerCase().split('.').pop();
    if (['jpg','jpeg','png','gif','bmp','webp','svg'].indexOf(ext) >= 0) return '<i class="bi bi-file-image"></i>';
    if (['mp4','avi','mkv','webm','mov'].indexOf(ext) >= 0) return '<i class="bi bi-file-play"></i>';
    if (['mp3','wav','flac','aac','ogg'].indexOf(ext) >= 0) return '<i class="bi bi-file-music"></i>';
    if (['txt','md','json','xml','yaml','yml','toml','ini','cfg','conf','log'].indexOf(ext) >= 0) return '<i class="bi bi-file-text"></i>';
    if (['zip','tar','gz','7z','rar'].indexOf(ext) >= 0) return '<i class="bi bi-file-zip"></i>';
    if (['pdf'].indexOf(ext) >= 0) return '<i class="bi bi-file-pdf"></i>';
    if (['html','css','js','ts','jsx','tsx','vue','java','py','c','cpp','h','hpp','go','rs','rb','php'].indexOf(ext) >= 0) return '<i class="bi bi-file-code"></i>';
    if (['exe','bin','sh','bat'].indexOf(ext) >= 0) return '<i class="bi bi-file-binary"></i>';
    return '<i class="bi bi-file-earmark"></i>';
}

function formatFileSize(bytes) {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
    return (bytes / 1073741824).toFixed(2) + ' GB';
}

function setFileBrowserContent(html) {
    document.getElementById('fileBrowserContent').innerHTML = html;
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function escapeQuotes(str) {
    if (!str) return '';
    return str.replace(/\\/g, '\\\\').replace(/'/g, '\\u0027').replace(/"/g, '&quot;');
}

function setFileBrowserOnConfirm(fn) {
    _fileBrowserOnConfirm = fn;
}
function setFileBrowserOnApply(fn) {
    _fileBrowserOnApply = fn;
}

function selectFile(targetId) {
    openFileBrowser(targetId, 'file');
}

function selectDirectory(targetId) {
    openFileBrowser(targetId, 'directory');
}

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('fileBrowserConfirmBtn').addEventListener('click', confirmFileBrowserSelection);
    if (_useBootstrapModal) {
        document.getElementById('fileBrowserModal').addEventListener('shown.bs.modal', function() {
            var pathInput = document.getElementById('fileBrowserCurrentPath');
            if (pathInput) pathInput.focus();
        });
    }
    document.getElementById('fileBrowserModal').addEventListener('click', function(e) {
        if (e.target && e.target.classList.contains('file-browser-list')) {
            if ((_fileBrowserMode === 'directory' || _fileBrowserMixedMode) && _fileBrowserCurrentDir) {
                _fileBrowserSelectedPath = _fileBrowserCurrentDir;
                document.getElementById('fileBrowserSelectedPath').textContent = _fileBrowserCurrentDir;
                document.getElementById('fileBrowserConfirmBtn').disabled = false;
            }
        }
        if (!_useBootstrapModal) {
            if (e.target && (e.target.classList.contains('file-browser-close') || e.target.closest('.file-browser-close'))) {
                hideFileBrowserModal();
            }
            if (e.target && (e.target.classList.contains('file-browser-cancel') || e.target.closest('.file-browser-cancel'))) {
                hideFileBrowserModal();
            }
        }
    });
});
