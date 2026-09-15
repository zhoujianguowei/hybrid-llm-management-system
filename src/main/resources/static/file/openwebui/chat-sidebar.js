let sidebarLoadingMore = false;

async function newChat() {
    if (!selectedChatModel) {
        showAlert(t('chat.select_model_first'), 'warning');
        return;
    }

    const config = selectedChatModel.openApiLLMConfig;
    const request = {
        apiConfigId: config ? config.baseUrl : '',
        apiConfigName: config ? config.baseUrl : '',
        modelName: selectedChatModel.modelName
    };
    
    try {
        const result = await api('/chat/sessions/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        });
        
        if (result.success) {
            if (typeof closeHtmlPreview === 'function') closeHtmlPreview();
            const prevChatId = currentChatSessionId;
            currentChatSessionId = result.data.chatId;
            currentTotalTokens = 0;
            currentPromptPerSecond = null;
            currentPredictedPerSecond = null;
            isGenerating = false;
            currentAssistantMessage = '';
            currentThinkingContent = null;
            currentMessageElement = null;
            // Cancel any scroll-retry loop the previously loaded session started
            // before we swap the container to the welcome screen.
            sessionRenderToken++;
            clearMessages();
            await saveUserContextField('lastOpenedChatId', currentChatSessionId);
            loadSessionList();
            connectWebSocket(currentChatSessionId);
            updateContextUsage();
            updateSendButtonState();
            document.querySelector('.btn-send').style.display = 'flex';
            document.getElementById('btnStop').style.display = 'none';
            thinkingEnabled = false;
            currentThinkingLevel = 'no_think';
            sessionThinkingState.set(currentChatSessionId, 'no_think');
            updateThinkButton();
            updateAttachReasoningButton();
            // 清理上一个会话的运行时参数状态，避免残留
            if (prevChatId) sessionRuntimeConfigState.delete(prevChatId);
            syncRuntimeSidebar();
        }
    } catch (error) {
        console.error('Create session failed:', error);
        showAlert(t('chat.create_session_failed'), 'error');
    }
}

async function loadSessionList() {
    sessionOffset = 0;
    sessions = [];

    try {
        const [listResult, userResult] = await Promise.all([
            api('/chat/sessions/list', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ offset: 0, limit: SESSION_PAGE_SIZE })
            }),
            api('/file/user/current')
        ]);

        const chatContext = userResult.success && userResult.data.userContext ? userResult.data.userContext.chatContext : null;
        if (userResult.success) { window.__currentUserData = userResult.data; currentUserData = userResult.data; }
        const activeChatIds = new Set(chatContext ? chatContext.activeChatIdList || [] : []);

        if (chatContext && chatContext.lastPinnedSessionOpenedStatus !== undefined) {
            pinnedExpanded = chatContext.lastPinnedSessionOpenedStatus;
        }

        // Restore sidebar collapsed state
        if (chatContext && chatContext.sidebarCollapsed) {
            const sidebar = document.getElementById('sidebar');
            if (sidebar) sidebar.classList.add('collapsed');
        }

        if (listResult.success) {
            sessions = listResult.data.sessions;
            sessions.forEach(s => { s.isGenerating = activeChatIds.has(s.chatId); });
            sessionOffset = SESSION_PAGE_SIZE;
            renderSessionList();
            document.getElementById('loadMoreContainer').style.display = listResult.data.hasMore ? 'block' : 'none';
            initSidebarInfiniteScroll();
        }
        await loadPinnedSessions(activeChatIds);

        // Reconnect WebSocket for background generating sessions so they can receive stream_end
        activeChatIds.forEach(chatId => {
            if (!wsMap.has(chatId)) {
                connectWebSocket(chatId, true);
            }
        });

        // Decide which session to auto-load: lastOpenedChatId -> first normal -> first pinned
        if (!currentChatSessionId && chatContext && chatContext.lastOpenedChatId) {
            const loaded = await loadSession(chatContext.lastOpenedChatId);
            if (loaded) return;
        }

        // Fallback: first normal session, then first pinned session
        if (!currentChatSessionId && sessions.length > 0) {
            loadSession(sessions[0].chatId);
        } else if (!currentChatSessionId && pinnedSessions.length > 0) {
            pinnedExpanded = true;
            loadSession(pinnedSessions[0].chatId);
        }
    } catch (error) {
        console.error('Load session list failed:', error);
    }
}

async function loadPinnedSessions(activeChatIds) {
    pinnedOffset = 0;
    pinnedSessions = [];

    try {
        const result = await api('/chat/sessions/pinned', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ offset: 0, limit: PINNED_PAGE_SIZE })
        });

        if (result.success) {
            pinnedSessions = result.data.sessions;
            if (activeChatIds) {
                pinnedSessions.forEach(s => { s.isGenerating = activeChatIds.has(s.chatId); });
            }
            pinnedOffset = PINNED_PAGE_SIZE;
            renderPinnedList();
            if (result.data.sessions.length > 0) {
                document.getElementById('pinnedContainer').style.display = 'block';
                applyPinnedExpanded();
            } else {
                document.getElementById('pinnedContainer').style.display = 'none';
            }
            document.getElementById('pinnedLoadMore').style.display = result.data.hasMore ? 'block' : 'none';
        }
    } catch (error) {
        console.error('Load pinned sessions failed:', error);
    }
}

function renderPinnedList() {
    const container = document.getElementById('pinnedList');
    const badge = document.getElementById('pinnedCountBadge');
    if (badge) badge.textContent = pinnedSessions.length;
    container.innerHTML = pinnedSessions.map(session => {
        const isActive = session.chatId === currentChatSessionId;
       const actions = `
            <div class="conversation-actions">
                <span class="action-dot" onclick="event.stopPropagation();togglePinSession('${session.chatId}', 0)" title="${t('chat.unpin')}">
                    <i class="bi bi-pin-angle-fill"></i>
                </span>
                <span class="action-dot" onclick="event.stopPropagation();showRenameSessionModal('${session.chatId}')" title="${t('chat.rename')}">
                    <i class="bi bi-pencil-fill"></i>
                </span>
                ${!isActive && !session.isGenerating ? `<span class="action-dot" onclick="event.stopPropagation();deleteSession('${session.chatId}')"><i class="bi bi-trash"></i></span>` : ''}
            </div>
        `;
        return `
        <div class="conversation-item pinned-item ${isActive ? 'active' : ''} ${session.isGenerating ? 'is-generating' : ''}" data-chat-id="${session.chatId}">
            <div class="conversation-title">${session.isGenerating ? '<span class="session-spinner"></span>' : ''}<i class="bi bi-pin-angle-fill pinned-item-icon"></i><span class="session-title-text">${escapeHtml(session.title && hasMsgKey(session.title) ? t(session.title) : session.title || t('chat.unnamed_conversation'))}</span></div>
            <div class="conversation-meta"><span class="session-meta-time">${formatTime(session.createdAt)}</span><span class="session-meta-count"> · ${session.messageCount || 0}${t('chat.unit_messages')}</span></div>
            ${actions}
        </div>
        `;
    }).join('');

    container.querySelectorAll('.conversation-item').forEach(item => {
        item.addEventListener('click', (e) => {
            if (e.target.closest('.action-dot')) return;
            const chatId = item.dataset.chatId;
            if (chatId && chatId !== currentChatSessionId) {
                loadSession(chatId);
            }
        });
        const titleEl = item.querySelector('.session-title-text');
        if (titleEl && titleEl.scrollWidth > titleEl.clientWidth) {
            item.title = titleEl.textContent;
        }
    });

    // Ensure active session item is visible in pinned list
    const activeItem = container.querySelector('.conversation-item.active');
    if (activeItem) {
        activeItem.scrollIntoView({ block: 'nearest', behavior: 'auto' });
    }
}

function applyPinnedExpanded() {
    const list = document.getElementById('pinnedList');
    const loadMore = document.getElementById('pinnedLoadMore');
    const chevron = document.getElementById('pinnedChevron');
    if (pinnedExpanded) {
        list.style.display = 'block';
        loadMore.style.display = loadMore.style.display !== 'none' ? loadMore.style.display : 'none';
        chevron.style.transform = 'none';
    } else {
        list.style.display = 'none';
        loadMore.style.display = 'none';
        chevron.style.transform = 'rotate(-90deg)';
    }
}

async function togglePinnedSection() {
    pinnedExpanded = !pinnedExpanded;
    applyPinnedExpanded();
    await saveUserContextField('lastPinnedSessionOpenedStatus', pinnedExpanded);
}

function isCurrentSessionPinned() {
    if (!currentChatSessionId) return false;
    return pinnedSessions.some(s => s.chatId === currentChatSessionId);
}

function getCurrentUserContext() {
    const user = window.__currentUserData;
    if (user && user.userContext && user.userContext.chatContext) {
        return user.userContext.chatContext;
    }
    return null;
}

function saveUserContextField(field, value) {
    const ctx = getCurrentUserContext();
    if (!ctx) return;
    ctx[field] = value;
    return saveUserContext(ctx);
}

async function saveUserContext(chatContext) {
    try {
        await api('/chat/context', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ chatContext: chatContext })
        });
    } catch (e) {
        console.error('Save user context failed:', e);
    }
}

async function loadMorePinnedSessions() {
    try {
        const result = await api('/chat/sessions/pinned', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ offset: pinnedOffset, limit: PINNED_PAGE_SIZE })
        });

        if (result.success) {
            pinnedSessions = [...pinnedSessions, ...result.data.sessions];
            pinnedOffset += PINNED_PAGE_SIZE;
            renderPinnedList();
            document.getElementById('pinnedLoadMore').style.display = result.data.hasMore ? 'block' : 'none';
        }
    } catch (error) {
        console.error('Load more pinned sessions failed:', error);
    }
}

async function togglePinSession(chatId, type) {
    if (type === 1 && sessionConfig) {
        if (pinnedSessions.length >= sessionConfig.pinnedSessionLimit) {
            showAlert(`${t('chat.pin_limit_reached')} ${sessionConfig.pinnedSessionLimit} ${t('chat.unit_count')}`, 'warning');
            return;
        }
    }
    try {
        const result = await api(`/chat/sessions/pin?chatId=${chatId}&type=${type}`, {
            method: 'POST'
        });

        if (result.success) {
            await loadSessionList();
            renderSessionList();
            renderPinnedList();
        } else {
            showAlert(result.msg || t('chat.operation_failed'), 'warning');
        }
    } catch (error) {
        console.error('Toggle pin failed:', error);
        showAlert(t('chat.operation_failed'), 'error');
    }
}

// 重渲染 innerHTML 会丢失滚动位置; 删除会话等场景先捕获视口顶部第一个可见
// 会话作为锚点, 渲染后再把该会话恢复到同一相对位置, 列表就不会跳回顶部
function captureSidebarScrollAnchor(container) {
    const anchor = { chatId: null, offset: 0, scrollTop: container.scrollTop };
    const containerTop = container.getBoundingClientRect().top;
    let el = container.firstElementChild;
    while (el) {
        const rect = el.getBoundingClientRect();
        if (rect.bottom > containerTop) {
            anchor.chatId = el.dataset.chatId || null;
            anchor.offset = rect.top - containerTop;
            break;
        }
        el = el.nextElementSibling;
    }
    return anchor;
}

function restoreSidebarScrollAnchor(container, anchor) {
    if (anchor.chatId) {
        const el = container.querySelector('.conversation-item[data-chat-id="' + anchor.chatId + '"]');
        if (el) {
            const delta = (el.getBoundingClientRect().top - container.getBoundingClientRect().top) - anchor.offset;
            container.scrollTop += delta;
            return;
        }
    }
    container.scrollTop = anchor.scrollTop;
}

function renderSessionList(scrollToActive) {
    const container = document.getElementById('conversationList');
    container.innerHTML = sessions.map(session => {
        const isActive = session.chatId === currentChatSessionId;
        const canDelete = !isActive && !session.isGenerating;
        const actions = `
            <div class="conversation-actions">
                <span class="action-dot" onclick="event.stopPropagation();togglePinSession('${session.chatId}', 1)" title="${t('chat.pin')}">
                    <i class="bi bi-pin-angle"></i>
                </span>
                <span class="action-dot" onclick="event.stopPropagation();showRenameSessionModal('${session.chatId}')">
                    <i class="bi bi-pencil-fill"></i>
                </span>
                ${canDelete ? `<span class="action-dot" onclick="deleteSession('${session.chatId}')">
                    <i class="bi bi-trash"></i>
                </span>` : ''}
            </div>
        `;
        return `
        <div class="conversation-item ${isActive ? 'active' : ''}" data-chat-id="${session.chatId}">
            <div class="conversation-title">${session.isGenerating ? '<span class="session-spinner"></span>' : ''}<span class="session-title-text">${escapeHtml(session.title && hasMsgKey(session.title) ? t(session.title) : session.title || t('chat.unnamed_conversation'))}</span></div>
            <div class="conversation-meta"><span class="session-meta-time">${formatTime(session.createdAt)}</span><span class="session-meta-count"> · ${session.messageCount || 0}${t('chat.unit_messages')}</span></div>
            ${actions}
        </div>
        `;
    }).join('');
    
    container.querySelectorAll('.conversation-item').forEach(item => {
        item.addEventListener('click', (e) => {
            const actionDot = e.target.closest('.action-dot');
            if (actionDot) {
                return;
            }
            const chatId = item.dataset.chatId;
            if (chatId && chatId !== currentChatSessionId) {
                loadSession(chatId);
            }
        });
        const titleEl = item.querySelector('.session-title-text');
        if (titleEl && titleEl.scrollWidth > titleEl.clientWidth) {
            item.title = titleEl.textContent;
        }
    });

    // Ensure active session item is visible in sidebar
    if (scrollToActive !== false) {
        const activeItem = container.querySelector('.conversation-item.active');
        if (activeItem) {
            activeItem.scrollIntoView({ block: 'nearest', behavior: 'auto' });
        }
    }
}

async function loadMoreSessions() {
     if (sidebarLoadingMore) return;
     sidebarLoadingMore = true;
     try {
         const result = await api('/chat/sessions/list', {
             method: 'POST',
             headers: { 'Content-Type': 'application/json' },
             body: JSON.stringify({ offset: sessionOffset, limit: SESSION_PAGE_SIZE })
         });

        if (result.success) {
              const container = document.getElementById('conversationList');
              const scrollTopBefore = container ? container.scrollTop : 0;
              sessions = [...sessions, ...result.data.sessions];
              sessionOffset += SESSION_PAGE_SIZE;
              renderSessionList(false);
              if (container) container.scrollTop = scrollTopBefore;
              updateSidebarEndMessage(result.data.hasMore);
              document.getElementById('loadMoreContainer').style.display = result.data.hasMore ? 'block' : 'none';
          }
     } catch (error) {
         console.error('Load more sessions failed:', error);
     } finally {
         sidebarLoadingMore = false;
     }
 }

 function updateSidebarEndMessage(hasMore) {
     let endMsg = document.getElementById('sidebarEndMessage');
     if (!endMsg) {
         endMsg = document.createElement('div');
         endMsg.id = 'sidebarEndMessage';
         endMsg.className = 'sidebar-end-message';
         const loadMoreContainer = document.getElementById('loadMoreContainer');
         if (loadMoreContainer) {
             loadMoreContainer.parentNode.insertBefore(endMsg, loadMoreContainer.nextSibling);
         } else {
             document.getElementById('sidebar').appendChild(endMsg);
         }
     }
     if (hasMore) {
         endMsg.textContent = '';
         endMsg.style.display = 'none';
     } else if (sessions.length > 0) {
          endMsg.textContent = '— ' + t('chat.reached_bottom') + ' —';
         endMsg.style.display = 'block';
     } else {
         endMsg.textContent = '';
         endMsg.style.display = 'none';
     }
 }

 let sidebarScrollListenerAttached = false;

 function initSidebarInfiniteScroll() {
     const list = document.getElementById('conversationList');
     if (!list || sidebarScrollListenerAttached) return;
     sidebarScrollListenerAttached = true;
     list.addEventListener('scroll', () => {
         if (sidebarLoadingMore) return;
         const threshold = 50;
         if (list.scrollHeight - list.scrollTop - list.clientHeight < threshold) {
             loadMoreSessions();
         }
     });
 }

async function deleteSession(chatId, event) {
    if (event) {
        event.stopPropagation();
    }

    if (currentChatSessionId === chatId) {
        showAlert(t('chat.cannot_delete_current'), 'warning');
        return;
    }

    const allSessions = [...sessions, ...pinnedSessions];
    const targetSession = allSessions.find(s => s.chatId === chatId);
    if (targetSession && targetSession.isGenerating) {
        showAlert(t('chat.cannot_delete_generating'), 'warning');
        return;
    }

    try {
        const result = await api(`/chat/sessions/delete?chatId=${chatId}`, {
            method: 'POST'
        });
        
        if (result.success) {
            if (currentChatSessionId === chatId) {
                currentChatSessionId = null;
                clearMessages();
            }
            // 删除后保持侧边栏原滚动位置, 不跳回顶部/激活项
            const listContainer = document.getElementById('conversationList');
            const scrollAnchor = listContainer ? captureSidebarScrollAnchor(listContainer) : null;
            sessions = sessions.filter(s => s.chatId !== chatId);
            pinnedSessions = pinnedSessions.filter(s => s.chatId !== chatId);
            renderSessionList(false);
            renderPinnedList();
            if (scrollAnchor) restoreSidebarScrollAnchor(listContainer, scrollAnchor);
            if (sessions.length > 0 && !currentChatSessionId) {
                loadSession(sessions[0].chatId);
            }
        }
    } catch (error) {
        console.error('Delete session failed:', error);
        showAlert(t('chat.delete_failed'), 'error');
    }
}

function toggleSessionMenu(dot, event) {
    event.stopPropagation();
    const menu = dot.nextElementSibling;
    menu.style.display = menu.style.display === 'none' ? 'block' : 'none';
}

window.addEventListener('click', () => {
    document.querySelectorAll('.session-menu').forEach(menu => {
        menu.style.display = 'none';
    });
});

async function loadSession(sessionId) {
    if (typeof closeHtmlPreview === 'function') closeHtmlPreview();
    // Invalidate any scroll-retry loop left running by the previous session.
    sessionRenderToken++;
    currentChatSessionId = sessionId;
    currentMessageElement = null;
    currentAssistantMessage = '';
    currentThinkingContent = null;
    isGenerating = false;
    allMessages = [];
    renderedMessageStart = 0;
    messageScrollLoading = false;
    autoScrollDisabled = false;
    if (messageSentinelObserver) {
        messageSentinelObserver.disconnect();
        messageSentinelObserver = null;
    }
    const container = document.getElementById('messagesContainer');
    if (container && container._waitForImagesScrollTimer) {
        clearTimeout(container._waitForImagesScrollTimer);
        container._waitForImagesScrollTimer = null;
    }
    renderSessionList();
    renderPinnedList();
    if (isCurrentSessionPinned()) {
        pinnedExpanded = true;
        applyPinnedExpanded();
    }
    updateSendButtonState();
    document.querySelector('.btn-send').style.display = 'flex';
    document.getElementById('btnStop').style.display = 'none';

  const existingWs = wsMap.get(sessionId);
    if (existingWs) {
        switchingSessionChatId = sessionId;
        existingWs.pageIsUnloading = true;
        existingWs.close();
        wsMap.delete(sessionId);
    }
    connectWebSocket(sessionId); 
    try {
        const result = await api(`/chat/sessions/load?chatSessionId=${sessionId}`, {
            method: 'POST'
        });
        // 用户可能在请求期间切换了会话，丢弃过期响应避免渲染错乱
        if (sessionId !== currentChatSessionId) return false;

     if (result.success && result.data) {
            // Sidebar active state was already rendered before the await; a
            // second rebuild here only restarts the active-item scroll and adds
            // switch-time jitter, so it is skipped.
            renderMessages(result.data.messages || []);

            // Clear sidebar spinner if generation already completed (backend sent stream_end to closed WebSocket)
            const loadedMessages = result.data.messages || [];
            const lastAsstMsg = [...loadedMessages].reverse().find(m => m.role === 'assistant');
            if (lastAsstMsg && lastAsstMsg.status && !isInRunning(lastAsstMsg.status)) {
                const foundSession = sessions.find(s => s.chatId === sessionId);
                if (foundSession) foundSession.isGenerating = false;
                const foundPinnedSession = pinnedSessions.find(s => s.chatId === sessionId);
                if (foundPinnedSession) foundPinnedSession.isGenerating = false;
                renderSessionList();
                renderPinnedList();
            }

            await loadChatModels();

            if (result.data.modelName) {
                const matchedIndex = chatModels.findIndex(m => m.modelName === result.data.modelName);
                if (matchedIndex >= 0) {
                    selectModel(matchedIndex, true);
                } else if (chatModels.length > 0) {
                    selectModel(0, true);
                }
            }

            if (result.data.thinkingMode !== null && result.data.thinkingMode !== undefined && result.data.thinkingMode !== '') {
                const tm = result.data.thinkingMode;
                thinkingEnabled = tm === true
                    || (tm !== false && tm !== 'false' && tm !== 'no_think' && tm !== 'disabled');
                if (tm === true) {
                    currentThinkingLevel = 'true';
                } else if (thinkingEnabled && typeof tm === 'string' && tm !== 'false') {
                    currentThinkingLevel = tm;
                } else {
                    currentThinkingLevel = 'no_think';
                }
                sessionThinkingState.set(sessionId, currentThinkingLevel);
            } else {
                thinkingEnabled = false;
                currentThinkingLevel = 'no_think';
                sessionThinkingState.set(sessionId, 'no_think');
            }
            updateThinkButton();

            restoreSessionAttachReasoning(sessionId, result.data.chatRuntimeConfig);
            updateAttachReasoningButton();
            restoreSessionRuntimeConfig(sessionId, result.data.chatRuntimeConfig);
            syncRuntimeSidebar();

            if (result.data.stats) {
                displayStats(result.data.stats);
            }

            const messages = result.data.messages || [];
            currentTotalTokens = 0;
            currentPromptPerSecond = null;
            currentPredictedPerSecond = null;
            for (let i = messages.length - 1; i >= 0; i--) {
                if (messages[i].role === 'assistant' && messages[i].stats && messages[i].stats.totalTokens != null && messages[i].stats.totalTokens > 0) {
                    currentTotalTokens = messages[i].stats.totalTokens;
                    if (messages[i].stats.promptPerSecond != null) currentPromptPerSecond = messages[i].stats.promptPerSecond;
                    if (messages[i].stats.predictedPerSecond != null) currentPredictedPerSecond = messages[i].stats.predictedPerSecond;
                    break;
                }
            }
            updateContextUsage();

            // Always send check_recovery after loading session
            waitForWebSocket(sessionId).then(() => {
                if (sessionId !== currentChatSessionId) return;
                const ws = wsMap.get(sessionId);
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({ type: 'check_recovery', sessionId: getCurrentSessionId(), chatId: sessionId }));
                }
            });
        } else {
            currentChatSessionId = null;
            resetAutoScroll();
            scrollToBottom();
            return false;
        }
        resetAutoScroll();
        scrollToBottom();
        return true;
    } catch (error) {
        console.error('Load session failed:', error);
        showAlert(t('chat.load_session_failed'), 'error');
        return false;
    }
}

function showRenameSessionModal(chatId) {
    const session = sessions.find(s => s.chatId === chatId);
    const titleInput = document.getElementById('renameSessionTitle');
    titleInput.value = session ? (session.title ? t(session.title) : '') : '';
    document.getElementById('renameSessionChatId').value = chatId;
    document.getElementById('renameSessionModal').classList.add('active');
    setTimeout(() => titleInput.focus(), 100);
}

function closeRenameSessionModal() {
    document.getElementById('renameSessionModal').classList.remove('active');
}

async function saveRenameSession() {
    const chatId = document.getElementById('renameSessionChatId').value;
    const title = document.getElementById('renameSessionTitle').value.trim();
    if (!title) {
        showAlert(t('chat.title_required'), 'warning');
        return;
    }
    try {
        const result = await api(`/chat/sessions/rename?chatId=${chatId}&title=${encodeURIComponent(title)}`, {
            method: 'POST'
        });
        if (result.success) {
            const session = sessions.find(s => s.chatId === chatId);
            if (session) session.title = title;
            const pinnedSession = pinnedSessions.find(s => s.chatId === chatId);
            if (pinnedSession) pinnedSession.title = title;
            renderSessionList();
            renderPinnedList();
            closeRenameSessionModal();
        } else {
            showAlert(result.msg || t('chat.rename_failed'), 'warning');
        }
    } catch (error) {
        console.error('Rename session failed:', error);
        showAlert(t('chat.rename_failed'), 'error');
    }
}

document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        closeRenameSessionModal();
    }
});

document.getElementById('renameSessionForm').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') {
        e.preventDefault();
        saveRenameSession();
    }
});
