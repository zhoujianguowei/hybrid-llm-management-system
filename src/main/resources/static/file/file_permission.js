// === File Permission Management ===
// Requires upload.js to be loaded first (provides escapeHtml)

var _permModal = null;
var _permStore = null;
var _permTreeNodes = [];
var _permAllUsers = [];
var _permEditingRule = null;
var _permCascadeRunning = false;

const _ROLE_ORDER = {admin: 0, user: 1, guest: 2};
const _PERMS = ['read', 'execute', 'download', 'upload', 'delete'];
const _PERM_LEVEL = {read: 1, execute: 2, download: 3, upload: 4, delete: 5};
const _PERM_LABELS = {read: t('perm.read'), execute: t('perm.execute'), download: t('perm.download'), upload: t('perm.upload'), delete: t('perm.delete')};

// === Tree Building ===

function buildPermTree(tree) {
    if (!tree || !tree.childrenMap) return [];
    var nodes = [];
    Object.keys(tree.childrenMap).forEach(function(key) {
        collectPermTreeNode(tree.childrenMap[key], nodes);
    });
    return nodes;
}

function collectPermTreeNode(node, nodes) {
    if (!node) return;
    var rule = node.filePermissionRule;
    if (rule && rule.path) {
        var name = node.name || rule.path.split('/').filter(Boolean).pop() || rule.path;
        nodes.push({
            path: rule.path,
            name: name,
            rule: rule,
            _isRoot: !!node.isRoot,
            _isLeaf: !!node.isLeaf,
            _children: node.childrenMap ? buildPermTree(node) : [],
            _expanded: node.childrenMap && Object.keys(node.childrenMap).length > 0
        });
    }
}

function findRuleInTree(treeNodes, path, parent) {
    for (var i = 0; i < treeNodes.length; i++) {
        if (treeNodes[i].path === path) return {node: treeNodes[i], parent: parent || null, index: i};
        var found = findRuleInTree(treeNodes[i]._children, path, treeNodes[i]);
        if (found) return found;
    }
    return null;
}

// === Role & User Helpers ===

function getCascadedRoles(role) {
    var order = _ROLE_ORDER[role];
    if (order === undefined) return [role];
    return Object.keys(_ROLE_ORDER).filter(function(r) { return _ROLE_ORDER[r] <= order; });
}

function removeCascadedUsers(additionalUsers, role) {
    if (!additionalUsers || additionalUsers.length === 0) return [];
    var cascaded = getCascadedRoles(role);
    return additionalUsers.filter(function(u) { return cascaded.indexOf(u) < 0; });
}

// === API Calls ===

async function loadAllUsers() {
    try {
        var result = await api('/file/user/list');
        if (result.success) {
            _permAllUsers = result.data;
        }
    } catch (e) { console.error('Load users error:', e); }
}

async function reloadPermissionTree() {
    var result = await api('/file/permission/list');
    if (result.success) {
        _permStore = result.data;
        _permTreeNodes = buildPermTree(_permStore);
        renderPermissionTree();
        return true;
    }
    return false;
}

// === Main Modal ===

async function openPermissionModal() {
    await loadAllUsers();
    try {
        var result = await api('/file/permission/list');
        if (result.success) {
            _permStore = result.data;
            _permTreeNodes = buildPermTree(_permStore);
            renderPermissionTree();
            document.getElementById('permissionModal').classList.add('active');
        } else {
            showToast(t('perm.load_failed') + ': ' + result.msg, 'error');
        }
    } catch (e) {
        showToast(t('perm.load_failed'), 'error');
    }
}

function closePermissionModal() {
    document.getElementById('permissionModal').classList.remove('active');
    _permStore = null;
    _permTreeNodes = [];
    _permEditingRule = null;
}

function switchPermTab(tab) {
    document.querySelectorAll('.perm-tab').forEach(function(t) {
        t.classList.toggle('active', t.dataset.permtab === tab);
    });
    document.getElementById('permPathTab').style.display = tab === 'path' ? 'block' : 'none';
    var footer = document.querySelector('.perm-footer');
    footer.innerHTML = '<button class="btn btn-cancel perm-close-modal">' + t('perm.close') + '</button>';
    footer.querySelector('.perm-close-modal').addEventListener('click', closePermissionModal);
}

// === Tree Rendering ===

function renderPermissionTree() {
    var container = document.getElementById('permPathList');
    if (!_permTreeNodes || _permTreeNodes.length === 0) {
        container.innerHTML = '<div style="text-align:center;color:var(--text-muted);padding:20px;font-size:13px;">' + t('perm.no_rules') + '</div>';
        return;
    }
    var html = '<div class="perm-tree">';
    _permTreeNodes.forEach(function(node) {
        html += renderPermTreeNode(node, 0);
    });
    html += '</div>';
    container.innerHTML = html;
    container.querySelectorAll('.perm-tree-toggle[data-path]').forEach(function(el) {
        el.addEventListener('click', function() { togglePermTreeNode(this, this.getAttribute('data-path')); });
    });
    container.querySelectorAll('.perm-action-edit').forEach(function(el) {
        el.addEventListener('click', function() { editPathRuleByPath(this.getAttribute('data-path')); });
    });
    container.querySelectorAll('.perm-action-delete').forEach(function(el) {
        el.addEventListener('click', function() { deletePathRuleByPath(this.getAttribute('data-path')); });
    });
}

function renderPermTreeNode(node, depth) {
    var hasChildren = node._children && node._children.length > 0;
    var indent = depth * 20;
    var toggleIcon = hasChildren
        ? '<span class="perm-tree-toggle" data-path="' + escapeHtml(node.path) + '"><i class="bi bi-chevron-' + (node._expanded ? 'down' : 'right') + '"></i></span>'
        : '<span class="perm-tree-toggle perm-tree-toggle-empty"></span>';
    var pathDisplay = node._isRoot ? '<i class="bi bi-hdd"></i> ' + escapeHtml(node.name) : escapeHtml(node.name);
    var leafBadge = node._isLeaf ? '<span class="perm-icon partial" title="' + t('perm.manual') + '"><i class="bi bi-pencil-fill"></i></span>' : '';
    var protectedBadge = node.rule && node.rule.isProtected ? '<span class="perm-icon locked" title="' + t('perm.protected') + '"><i class="bi bi-shield-lock-fill"></i></span>' : '';
    var actions = '<button class="action-btn perm-action-edit" data-path="' + escapeHtml(node.path) + '">' + t('perm.edit') + '</button>';
    if (!node._isRoot) {
        actions += '<button class="action-btn action-btn-delete perm-action-delete" data-path="' + escapeHtml(node.path) + '">' + t('perm.delete') + '</button>';
    }
    var html = '<div class="perm-tree-node" data-path="' + escapeHtml(node.path) + '">';
    html += '<div class="perm-tree-row" style="padding-left:' + (12 + indent) + 'px">';
    html += toggleIcon;
    html += '<span class="perm-tree-path" title="' + escapeHtml(node.path) + '">' + pathDisplay + '</span>' + leafBadge + protectedBadge;
    html += '<div class="path-actions">' + actions + '</div>';
    html += '</div>';
    if (hasChildren && node._expanded) {
        html += '<div class="perm-tree-children">';
        node._children.forEach(function(child) {
            html += renderPermTreeNode(child, depth + 1);
        });
        html += '</div>';
    }
    html += '</div>';
    return html;
}

function togglePermTreeNode(el, path) {
    var found = findRuleInTree(_permTreeNodes, path);
    if (!found) return;
    found.node._expanded = !found.node._expanded;
    renderPermissionTree();
}

// === Edit by path (tree-based) ===

function editPathRuleByPath(path) {
    var found = findRuleInTree(_permTreeNodes, path);
    if (!found) return;
    _permEditingRule = found.node.rule;
    document.getElementById('permEditPath').value = _permEditingRule.path;
    resetAllRoleDropdowns();
    _permCascadeRunning = true;
    _PERMS.forEach(function(p) {
        var rule = _permEditingRule[p];
        var roleSel = document.getElementById('permEdit' + capitalize(p) + 'Role');
        if (roleSel) roleSel.value = (rule && rule.minRole) || 'admin';
        renderPermUsers('permEdit' + capitalize(p) + 'Users', (rule && rule.additionalUsers) || [], p);
    });
    _permCascadeRunning = false;
    updateAllRoleDropdowns();
    document.getElementById('permEditIsProtected').checked = !!_permEditingRule.isProtected;
    if (_permEditingRule.isDirectory === false) {
        var executeRow = document.getElementById('permEditExecuteRole');
        if (executeRow) executeRow.closest('.perm-row').style.display = 'none';
        var uploadRow = document.getElementById('permEditUploadRole');
        if (uploadRow) uploadRow.closest('.perm-row').style.display = 'none';
    } else {
        document.querySelectorAll('#permPathEditModal .perm-row').forEach(function(row) {
            row.style.display = '';
        });
    }
    document.getElementById('permissionModal').style.display = 'none';
    document.getElementById('permPathEditModal').classList.add('active');
}

async function deletePathRuleByPath(path) {
    const action = await showDialog({ type: 'warn', title: t('perm.delete_path_title'), message: t('perm.delete_path_confirm', {path: path}), buttons: [{ label: t('dialog.confirm'), value: 'yes', primary: true }, { label: t('dialog.cancel'), value: 'cancel' }] }).catch(() => null);
    if (action !== 'yes') return;
    await doAsyncDeletePathRule(path);
}

async function doAsyncDeletePathRule(path) {
    try {
        var result = await api('/file/permission/path/delete?path=' + encodeURIComponent(path), {method: 'POST'});
        if (result.success) {
            showToast(t('perm.rule_deleted'), 'success');
            reloadPermissionTree();
        } else {
            showToast(t('perm.delete_failed') + ': ' + result.msg, 'error');
        }
    } catch (e) {
        showToast(t('perm.delete_failed'), 'error');
    }
}

// Backward compatibility: index.html may still call editPathRule(index) / deletePathRule(index)
function editPathRule(index) { editPathRuleByPath(_permTreeNodes[index].path); }
async function deletePathRule(index) { await deletePathRuleByPath(_permTreeNodes[index].path); }

// === User Tag Rendering ===

function renderPermUsers(containerId, users, perm) {
    var container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = '';
    (users || []).forEach(function(u) {
        var tag = document.createElement('span');
        tag.className = 'perm-users-tag';
        var rm = document.createElement('span');
        rm.className = 'remove-user';
        rm.setAttribute('data-container', containerId);
        rm.setAttribute('data-user', u);
        rm.textContent = '\u00d7';
        rm.addEventListener('click', function() { removePermUser(this.getAttribute('data-container'), this.getAttribute('data-user')); });
        tag.innerHTML = escapeHtml(u) + ' ';
        tag.appendChild(rm);
        container.appendChild(tag);
    });
    var addBtn = document.createElement('button');
    addBtn.className = 'add-user-btn';
    addBtn.innerHTML = '+';
    addBtn.onclick = function() { showUserSelectDropdown(container, containerId); };
    container.appendChild(addBtn);
}

function showUserSelectDropdown(container, containerId) {
    var existing = document.querySelector('.user-select-dropdown');
    if (existing) existing.remove();
    var rect = container.getBoundingClientRect();
    var dropdown = document.createElement('div');
    dropdown.className = 'user-select-dropdown';
    dropdown.style.top = (rect.bottom + 4) + 'px';
    dropdown.style.left = rect.left + 'px';
    var currentUsers = getCurrentUsers(containerId);
    var perm = containerIdToPerm(containerId);
    var permRole = perm ? getPermRoleValue(perm) : 'admin';
    var permRoleOrder = _ROLE_ORDER[permRole];
    var candidates = _permAllUsers.filter(function(user) {
        return _ROLE_ORDER[user.role] > permRoleOrder;
    });
    if (candidates.length === 0) {
        var hint = document.createElement('div');
        hint.className = 'user-select-item';
        hint.style.color = 'var(--text-muted)';
        hint.style.fontStyle = 'italic';
        hint.textContent = t('perm.role_covers_all');
        dropdown.appendChild(hint);
    } else {
        var searchInput = document.createElement('input');
        searchInput.type = 'text';
        searchInput.placeholder = t('perm.search_user');
        searchInput.style.cssText = 'width:100%;padding:6px 8px;border:none;border-bottom:1px solid var(--border-color);font-size:12px;background:transparent;color:var(--text-primary);outline:none;box-sizing:border-box;';
        var listContainer = document.createElement('div');
        listContainer.style.cssText = 'height:auto;min-height:40px;max-height:160px;overflow-y:auto;';
        var renderItems = function(filter) {
            listContainer.innerHTML = '';
            var filtered = candidates;
            if (filter) {
                var kw = filter.toLowerCase();
                filtered = candidates.filter(function(u) {
                    return u.username.toLowerCase().indexOf(kw) >= 0 || (u.nickname && u.nickname.toLowerCase().indexOf(kw) >= 0);
                });
            }
            if (filtered.length === 0 && filter) {
                var noResult = document.createElement('div');
                noResult.className = 'user-select-item';
                noResult.style.color = 'var(--text-muted)';
                noResult.style.fontStyle = 'italic';
                noResult.textContent = t('perm.no_match_user');
                listContainer.appendChild(noResult);
            }
            filtered.forEach(function(user) {
                var item = document.createElement('div');
                item.className = 'user-select-item' + (currentUsers.indexOf(user.username) >= 0 ? ' selected' : '');
                item.innerHTML = '<span class="check-icon"><i class="bi bi-check"></i></span>' + escapeHtml(user.username) + (user.nickname ? ' (' + escapeHtml(user.nickname) + ')' : '');
                item.onclick = function() {
                    if (currentUsers.indexOf(user.username) >= 0) {
                        removePermUserWithCascade(containerId, user.username);
                    } else {
                        addPermUserWithCascade(containerId, user.username);
                    }
                    dropdown.remove();
                };
                listContainer.appendChild(item);
            });
        };
        renderItems('');
        searchInput.addEventListener('input', function() {
            renderItems(searchInput.value.trim());
        });
        searchInput.addEventListener('mousedown', function(e) { e.stopPropagation(); });
        searchInput.addEventListener('click', function(e) { e.stopPropagation(); });
        dropdown.appendChild(searchInput);
        dropdown.appendChild(listContainer);
    }
    document.body.appendChild(dropdown);
    setTimeout(function() {
        var searchEl = dropdown.querySelector('input');
        if (searchEl) searchEl.focus();
        document.addEventListener('click', function handler(e) {
            if (!dropdown.contains(e.target)) {
                dropdown.remove();
                document.removeEventListener('click', handler);
            }
        });
    }, 10);
}

function getCurrentUsers(containerId) {
    var container = document.getElementById(containerId);
    if (!container) return [];
    var tags = container.querySelectorAll('.perm-users-tag');
    var users = [];
    tags.forEach(function(tag) {
        var text = tag.textContent.replace('\u00d7', '').trim();
        if (text) users.push(text);
    });
    return users;
}

function addPermUser(containerId, username) {
    var container = document.getElementById(containerId);
    if (!container) return;
    var tag = document.createElement('span');
    tag.className = 'perm-users-tag';
    var rm = document.createElement('span');
    rm.className = 'remove-user';
    rm.setAttribute('data-container', containerId);
    rm.setAttribute('data-user', username);
    rm.textContent = '\u00d7';
    rm.addEventListener('click', function() { removePermUser(this.getAttribute('data-container'), this.getAttribute('data-user')); });
    tag.innerHTML = escapeHtml(username) + ' ';
    tag.appendChild(rm);
    var addBtn = container.querySelector('.add-user-btn');
    container.insertBefore(tag, addBtn);
}

function removePermUser(containerId, username) {
    var container = document.getElementById(containerId);
    if (!container) return;
    var tags = container.querySelectorAll('.perm-users-tag');
    tags.forEach(function(tag) {
        if (tag.textContent.replace('\u00d7', '').trim() === username) {
            tag.remove();
        }
    });
}

// === Permission Cascade Logic ===
// Hierarchy: read(1) < execute(2) < download(3) < upload(4) < delete(5)
// Role order: admin(0) < user(1) < guest(2) — lower = broader
// Constraint: higher perm level must have ROLE_ORDER <= lower perm level's ROLE_ORDER
// i.e., delete <= upload <= download <= execute <= read (in ROLE_ORDER values)

function containerIdToPerm(containerId) {
    var match = containerId.match(/permEdit([A-Z][a-z]+)Users/);
    if (!match) return null;
    return match[1].charAt(0).toLowerCase() + match[1].slice(1);
}

function addPermUserWithCascade(containerId, username) {
    addPermUser(containerId, username);
}

function removePermUserWithCascade(containerId, username) {
    var perm = containerIdToPerm(containerId);
    if (!perm) { removePermUser(containerId, username); return; }
    var permLv = _PERM_LEVEL[perm];
    _PERMS.forEach(function(p) {
        if (_PERM_LEVEL[p] < permLv) {
            var targetId = 'permEdit' + capitalize(p) + 'Users';
            removePermUser(targetId, username);
        }
    });
    removePermUser(containerId, username);
}

function getPermRoleValue(perm) {
    var sel = document.getElementById('permEdit' + capitalize(perm) + 'Role');
    return sel ? sel.value : 'admin';
}

function isPermActive(perm) {
    var sel = document.getElementById('permEdit' + capitalize(perm) + 'Role');
    if (!sel) return false;
    var row = sel.closest('.perm-row');
    return row && row.style.display !== 'none';
}

function getAllowedRoles(perm) {
    var permLv = _PERM_LEVEL[perm];
    var maxOrder = 2;
    _PERMS.forEach(function(p) {
        if (_PERM_LEVEL[p] < permLv && p !== perm && isPermActive(p)) {
            var order = _ROLE_ORDER[getPermRoleValue(p)];
            if (order < maxOrder) maxOrder = order;
        }
    });
    var minOrder = 0;
    _PERMS.forEach(function(p) {
        if (_PERM_LEVEL[p] > permLv && p !== perm && isPermActive(p)) {
            var order = _ROLE_ORDER[getPermRoleValue(p)];
            if (order > minOrder) minOrder = order;
        }
    });
    if (minOrder > maxOrder) maxOrder = minOrder;
    return Object.keys(_ROLE_ORDER).filter(function(r) {
        return _ROLE_ORDER[r] >= minOrder && _ROLE_ORDER[r] <= maxOrder;
    });
}

function updateRoleDropdown(perm) {
    var sel = document.getElementById('permEdit' + capitalize(perm) + 'Role');
    if (!sel) return;
    var allowed = getAllowedRoles(perm);
    var currentVal = sel.value;
    var options = sel.querySelectorAll('option');
    options.forEach(function(opt) {
        var hidden = allowed.indexOf(opt.value) < 0;
        opt.style.display = hidden ? 'none' : '';
        opt.disabled = hidden;
    });
    if (allowed.indexOf(currentVal) < 0 && allowed.length > 0) {
        sel.value = allowed[0];
        currentVal = allowed[0];
    }
}

function updateAllRoleDropdowns() {
    _PERMS.forEach(function(p) { updateRoleDropdown(p); });
}

function cascadeFixValues(perm) {
    var permLv = _PERM_LEVEL[perm];
    var roleOrder = _ROLE_ORDER[getPermRoleValue(perm)];
    _PERMS.forEach(function(p) {
        if (p === perm) return;
        if (!isPermActive(p)) return;
        var targetSel = document.getElementById('permEdit' + capitalize(p) + 'Role');
        if (!targetSel) return;
        var targetOrder = _ROLE_ORDER[targetSel.value];
        if (_PERM_LEVEL[p] > permLv && targetOrder > roleOrder) {
            targetSel.value = getPermRoleValue(perm);
        } else if (_PERM_LEVEL[p] < permLv && targetOrder < roleOrder) {
            targetSel.value = getPermRoleValue(perm);
        }
    });
}

function resetAllRoleDropdowns() {
    _PERMS.forEach(function(p) {
        var sel = document.getElementById('permEdit' + capitalize(p) + 'Role');
        if (!sel) return;
        var options = sel.querySelectorAll('option');
        options.forEach(function(opt) {
            opt.style.display = '';
            opt.disabled = false;
        });
    });
}

function onPermEditRoleChange(perm) {
    if (_permCascadeRunning) return;
    var roleSel = document.getElementById('permEdit' + capitalize(perm) + 'Role');
    var role = roleSel.value;
    var containerId = 'permEdit' + capitalize(perm) + 'Users';
    var container = document.getElementById(containerId);
    if (!container) return;
    var currentUsers = getCurrentUsers(containerId);
    var cascaded = getCascadedRoles(role);
    var filtered = currentUsers.filter(function(username) {
        var userObj = _permAllUsers.find(function(u) { return u.username === username; });
        return !userObj || cascaded.indexOf(userObj.role) < 0;
    });
    container.innerHTML = '';
    filtered.forEach(function(u) {
        var tag = document.createElement('span');
        tag.className = 'perm-users-tag';
        var rm = document.createElement('span');
        rm.className = 'remove-user';
        rm.setAttribute('data-container', containerId);
        rm.setAttribute('data-user', u);
        rm.textContent = '\u00d7';
        rm.addEventListener('click', function() { removePermUser(this.getAttribute('data-container'), this.getAttribute('data-user')); });
        tag.innerHTML = escapeHtml(u) + ' ';
        tag.appendChild(rm);
        container.appendChild(tag);
    });
    var addBtn = document.createElement('button');
    addBtn.className = 'add-user-btn';
    addBtn.innerHTML = '+';
    addBtn.onclick = function() { showUserSelectDropdown(container, containerId); };
    container.appendChild(addBtn);
    _permCascadeRunning = true;
    cascadeFixValues(perm);
    updateAllRoleDropdowns();
    _permCascadeRunning = false;
}

// === Save & Add ===

async function savePermPathEdit() {
    if (!_permEditingRule) return;
    _PERMS.forEach(function(p) {
        var rule = collectPermissionRuleEdit(p);
        _permEditingRule[p] = rule;
    });
    _permEditingRule.isProtected = document.getElementById('permEditIsProtected').checked;
    try {
        var result = await api('/file/permission/save/path', {method: 'POST', body: JSON.stringify(_permEditingRule), headers: {'Content-Type': 'application/json'}});
        if (result.success) {
            showToast(t('perm.saved'), 'success');
            closePermPathEdit();
            reloadPermissionTree();
        } else {
            showToast(t('perm.save_failed') + ': ' + result.msg, 'error');
        }
    } catch (e) {
        showToast(t('perm.save_failed'), 'error');
    }
}

function closePermPathEdit() {
    document.getElementById('permPathEditModal').classList.remove('active');
    document.getElementById('permissionModal').style.display = '';
    _permEditingRule = null;
}

function collectPermissionRuleEdit(perm) {
    var role = document.getElementById('permEdit' + capitalize(perm) + 'Role').value;
    var users = getCurrentUsers('permEdit' + capitalize(perm) + 'Users');
    return {minRole: role, additionalUsers: users};
}

async function addPathRule() {
    var pathInput = document.getElementById('permNewPath');
    var path = pathInput.value.trim();
    if (!path) { showToast(t('perm.enter_path'), 'error'); return; }
    try {
        var normalized = path.replace(/\\/g, '/').replace(/\/+$/, '');
        var inherited = getInheritedFromParent(normalized);
        var rule = {path: normalized, isDirectory: true, isProtected: false};
        if (inherited) {
            rule.read = inherited.read;
            rule.download = inherited.download;
            rule.delete = inherited.delete;
            rule.execute = inherited.execute;
            rule.upload = inherited.upload;
            rule.isProtected = !!inherited.isProtected;
        }
        var result = await api('/file/permission/save/path', {method: 'POST', body: JSON.stringify(rule), headers: {'Content-Type': 'application/json'}});
        if (result.success) {
            showToast(t('perm.added'), 'success');
            pathInput.value = '';
            reloadPermissionTree();
        } else {
            showToast(t('perm.add_failed') + ': ' + result.msg, 'error');
        }
    } catch (e) {
        showToast(t('perm.add_failed'), 'error');
    }
}

// === Path Browser for Permission ===

function openPermPathBrowser() {
    setFileBrowserOnConfirm(function(selectedPath) {
        var selectedLi = document.querySelector('#fileBrowserContent .file-browser-list li.selected');
        var isDir = selectedLi ? selectedLi.getAttribute('data-isdir') === 'true' : true;
        try {
            hideFileBrowserModal(function() {
                setTimeout(function() {
                    var backdrops = document.querySelectorAll('.modal-backdrop');
                    backdrops.forEach(function(b) { b.remove(); });
                    if (document.body.classList.contains('modal-open')) {
                        document.body.classList.remove('modal-open');
                    }
                    openPermPathEditModal(selectedPath, isDir);
                }, 50);
            });
        } catch (e) {
            console.error('Open path edit error:', e);
            showToast(t('perm.open_failed'), 'error');
        }
    });
    openFileBrowser('', 'file', true);
}

function openPermPathEditModal(selectedPath, isDir) {
    var normalized = selectedPath.replace(/\\/g, '/').replace(/\/+$/, '');
    var inherited = getInheritedFromParent(normalized);
    _permEditingRule = {
        path: normalized,
        isDirectory: isDir,
        isProtected: false
    };
    if (inherited) {
        _permEditingRule.read = inherited.read;
        _permEditingRule.download = inherited.download;
        _permEditingRule.delete = inherited.delete;
        _permEditingRule.execute = inherited.execute;
        _permEditingRule.upload = inherited.upload;
        _permEditingRule.isProtected = !!inherited.isProtected;
    }
    document.getElementById('permEditPath').value = normalized;
    resetAllRoleDropdowns();
    _permCascadeRunning = true;
    _PERMS.forEach(function(p) {
        var rule = _permEditingRule[p];
        var roleSel = document.getElementById('permEdit' + capitalize(p) + 'Role');
        if (roleSel) roleSel.value = (rule && rule.minRole) || 'admin';
        renderPermUsers('permEdit' + capitalize(p) + 'Users', (rule && rule.additionalUsers) || [], p);
    });
    _permCascadeRunning = false;
    updateAllRoleDropdowns();
    document.getElementById('permEditIsProtected').checked = !!_permEditingRule.isProtected;
    if (!isDir) {
        var executeRow = document.getElementById('permEditExecuteRole');
        if (executeRow) executeRow.closest('.perm-row').style.display = 'none';
        var uploadRow = document.getElementById('permEditUploadRole');
        if (uploadRow) uploadRow.closest('.perm-row').style.display = 'none';
    } else {
        document.querySelectorAll('#permPathEditModal .perm-row').forEach(function(row) {
            row.style.display = '';
        });
    }
    document.getElementById('permissionModal').style.display = 'none';
    document.getElementById('permPathEditModal').classList.add('active');
}

function getInheritedFromParent(path) {
    if (!_permStore) return null;
    var normalized = path.replace(/\/+$/, '');
    if (!normalized || normalized === '/') return null;
    var bestMatch = null;
    var bestLen = -1;
    function scanTree(node) {
        if (!node) return;
        var rule = node.filePermissionRule;
        if (rule && rule.path) {
            var rulePath = rule.path.replace(/\/+$/, '');
            if (normalized !== rulePath && normalized.startsWith(rulePath + '/')) {
                if (rulePath.length > bestLen) {
                    bestMatch = rule;
                    bestLen = rulePath.length;
                }
            }
        }
        if (node.childrenMap) {
            Object.keys(node.childrenMap).forEach(function(key) {
                scanTree(node.childrenMap[key]);
            });
        }
    }
    scanTree(_permStore);
    return bestMatch;
}

// === filePermission Bit Parsing & Icon Rendering ===

function parsePermissionBits(bits) {
    if (bits === undefined || bits === null || bits === 0) return null;
    return {
        isFolder: !!(bits & 0x4000),
        read: !!(bits & 0x0001),
        execute: !!(bits & 0x0004),
        download: !!(bits & 0x0010),
        upload: !!(bits & 0x0040),
        delete: !!(bits & 0x0100)
    };
}

function hasDownloadPermission(filePermission) {
    if (!filePermission) return false;
    return (filePermission & 0x0010) !== 0;
}

function hasDeletePermission(filePermission) {
    if (!filePermission) return false;
    return (filePermission & 0x0100) !== 0;
}

function hasExecutePermission(filePermission) {
    if (!filePermission) return false;
    return (filePermission & 0x0004) !== 0;
}

function hasUploadPermission(filePermission) {
    if (!filePermission) return false;
    return (filePermission & 0x0040) !== 0;
}

function isPathProtected(filePermission) {
    if (!filePermission) return false;
    return (filePermission & 0x0400) !== 0;
}

function updateUploadButtonVisibility(filePermission) {
    var btn = document.getElementById('uploadToolbarBtn');
    if (!btn) return;
    btn.style.display = hasUploadPermission(filePermission) ? '' : 'none';
}

function updateCreateFolderButtonVisibility(filePermission) {
    var btn = document.getElementById('createFolderBtn');
    if (!btn) return;
    btn.style.display = hasUploadPermission(filePermission) ? '' : 'none';
}

function getPermIconHTML(hasPerm, text) {
    if (hasPerm) return '<span class="perm-text">' + text + '</span>';
    return '';
}

function getFolderPermIconHTML(filePermission) {
    var parsed = parsePermissionBits(filePermission);
    if (!parsed) return '';
    var icons = [];
    if (!parsed.execute) {
        icons.push('<span class="perm-icon locked" title="' + t('perm.no_execute') + '"><i class="bi bi-lock-fill"></i></span>');
    }
    icons.push(getPermIconHTML(parsed.download, t('perm.download')));
    icons.push(getPermIconHTML(parsed.delete, t('perm.delete')));
    return icons.join('');
}

function getFilePermIconHTML(filePermission) {
    var parsed = parsePermissionBits(filePermission);
    if (!parsed) return '';
    var icons = [];
    icons.push(getPermIconHTML(parsed.download, t('perm.download')));
    icons.push(getPermIconHTML(parsed.delete, t('perm.delete')));
    return icons.join('');
}

// === Utility ===

function capitalize(s) { return s.charAt(0).toUpperCase() + s.slice(1); }

function escapeJs(s) { return s.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"'); }

function showPermHelpDialog() {
    document.getElementById('permHelpModal').classList.add('active');
}

function closePermHelpDialog() {
    document.getElementById('permHelpModal').classList.remove('active');
}


