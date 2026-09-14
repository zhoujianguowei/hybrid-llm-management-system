function isInRunning(status) {
    return status === 'INIT_BEFORE' || status === 'INIT_FINISHED' || status === 'THINKING' || status === 'GENERATION';
}

function findBacktickFence(text, fence) {
    var minLen = fence.length;
    var i = 0;
    while (i < text.length) {
        if (text[i] === '`') {
            var j = i;
            while (j < text.length && text[j] === '`') j++;
            if (j - i >= minLen) return i;
            i = j;
        } else {
            i++;
        }
    }
    return -1;
}

function normalizeCodeFences(text) {
    var lines = text.split('\n');
    var result = [];
    var inCode = false;
    var curFence = '';

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];
        var indent = line.match(/^\s*/)[0];
        var trimmed = line.trim();

        if (!inCode) {
            var openMatch = trimmed.match(/^(```+)([a-zA-Z_][\w+#-]*)?([\s\S]*)$/);
            if (openMatch && openMatch[1]) {
                var fence = openMatch[1];
                var lang = openMatch[2] || '';
                var rest = (openMatch[3] || '').trim();

                if (rest && !rest.startsWith(fence)) {
                    var closeIdx = findBacktickFence(rest, fence);
                    result.push(indent + fence + lang);
                    if (closeIdx >= 0) {
                        var codeContent = rest.substring(0, closeIdx);
                        var afterClose = rest.substring(closeIdx + fence.length);
                        result.push(indent + codeContent);
                        result.push(indent + fence);
                        inCode = false;
                        if (afterClose.trim()) {
                            result.push(indent + afterClose.trim());
                        }
                    } else {
                        result.push(indent + rest);
                        inCode = true;
                        curFence = fence;
                    }
                } else if (rest && rest.startsWith(fence)) {
                    result.push(indent + fence + lang);
                    result.push('');
                    result.push(indent + fence);
                } else {
                    result.push(line);
                    inCode = true;
                    curFence = fence;
                }
            } else {
                result.push(line);
            }
        } else {
            var fenceRe = new RegExp('^\\s*(' + curFence.replace(/`/g, '\\`') + '+)[~`]*\\s*$');
            if (trimmed.match(fenceRe)) {
                inCode = false;
                curFence = '';
                result.push(line);
            } else {
                // Only treat a backtick run as the closing fence when it reaches
                // the end of the line; otherwise it is just code content (e.g.
                // a nested ```lang fence or a string containing backticks).
                var idx = findBacktickFence(line, curFence);
                if (idx >= 0) {
                    var afterIdx = idx;
                    while (afterIdx < line.length && line.charAt(afterIdx) === '`') afterIdx++;
                    var lineAfter = line.substring(afterIdx).replace(/[~\s]*$/, '');
                    if (lineAfter === '') {
                        var before = line.substring(0, idx);
                        result.push(before);
                        result.push(indent + curFence);
                        inCode = false;
                        curFence = '';
                    } else {
                        result.push(line);
                    }
                } else {
                    result.push(line);
                }
            }
        }
    }
    return result.join('\n');
}

// LLMs frequently emit ATX headings without the space CommonMark requires
// between the # run and the text (e.g. `###最常用的库/框架：`), which marked
// then renders as a literal paragraph. Insert the missing space so such lines
// become real headings. Fence-aware so code content is never touched.
function normalizeHeadings(text) {
    var lines = text.split('\n');
    var inCode = false;
    var curFenceChar = '';
    var curFenceLen = 0;

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];
        var trimmed = line.trim();

        var fenceMatch = /^(`{3,}|~{3,})/.exec(trimmed);
        if (fenceMatch) {
            var fence = fenceMatch[1];
            if (!inCode) {
                inCode = true;
                curFenceChar = fence.charAt(0);
                curFenceLen = fence.length;
            } else if (curFenceChar === fence.charAt(0) &&
                       fence.length >= curFenceLen &&
                       new RegExp('^' + fence.charAt(0) + '{' + curFenceLen + ',}\\s*$').test(trimmed)) {
                inCode = false;
            }
            continue;
        }

        if (inCode) continue;

        // ATX heading candidate: 1-6 # at line start (0-3 space indent per
        // CommonMark) directly followed by non-whitespace. Skip when the next
        // char is an ASCII letter so lines like `#include <stdio.h>` keep
        // their literal meaning; CJK/digit/punctuation starts in model output
        // are almost always meant as headings.
        var headMatch = /^( {0,3})(#{1,6})(\S)/.exec(line);
        if (headMatch && headMatch[3] !== '#' && !/[A-Za-z]/.test(headMatch[3])) {
            var insertAt = headMatch[1].length + headMatch[2].length;
            lines[i] = line.substring(0, insertAt) + ' ' + line.substring(insertAt);
        }
    }
    return lines.join('\n');
}

// Models (or a stream that loses newlines) sometimes glue whole markdown
// blocks onto a single line: `...文字---### 标题####1. 正文` or a table
// flattened to `...场景|说明 ||---|---|| 行...`. marked only recognizes block
// syntax at line starts, so such headings, thematic breaks and tables render
// as literal text. Insert the missing line breaks before mid-line block
// markers. Fence-aware (line-start fences) and inline-code-span-aware; real
// line-based tables are detected and left untouched.
function normalizeMidLineBlocks(text) {
    // Fast path must cover every marker the branches below act on: # (headings),
    // - (hr/list/delimiter), + (list), | (tables).
    if (!text || !/[#|+-]/.test(text)) return text;
    var lines = text.split('\n');

    // Protect the lines of genuine (line-based) GFM tables: a delimiter row
    // (`|---|---|`) plus the contiguous header/body rows around it.
    var isDelimiterRow = function (line) {
        var t = line.trim();
        return t.length >= 3 && t.indexOf('|') >= 0 && t.indexOf('--') >= 0 && /^\|?[-: |]+\|?$/.test(t);
    };
    var tableLine = {};
    for (var t = 0; t < lines.length; t++) {
        if (!isDelimiterRow(lines[t])) continue;
        tableLine[t] = true;
        for (var u = t - 1; u >= 0 && !/^[ \t]*$/.test(lines[u]) && lines[u].indexOf('|') >= 0; u--) tableLine[u] = true;
        for (var d = t + 1; d < lines.length && !/^[ \t]*$/.test(lines[d]) && lines[d].indexOf('|') >= 0; d++) tableLine[d] = true;
    }

    var inCode = false;
    var curFenceChar = '';
    var curFenceLen = 0;
    var out = [];

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];
        var trimmed = line.trim();

        var fenceMatch = /^(`{3,}|~{3,})/.exec(trimmed);
        if (fenceMatch) {
            var fence = fenceMatch[1];
            if (!inCode) {
                inCode = true;
                curFenceChar = fence.charAt(0);
                curFenceLen = fence.length;
            } else if (curFenceChar === fence.charAt(0) &&
                       fence.length >= curFenceLen &&
                       new RegExp('^' + fence.charAt(0) + '{' + curFenceLen + ',}\\s*$').test(trimmed)) {
                inCode = false;
            }
            out.push(line);
            continue;
        }
        if (inCode || tableLine[i]) {
            out.push(line);
            continue;
        }

        var masked = maskInlineCodeSpans(line);
        // splitAt[pos] = separator inserted before position pos. Blocks
        // (headings, hr, lists, table start) need a blank line before them;
        // table rows must stay contiguous (single newline) or the table dies.
        var splitAt = {};
        var m;

        // `text####heading` / `---### heading`: a # run of 2-6 glued to the
        // preceding text starts a heading. A single # is never split (C#,
        // hashtags, URL fragments). Skip # inside a URL (`http://x#frag`).
        var headRe = /#{2,6}(?!#)/g;
        while ((m = headRe.exec(masked)) !== null) {
            var pos = m.index;
            var prev = pos > 0 ? masked.charAt(pos - 1) : '';
            if (!prev || /\s/.test(prev)) continue;
            var tokStart = pos;
            while (tokStart > 0 && !/\s/.test(masked.charAt(tokStart - 1))) tokStart--;
            if (masked.slice(tokStart, pos).indexOf('://') >= 0) continue;
            splitAt[pos] = '\n\n';
        }

        // `text---` glued thematic break. Only `-`: mid-line `***`/`___` are
        // emphasis. Skip when attached to a table cell (`|---`), to an arrow
        // (`--->`), or when followed by ordinary text (`a---b`, ranges);
        // a following `#` is fine (the heading split above breaks there too).
        var hrRe = /-{3,}/g;
        while ((m = hrRe.exec(masked)) !== null) {
            if (m.index === 0) continue;
            var pv = masked.charAt(m.index - 1);
            if (/\s/.test(pv) || pv === '|') continue;
            var nx = masked.charAt(m.index + m[0].length);
            if (nx && !/\s/.test(nx) && nx !== '#') continue;
            splitAt[m.index] = '\n\n';
        }

        // `text- item` glued list item (the `- `/`+ ` must be at a line start
        // for markdown). Never the same char twice (`--`, `C++`) and never a
        // `-`/`+` preceded by whitespace (already handled / normal prose).
        // Also never preceded by an ASCII alphanumeric (`JDK 7+ 中`, `V9+ 的
        // 变化` are prose, not glued lists) and never inside a `**strong**`
        // span, or the split would break the emphasis in half.
        var insideStrong = function (str, pos) {
            var count = 0, idx = 0;
            while ((idx = str.indexOf('**', idx)) !== -1 && idx < pos) { count++; idx += 2; }
            return count % 2 === 1;
        };
        var liRe = /([-+])(?=[ \t]\S)/g;
        var liCands = [];
        while ((m = liRe.exec(masked)) !== null) {
            if (m.index === 0) continue;
            var pvl = masked.charAt(m.index - 1);
            if (/\s/.test(pvl) || pvl === m[1]) continue;
            if (insideStrong(masked, m.index)) continue;
            liCands.push({ pos: m.index, strong: /[A-Za-z0-9]/.test(pvl) });
        }
        // An alphanumeric-prefixed marker (`7+ 中`) is prose unless the line
        // already shows a clean glued-list marker (`：- `, `:- `), which is
        // evidence the whole line is a flattened list.
        var listEvidence = liCands.some(function (c) { return !c.strong; });
        for (var lc = 0; lc < liCands.length; lc++) {
            if (liCands[lc].strong && !listEvidence) continue;
            splitAt[liCands[lc].pos] = '\n\n';
        }

        // Flattened table rows: only on lines carrying a `|---`/`---|`
        // delimiter signature. A glued table always flattens its delimiter
        // row onto the same line, while prose with a bare `||` (e.g. `a || b`)
        // never does and must stay untouched. Never on lines that are already
        // table rows (protected above). A `|` glued to another `|` is a row
        // boundary (single newline keeps the table intact); a `|` glued to
        // ordinary text is the start of the table after prose (blank line so
        // the table is not absorbed by the paragraph), but not when the text
        // before it already opens with `|` (then it is a cell boundary inside
        // an intact row). Never inside `|---|` cells.
        if (/\|-{2,}|-{2,}\|/.test(masked)) {
            var lineEnd = masked.replace(/\s+$/, '').length;
            // rowStart: position where the current table row begins (its
            // leading `|`, or 0 before any row break). A `|` glued to text is
            // a table start only when the CURRENT ROW does not open with `|`.
            var rowStart = 0;
            for (var q = 1; q < lineEnd; q++) {
                if (masked.charAt(q) !== '|') continue;
                var pvq = masked.charAt(q - 1);
                if (/\s/.test(pvq) || pvq === '-') continue;
                if (pvq === '|') {
                    splitAt[q] = '\n';
                    rowStart = q;
                } else if (!/^[ \t]*\|/.test(masked.substring(rowStart, q))) {
                    splitAt[q] = '\n\n';
                    rowStart = q;
                }
            }
        }

        var positions = Object.keys(splitAt);
        if (!positions.length) {
            out.push(line);
            continue;
        }

        positions.sort(function (a, b) { return a - b; });

        var segs = [];
        var last = 0;
        for (var k = 0; k < positions.length; k++) {
            var p = Number(positions[k]);
            if (p > last) segs.push(line.substring(last, p));
            last = p;
            segs.push(splitAt[p]);
        }
        segs.push(line.substring(last));
        out.push(segs.join(''));
    }
    return out.join('\n');
}

// The HTML5 parser treats <script>/<style>/<textarea>/<title> (RCDATA) and
// <iframe>/<xmp>/<noembed>/<noframes>/<noscript> as raw-text elements: when the
// RENDERED html is parsed into a DOM, everything after such an opening tag
// becomes the tag's text content until the matching closing tag or end of
// document (marked additionally treats <pre> the same way). marked passes a
// bare `<style>` mentioned in prose through un-escaped, so an unclosed one
// makes the DOM parser swallow the entire rest of the message, which
// stripDangerousHtml then deletes (the tags are in FORBID_TAGS / not on the
// allowlist) — the message visibly ends mid-sentence. Escape the dangling
// openers (no matching closer outside code) so they display literally and the
// remaining text renders normally. Fence- and inline-code-span-aware so real
// code samples are never touched.
function normalizeRawTextTags(text) {
    // Fast path: none of the swallow-prone tags appear at all, so nothing can
    // dangle and the per-line scan can be skipped entirely.
    if (!/<(script|pre|style|textarea|title|iframe|xmp|noembed|noframes|noscript)|<!--|-->/i.test(text)) return text;
    var RAW_TEXT_TAGS = ['script', 'pre', 'style', 'textarea', 'title', 'iframe', 'xmp', 'noembed', 'noframes', 'noscript'];
    var tagRe = new RegExp('</?(' + RAW_TEXT_TAGS.join('|') + ')(?=[\\s>/])', 'gi');
    var commentRe = /<!--|-->/g;
    var lines = text.split('\n');
    var inCode = false;
    var curFenceChar = '';
    var curFenceLen = 0;
    var events = [];

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];
        var trimmed = line.trim();
        var fenceMatch = /^(`{3,}|~{3,})/.exec(trimmed);
        if (fenceMatch) {
            var fence = fenceMatch[1];
            if (!inCode) {
                inCode = true;
                curFenceChar = fence.charAt(0);
                curFenceLen = fence.length;
            } else if (curFenceChar === fence.charAt(0) &&
                       fence.length >= curFenceLen &&
                       new RegExp('^' + fence.charAt(0) + '{' + curFenceLen + ',}\\s*$').test(trimmed)) {
                inCode = false;
            }
            continue;
        }
        if (inCode) continue;

        // Blank out inline code spans (backtick runs) with same-length spaces
        // so tags inside backticks never count and column offsets stay aligned.
        var masked = '';
        var inSpan = false;
        var spanLen = 0;
        for (var p = 0; p < line.length; p++) {
            if (line.charAt(p) !== '`') {
                masked += inSpan ? ' ' : line.charAt(p);
                continue;
            }
            var q = p;
            while (q < line.length && line.charAt(q) === '`') q++;
            var run = q - p;
            if (inSpan) {
                if (run === spanLen) {
                    inSpan = false;
                    spanLen = 0;
                }
            } else {
                inSpan = true;
                spanLen = run;
            }
            masked += new Array(run + 1).join(' ');
            p = q - 1;
        }

        var m;
        tagRe.lastIndex = 0;
        while ((m = tagRe.exec(masked)) !== null) {
            events.push({ line: i, col: m.index, close: m[0].charAt(1) === '/', tag: m[1].toLowerCase() });
        }
        commentRe.lastIndex = 0;
        while ((m = commentRe.exec(masked)) !== null) {
            events.push({ line: i, col: m.index, close: m[0] === '-->', tag: 'comment' });
        }
    }

    // Left-to-right matching (each closer closes the innermost opener);
    // whatever is left on the stack has no closing tag and would swallow the
    // rest of the document at DOM-parse time.
    var dangling = [];
    RAW_TEXT_TAGS.concat('comment').forEach(function (tag) {
        var stack = [];
        events.forEach(function (ev) {
            if (ev.tag !== tag) return;
            if (ev.close) stack.pop();
            else stack.push(ev);
        });
        dangling = dangling.concat(stack);
    });
    if (!dangling.length) return text;

    var escapeCols = {};
    dangling.forEach(function (ev) {
        (escapeCols[ev.line] = escapeCols[ev.line] || []).push(ev.col);
    });
    var out = lines.slice();
    Object.keys(escapeCols).forEach(function (key) {
        var idx = Number(key);
        var s = out[idx];
        var cols = escapeCols[idx].sort(function (a, b) { return b - a; });
        for (var c = 0; c < cols.length; c++) {
            s = s.substring(0, cols[c]) + '&lt;' + s.substring(cols[c] + 1);
        }
        out[idx] = s;
    });
    return out.join('\n');
}

// Blank out inline code spans (backtick runs) with same-length spaces so
// delimiters inside backticks never count and column offsets stay aligned.
function maskInlineCodeSpans(line) {
    var masked = '';
    var inSpan = false;
    var spanLen = 0;
    for (var p = 0; p < line.length; p++) {
        if (line.charAt(p) !== '`') {
            masked += inSpan ? ' ' : line.charAt(p);
            continue;
        }
        var q = p;
        while (q < line.length && line.charAt(q) === '`') q++;
        var run = q - p;
        if (inSpan) {
            if (run === spanLen) {
                inSpan = false;
                spanLen = 0;
            }
        } else {
            inSpan = true;
            spanLen = run;
        }
        masked += new Array(run + 1).join(' ');
        p = q - 1;
    }
    return masked;
}

// Effective column width of a line's leading whitespace, with tabs expanded to
// the next multiple of 4 (CommonMark tab stops). A single leading tab is thus
// a 4-column indent — an indented code block — not a 1-character indent, so a
// tab-indented "\[ ... \]" is code, never math.
function leadingIndentCols(line) {
    var col = 0;
    for (var k = 0; k < line.length; k++) {
        var ch = line.charAt(k);
        if (ch === ' ') {
            col++;
        } else if (ch === '\t') {
            col = Math.floor(col / 4) * 4 + 4;
        } else {
            break;
        }
    }
    return col;
}

// Models frequently wrap display math in \[ ... \] (the standard LaTeX form)
// or in bare [ ... ] delimiters. marked renders both as literal text (a bare
// [ ] pair is at best a broken link, \[ \] are escaped brackets), so the
// formula shows up as raw LaTeX. Rewrite those delimiters to $$ ... $$ so the
// mathBlock extension renders them with KaTeX.
//
// \[ ... \] is converted anywhere: in prose it can only mean math. Bare
// [ ... ] is converted only when it unambiguously is math: the [ opens a line
// (0-3 leading spaces), the ] closes a later line, the span contains a LaTeX
// command, and the opening line has no ] of its own — a single-line [ ... ]
// may be a citation, a task-list box or a link label, so it stays text.
// Fence- and inline-code-aware so LaTeX shown as code is never touched.
function normalizeMathDelimiters(text) {
    if (!/\\\[|\\\]|\\[A-Za-z]/.test(text)) return text;
    var lines = text.split('\n');
    var out = lines.slice();
    var inCode = false;
    var curFenceChar = '';
    var curFenceLen = 0;
    var inMath = false; // inside a \[ ... \] region whose opener was rewritten
    var changed = false;

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];

        // Inside an open \[ ... \] region every line is math content — even
        // one starting with a fence marker — so only the closing \] matters
        // and the fence state stays frozen until the region ends.
        if (inMath) {
            var cm = maskInlineCodeSpans(line).indexOf('\\]');
            if (cm >= 0) {
                out[i] = line.slice(0, cm) + '$$' + line.slice(cm + 2);
                changed = true;
                inMath = false;
            }
            continue;
        }

        var trimmed = line.trim();
        var fenceMatch = /^(`{3,}|~{3,})/.exec(trimmed);
        if (fenceMatch) {
            var fence = fenceMatch[1];
            if (!inCode) {
                inCode = true;
                curFenceChar = fence.charAt(0);
                curFenceLen = fence.length;
            } else if (curFenceChar === fence.charAt(0) &&
                       fence.length >= curFenceLen &&
                       new RegExp('^' + fence.charAt(0) + '{' + curFenceLen + ',}\\s*$').test(trimmed)) {
                inCode = false;
            }
            continue;
        }
        if (inCode) continue;

        var masked = maskInlineCodeSpans(line);

        // \[ ... \] — convert every pair on the line, left to right; a
        // trailing unclosed \[ opens a multi-line region. restMasked stays in
        // sync with rest because masking is length-preserving. Lines indented
        // 4+ columns (spaces or a leading tab) are indented code blocks, never
        // math.
        var om = masked.indexOf('\\[');
        if (om >= 0 && leadingIndentCols(line) < 4) {
            var rest = line, restMasked = masked, outStr = '', opened = false, openerIsFirst = true;
            for (;;) {
                var o = restMasked.indexOf('\\[');
                if (o < 0) {
                    outStr += rest;
                    break;
                }
                var c = restMasked.indexOf('\\]', o + 2);
                var prefix = rest.slice(0, o);
                // When the pair opens the line (only a <4-column indent before
                // it — the guard above already bounds this) the $$ is written
                // at column 0 so the block math tokenizer picks it up.
                var dropIndent = openerIsFirst && /^[ \t]*$/.test(prefix);
                if (c < 0) {
                    outStr += (dropIndent ? '' : prefix) + '$$' + rest.slice(o + 2);
                    opened = true;
                    break;
                }
                outStr += (dropIndent ? '' : prefix) + '$$' + rest.slice(o + 2, c) + '$$';
                rest = rest.slice(c + 2);
                restMasked = restMasked.slice(c + 2);
                openerIsFirst = false;
            }
            out[i] = outStr;
            changed = true;
            if (opened) inMath = true;
            continue;
        }

        // Bare [ ... ] — only when unambiguously math (see header comment).
        // The [ must open the line with a <4-column indent (a leading tab is
        // a 4-column indent = indented code, never math).
        var lead = /^[ \t]{0,3}\[/.exec(line);
        if (lead && leadingIndentCols(line) < 4 && line.slice(lead[0].length).indexOf(']') < 0) {
            var hasLaTeX = /\\[A-Za-z]/.test(masked.slice(lead[0].length));
            // Find the closing line: the next non-code line ending in ].
            var inCode2 = inCode, cfc2 = curFenceChar, cfl2 = curFenceLen;
            var closeLine = -1;
            for (var j = i + 1; j < lines.length; j++) {
                var lj = lines[j];
                var tj = lj.trim();
                var fj = /^(`{3,}|~{3,})/.exec(tj);
                if (fj) {
                    var f = fj[1];
                    if (!inCode2) {
                        inCode2 = true;
                        cfc2 = f.charAt(0);
                        cfl2 = f.length;
                    } else if (cfc2 === f.charAt(0) && f.length >= cfl2 &&
                               new RegExp('^' + f.charAt(0) + '{' + cfl2 + ',}\\s*$').test(tj)) {
                        inCode2 = false;
                    }
                    continue;
                }
                if (inCode2) continue;
                var mj = maskInlineCodeSpans(lj);
                if (!hasLaTeX && /\\[A-Za-z]/.test(mj)) hasLaTeX = true;
                // The ] closes the span at the end of the line or opens one
                // (both are rare in prose; the hasLaTeX gate keeps this safe).
                // A ] opening a tab-indented line is code, not a closer.
                var closeEnd = /\][ \t]*\r?$/.test(mj);
                var closeStart = leadingIndentCols(mj) < 4 && /^[ \t]{0,3}\]/.test(mj);
                if (closeEnd || closeStart) { closeLine = j; break; }
            }
            if (closeLine >= 0 && hasLaTeX) {
                // Consume the span: the main loop resumes after closeLine with
                // the fence state as the search left it.
                inCode = inCode2;
                curFenceChar = cfc2;
                curFenceLen = cfl2;
                out[i] = '$$' + out[i].slice(lead[0].length);
                var cl = out[closeLine];
                if (/^[ \t]{0,3}\]/.test(cl)) {
                    // ] opens the line: replace it (drop the indent) so the
                    // closing $$ starts the line; the rest of the line, if
                    // any, continues as normal text after the math block.
                    var sIdx = cl.indexOf(']');
                    out[closeLine] = '$$' + cl.slice(sIdx + 1);
                } else {
                    var cIdx = cl.lastIndexOf(']');
                    out[closeLine] = cl.slice(0, cIdx) + '$$' + cl.slice(cIdx + 1);
                }
                changed = true;
                i = closeLine;
            }
        }
    }
    return changed ? out.join('\n') : text;
}

// Math is rendered via marked extensions so LaTeX ($...$ / $$...$$) is consumed
// as a single token during parsing. This keeps the raw LaTeX intact: marked's
// inline escape rule would otherwise strip backslashes (e.g. \_ -> _) before
// katex sees them, producing parse errors and raw \text{...} output.
function decodeHtmlEntities(str) {
    return str.replace(/&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);/g, function(match, entity) {
        if (entity.charAt(0) === '#') {
            var code;
            if (entity.charAt(1) === 'x' || entity.charAt(1) === 'X') {
                code = parseInt(entity.substring(2), 16);
            } else {
                code = parseInt(entity.substring(1), 10);
            }
            return !isNaN(code) ? String.fromCharCode(code) : match;
        }
        var map = {
            amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ',
            le: '\u2264', ge: '\u2265', ne: '\u2260', times: '\u00d7',
            minus: '\u2212', plusmn: '\u00b1', infin: '\u221e',
            sum: '\u2211', prod: '\u220f', int: '\u222b', radic: '\u221a',
            part: '\u2202', nabla: '\u2207', sdot: '\u22c5', middot: '\u00b7',
            bull: '\u2022', hellip: '\u2026', prime: '\u2032', deg: '\u00b0',
            perp: '\u22a5', cong: '\u2245', asymp: '\u2248', prop: '\u221d',
            sim: '\u223c', equiv: '\u2261', rarr: '\u2192', larr: '\u2190',
            uarr: '\u2191', darr: '\u2193', harr: '\u2194'
        };
        return Object.prototype.hasOwnProperty.call(map, entity) ? map[entity] : match;
    });
}

function renderMath(latex, displayMode) {
    if (typeof katex === 'undefined') return null;
    try {
        // Model output may contain literal HTML entities; decode them so katex
        // receives valid LaTeX. Raw <, >, & are no longer escaped by marked
        // because math is consumed as a single token before escaping.
        return katex.renderToString(decodeHtmlEntities(latex), {
            displayMode: displayMode,
            throwOnError: false
        });
    } catch (e) {
        return null;
    }
}

function registerMathExtensions() {
    if (typeof marked === 'undefined') return;
    marked.use({
        extensions: [
            {
                name: 'mathBlock',
                level: 'block',
                // Only treat $$...$$ as block math when it starts a line, so
                // mid-line "text $$x$$ more" stays inside the paragraph.
                start(src) {
                    const idx = src.indexOf('$$');
                    if (idx < 0) return -1;
                    if (idx === 0 || src[idx - 1] !== '\n') return -1;
                    return idx;
                },
                tokenizer(src) {
                    const match = /^\$\$([\s\S]+?)\$\$/.exec(src);
                    if (match) {
                        return { type: 'mathBlock', raw: match[0], text: match[1].trim() };
                    }
                },
                renderer(token) {
                    const rendered = renderMath(token.text, true);
                    return rendered ? '<div class="katex-block">' + rendered + '</div>' : token.raw;
                }
            },
            {
                name: 'mathInline',
                level: 'inline',
                start(src) { return src.indexOf('$'); },
                tokenizer(src) {
                    const blockMatch = /^\$\$([\s\S]+?)\$\$/.exec(src);
                    if (blockMatch) {
                        return { type: 'mathInline', raw: blockMatch[0], text: blockMatch[1].trim(), displayMode: true };
                    }
                    // Inline math: scan for the closing $. A $ immediately
                    // followed by a backslash opens an inner math-mode pair
                    // (e.g. \text{... $\rightarrow$ ...}); its own closing $
                    // must be skipped, not treated as the outer delimiter.
                    if (src.charAt(0) !== '$' || src.charAt(1) === '$') return;
                    let i = 1;
                    while (i < src.length) {
                        const c = src.charAt(i);
                        if (c === '\\') { i += 2; continue; }
                        if (c === '$') {
                            if (src.charAt(i + 1) === '\\') {
                                let j = i + 2;
                                while (j < src.length && !(src.charAt(j) === '$' && src.charAt(j - 1) !== '\\')) j++;
                                if (j >= src.length) break;
                                i = j + 1;
                                continue;
                            }
                            return { type: 'mathInline', raw: src.slice(0, i + 1), text: src.slice(1, i).trim(), displayMode: false };
                        }
                        i++;
                    }
                },
                renderer(token) {
                    const rendered = renderMath(token.text, token.displayMode);
                    if (!rendered) return token.raw;
                    return token.displayMode ? '<div class="katex-block">' + rendered + '</div>' : rendered;
                }
            }
        ]
    });
}

registerMathExtensions();

// marked implements CommonMark's strict emphasis rules, which reject two very
// common LLM boundary patterns:
//   1. closing run preceded by punctuation and directly followed by text
//      (`**标题：**内容`): the closing run is left-flanking only.
//   2. opening run preceded by a word character and immediately followed by
//      punctuation (`而是**“xxx”**：`): the opening run is not left-flanking.
// In both cases marked leaves the `**`/`*` as literal characters. Add a lax
// fallback so such model output renders as bold/italic as expected.
function registerEmphasisFallback() {
    if (typeof marked === 'undefined') return;
    marked.use({
        tokenizer: {
            emStrong(src, maskedSrc, prevChar) {
                const result = marked.Tokenizer.prototype.emStrong.call(this, src, maskedSrc, prevChar);
                if (result) return result;

                // Never split a longer `*`/`_` delimiter run left unmatched by
                // the strict algorithm (e.g. `**foo **bar**` must keep its
                // original `**foo <strong>bar</strong>` interpretation).
                if ((src[0] === '*' || src[0] === '_') && prevChar === src[0]) return;

                // Keep `_` (and `__`) literal between word characters: snake_case.
                if (src[0] === '_') {
                    if (src[1] === '_') {
                        if (prevChar && prevChar.match(/[\p{L}\p{N}]/u)) return;
                    } else if (prevChar && prevChar.match(/[\p{L}\p{N}]/u)) {
                        return;
                    }
                }

                // Closing delimiter preceded by punctuation/symbol and followed
                // by a letter/digit (pattern 1).
                let match = /^\*{2}(?=\S)([\s\S]*?[\p{P}\p{S}])\*{2}(?=[^\s\p{P}\p{S}])/u.exec(src)
                    || /^\*(?!\*)(?=\S)([\s\S]*?[\p{P}\p{S}])\*(?!\*)(?=[^\s\p{P}\p{S}])/u.exec(src)
                    || /^_{2}(?=\S)([\s\S]*?[\p{P}\p{S}])_{2}(?!_)(?=[^\s\p{P}\p{S}])/u.exec(src)
                    || /^_(?!_)(?=\S)([\s\S]*?[\p{P}\p{S}])_(?!_)(?=[^\s\p{P}\p{S}])/u.exec(src);

                // Opening delimiter preceded by a letter/digit and immediately
                // followed by punctuation (pattern 2, e.g. 而是**“xxx”**：).
                // The inner text must start with punctuation/symbol, which is
                // exactly the condition that makes the opening run non-
                // left-flanking in strict CommonMark.
                if (!match && prevChar && prevChar.match(/[\p{L}\p{N}]/u)) {
                    match = /^\*{2}([\p{P}\p{S}][\s\S]*?)\*{2}(?!\*)/u.exec(src)
                        || /^\*(?!\*)([\p{P}\p{S}][\s\S]*?)\*(?!\*)/u.exec(src);
                }

                if (match) {
                    const strong = match[0][1] === match[0][0];
                    return {
                        type: strong ? 'strong' : 'em',
                        raw: match[0],
                        text: match[1],
                        tokens: this.lexer.inlineTokens(match[1])
                    };
                }
                return;
            }
        }
    });
}

registerEmphasisFallback();

// Sanitize rendered model output. Model content is rendered without escaping
// (see safeMarkedParse) so raw HTML shows as produced, which means untrusted
// HTML could otherwise run in the chat page: a <style> block overriding the
// background/theme, <script> or inline event handlers executing JS, and
// <iframe>/<object>/<link>/<meta> loading foreign content or rewriting URLs.
// DOMPurify (allowlist-based, parses as a real DOM) removes all of that
// robustly — including unclosed tags left by a truncated stream — while
// keeping normal display HTML. The `style` attribute is kept so the KaTeX
// math markup (emitted during marked.parser) keeps its layout. Code-fence
// content is HTML-escaped by marked (&lt;style&gt;) and is untouched, so it
// still displays and can be copied/saved verbatim.
// Models sometimes emit SVG gradients with invalid coordinate values
// (e.g. x1="" or x1="50% 50%"); the browser logs
// "attribute x1: Expected length" when such markup is parsed. Replace
// invalid gradient coordinates with valid defaults to keep the console clean.
var SVG_GRADIENT_ATTR_DEFAULTS = { x1: '0%', y1: '0%', x2: '100%', y2: '0%', cx: '50%', cy: '50%', r: '50%', fx: '50%', fy: '50%' };

function isSvgLength(value) {
    return /^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?%?$/.test(value);
}

function repairSvgGradientAttrs(html) {
    if (!html || html.indexOf('Gradient') === -1) return html;
    var tpl = document.createElement('template');
    tpl.innerHTML = html;
    var gradients = tpl.content.querySelectorAll('linearGradient, radialGradient');
    if (!gradients.length) return html;
    var repaired = false;
    gradients.forEach(function (g) {
        Object.keys(SVG_GRADIENT_ATTR_DEFAULTS).forEach(function (attr) {
            if (g.hasAttribute(attr) && !isSvgLength((g.getAttribute(attr) || '').trim())) {
                g.setAttribute(attr, SVG_GRADIENT_ATTR_DEFAULTS[attr]);
                repaired = true;
            }
        });
    });
    return repaired ? tpl.innerHTML : html;
}

function stripDangerousHtml(html) {
    if (!html) return '';
    var purify = typeof window !== 'undefined' ? window.DOMPurify : null;
    if (purify && typeof purify.sanitize === 'function') {
        // DOMPurify's default allowlist does NOT remove <style>/<link>/<meta>,
        // so the dangerous document-level tags are forbidden explicitly. The
        // `style` ATTRIBUTE is kept (ADD_ATTR) so KaTeX math keeps its layout;
        // inline event handlers and javascript:/data: URLs are stripped by
        // DOMPurify automatically.
        return repairSvgGradientAttrs(purify.sanitize(html, {
            ADD_ATTR: ['style'],
            FORBID_TAGS: [
                'style', 'script', 'link', 'meta', 'base',
                'iframe', 'frame', 'frameset', 'object', 'embed',
                'noscript', 'noembed', 'applet', 'portal'
            ]
        }));
    }
    return repairSvgGradientAttrs(stripDangerousHtmlFallback(html));
}

// Regex fallback used only if DOMPurify failed to load. Strips document-level /
// executable tags (tolerating an unclosed closing tag by also ending at
// end-of-string, so a truncated stream can't leak a live <style>), inline
// event handlers, and javascript:/vbscript:/data: URLs.
function stripDangerousHtmlFallback(html) {
    if (!html) return '';
    return html
        .replace(/<style\b[^>]*>[\s\S]*?(<\/style\s*>|$)/gi, '')
        .replace(/<script\b[^>]*>[\s\S]*?(<\/script\s*>|$)/gi, '')
        .replace(/<iframe\b[^>]*>[\s\S]*?(<\/iframe\s*>|$)/gi, '')
        .replace(/<object\b[^>]*>[\s\S]*?(<\/object\s*>|$)/gi, '')
        .replace(/<noscript\b[^>]*>[\s\S]*?(<\/noscript\s*>|$)/gi, '')
        .replace(/<(link|meta|base|embed)\b[^>]*\/?>/gi, '')
        .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
        .replace(/\b(href|src)\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, function(m, name, val) {
            var inner = val.replace(/^["']|["']$/g, '').trim().toLowerCase();
            return /^(javascript|vbscript):/.test(inner) ? name + '="#"' : m;
        });
}

// Throttle parse-failure warnings: during streaming the same failing prefix
// is re-parsed every tick, so without this the console would spam per tick.
let lastParseFallbackWarnAt = 0;

function safeMarkedParse(text, isFinal) {
    if (!text) return '';
    try {
        const normalized = normalizeRawTextTags(normalizeHeadings(normalizeMathDelimiters(normalizeCodeFences(normalizeMidLineBlocks(text)))));
        const tokens = marked.lexer(normalized);
        // Assistant output is rendered without character escaping: model
        // output (including code and raw HTML) must be shown exactly as
        // produced. stripDangerousHtml then removes any un-fenced
        // document-level tags so the page can't be hijacked even if the model
        // emits raw HTML/CSS/JS.
        return stripDangerousHtml(marked.parser(tokens));
    } catch (err) {
        // A parse failure must never freeze the UI: fall back to escaped
        // plain text so streaming keeps appending (and history/recovery
        // rendering keeps going). Once the stream completes the final render
        // may parse cleanly again.
        const now = Date.now();
        if (now - lastParseFallbackWarnAt > 2000) {
            lastParseFallbackWarnAt = now;
            console.warn('markdown parse failed, using plain text fallback', err);
        }
        const plainDiv = document.createElement('div');
        plainDiv.textContent = text;
        return plainDiv.innerHTML;
    }
}

// Streaming parse: render normally but without code block wrapping/highlighting
function safeMarkedParseStreaming(text) {
    return safeMarkedParse(text, false);
}

// Incremental append: only append new content to container, skip unchanged prefix
function incrementalAppendContent(container, newHtml) {
    if (!container) return;
    var prevHtml = container._streamPrevHtml || '';
    if (!prevHtml) {
        container.innerHTML = newHtml;
        container._streamPrevHtml = newHtml;
        return;
    }
    if (prevHtml === newHtml) return;
    // If new HTML starts with old HTML, just append the difference
    if (newHtml.startsWith(prevHtml)) {
        var diff = newHtml.substring(prevHtml.length);
        var wrapper = document.createElement('div');
        wrapper.innerHTML = diff;
        while (wrapper.firstChild) {
            container.appendChild(wrapper.firstChild);
        }
        container._streamPrevHtml = newHtml;
        return;
    }
    // Structural change (e.g., code block started/ended): full re-render
    container.innerHTML = newHtml;
    container._streamPrevHtml = newHtml;
}

// Rendering throttle
let streamRenderPending = false;
let lastStreamRenderTime = 0;
const STREAM_RENDER_INTERVAL = 150;

function scheduleStreamRender(renderFn) {
    const now = performance.now();
    if (now - lastStreamRenderTime < STREAM_RENDER_INTERVAL) {
        if (!streamRenderPending) {
            streamRenderPending = true;
            requestAnimationFrame(() => {
                streamRenderPending = false;
                lastStreamRenderTime = performance.now();
                renderFn();
            });
        }
    } else {
        lastStreamRenderTime = now;
        renderFn();
    }
}

// Global state for streaming optimization
let streamFastMode = false;
let streamRawContent = '';
let streamChunkCount = 0;
let streamThinkingDone = false;
let streamThinkingTimeMs = null;
// 当前这轮对话使用的模型名称(stream_start 时记录, 用于消息标签与 allMessages 持久化)
let currentStreamModelName = null;

// Open WebUI 风格: assistant 消息最前面的模型名称标签; modelName 为空(历史数据)时不渲染
function createModelNameLabel(modelName) {
    if (!modelName || !modelName.trim()) return null;
    const label = document.createElement('div');
    label.className = 'message-model-name';
    const icon = document.createElement('i');
    icon.className = 'bi bi-cpu';
    const span = document.createElement('span');
    span.textContent = modelName;
    label.appendChild(icon);
    label.appendChild(span);
    return label;
}

// 思考耗时实时展示：streamThinkingTimeMs 由后端 stats.thinkingTimeMs 驱动，
// 每个 thinking_chunk / content_chunk 更新一次，渲染时刷新到 .thinking-time
function renderThinkingTimeSpan(thinkingBlock) {
    if (!thinkingBlock || streamThinkingTimeMs == null) return;
    const headerRight = thinkingBlock.querySelector('.thinking-header-right');
    if (!headerRight) return;
    let timeSpan = headerRight.querySelector('.thinking-time');
    if (!timeSpan) {
        timeSpan = document.createElement('span');
        timeSpan.className = 'thinking-time';
        headerRight.appendChild(timeSpan);
    }
    timeSpan.textContent = formatDuration(streamThinkingTimeMs);
}

// Throttled rendering for streaming
let streamRenderTimer = null;
function scheduleStreamingUpdate() {
    if (streamRenderTimer) return;
    streamRenderTimer = setTimeout(() => {
        streamRenderTimer = null;
        if (!isGenerating) return;

        const bodyDiv = currentMessageElement?.querySelector('.message-body');
        if (!bodyDiv) return;

        const hasThinking = currentThinkingContent?.trim();
        const hasContent = currentAssistantMessage?.trim();

        // Remove generating badge when thinking or content arrives
        if (hasThinking || hasContent) {
            const goingBadge = bodyDiv.querySelector('.message-status.going');
            if (goingBadge) goingBadge.remove();
        }

        // Render thinking content with spinner
        if (hasThinking) {
            let thinkingBlock = bodyDiv.querySelector('.thinking-block');
            if (!thinkingBlock) {
                thinkingBlock = document.createElement('div');
                thinkingBlock.className = 'thinking-block collapsed';
                thinkingBlock.innerHTML = `
                    <div class="thinking-header" onclick="toggleThinking(this)">
                        <div class="thinking-header-left"><span class="thinking-icon">${t('chat.thinking_process')}</span></div>
                        <div class="thinking-header-right"><span class="thinking-spinner"></span></div>
                    </div>
                    <div class="thinking-content"></div>
                `;
                // 模型名称标签始终保持在消息最前面, 思考块插到它后面
                const modelLabel = bodyDiv.querySelector('.message-model-name');
                if (modelLabel) {
                    modelLabel.insertAdjacentElement('afterend', thinkingBlock);
                } else {
                    bodyDiv.insertBefore(thinkingBlock, bodyDiv.firstChild);
                }
                attachThinkingFollowGuard(thinkingBlock.querySelector('.thinking-content'));
            }
            // 思考阶段实时刷新耗时（后端 stats.thinkingTimeMs）
            renderThinkingTimeSpan(thinkingBlock);
            const thinkingContent = thinkingBlock.querySelector('.thinking-content');
            if (thinkingContent) {
                // While the thinking phase is still streaming, an expanded
                // thinking block follows its newest content with the scrollbar
                // hidden and all user scrolling locked (like the collapsed
                // tail window of a streaming code block in the message body):
                // the latest line stays visible at the bottom and can't be
                // scrolled away mid-stream. Once the thinking phase ends
                // (first content arrives) the panel becomes static: scrollbar
                // restored, free scrolling so the user can re-read it.
                const followingStream = !thinkingBlock.classList.contains('collapsed') && !streamThinkingDone;
                if (followingStream) {
                    thinkingContent.classList.add('streaming-follow');
                } else {
                    thinkingContent.classList.remove('streaming-follow');
                }
                // Re-rendering innerHTML resets/clamps the scroll position, so
                // capture the user's view before the swap to restore it after.
                const wasAtBottom = thinkingContent.scrollHeight - thinkingContent.scrollTop - thinkingContent.clientHeight < 40;
                const prevScrollTop = thinkingContent.scrollTop;
                thinkingContent.innerHTML = safeMarkedParse(currentThinkingContent, false);
                thinkingContent.querySelectorAll('pre').forEach(function(pre) {
                    wrapStreamingCodeBlock(pre);
                    updateStreamingLineCount(pre);
                    if (!pre.closest('.code-block-wrapper.code-expanded') && (followingStream || shouldAutoScroll())) {
                        pre.scrollTop = pre.scrollHeight;
                    }
                });
                if (!thinkingBlock.classList.contains('collapsed')) {
                    if (followingStream || wasAtBottom) {
                        // Streaming phase: always the newest line. Static phase:
                        // interleaved backends can still grow the thinking text
                        // after content started, so a user sitting at the
                        // bottom stays on the newest line...
                        pinThinkingContentToBottom(thinkingContent, thinkingBlock);
                    } else {
                        // ...while a user who scrolled up to re-read keeps
                        // their position instead of being yanked to the bottom.
                        thinkingContent.scrollTop = prevScrollTop;
                    }
                }
            }
        }

        // Content arrived: thinking phase is done
        if (hasContent) {
            if (!streamThinkingDone) {
                streamThinkingDone = true;
                const thinkingBlock = bodyDiv.querySelector('.thinking-block');
                if (thinkingBlock) {
                    removeThinkingSpinner(thinkingBlock);
                    // 定格最终思考耗时（后端 stats.thinkingTimeMs）
                    renderThinkingTimeSpan(thinkingBlock);
                    thinkingBlock.classList.add('collapsed');
                }
            }
            let contentDiv = bodyDiv.querySelector('.message-content');
            if (!contentDiv) {
                contentDiv = document.createElement('div');
                contentDiv.className = 'message-content streaming';
                bodyDiv.appendChild(contentDiv);
            }
             incrementalAppendContent(contentDiv, safeMarkedParseStreaming(currentAssistantMessage));
                // Wrap streaming code blocks and scroll them to bottom. A
                // collapsed streaming block is an overflow:hidden tail window
                // (like the thinking panel): it always follows its newest line,
                // independent of the main-container auto-follow state. The main
                // state can get disabled transiently (a wheel over the block),
                // and since the block is re-rendered whole every tick a stale
                // flag would leave the fresh <pre> at scrollTop 0 — showing the
                // first lines instead of the latest stream. Only an expanded
                // block (user opted into free scrolling) is left alone.
                contentDiv.querySelectorAll('pre').forEach(function(pre) {
                    wrapStreamingCodeBlock(pre);
                    updateStreamingLineCount(pre);
                    if (!pre.closest('.code-block-wrapper.code-expanded')) {
                        pre.scrollTop = pre.scrollHeight;
                    }
                });
        }

        // Auto scroll main container
        if (shouldAutoScroll()) {
            scrollToBottom();
        }
    }, STREAM_RENDER_INTERVAL);
}

// Whether the user expanded a streaming code block; preserved across ticks
// because incrementalAppendContent re-renders the whole container each time
// the code content grows.
let streamCodeExpanded = false;

// While a code block is still being streamed, wrap it in a highlighted
// container with a header showing the language, a live line count and an
// expand/collapse toggle (mirrors the finalized code-block-wrapper UI).
function wrapStreamingCodeBlock(pre) {
    if (pre.closest('.code-block-wrapper')) return;
    const codeEl = pre.querySelector('code');
    if (!codeEl) return;

    const langClass = codeEl.className.split(' ').find(c => c.startsWith('lang-') || c.startsWith('language-'));
    const lang = langClass ? langClass.replace('lang-', '').replace('language-', '') : '';
    const lines = countCodeLines(codeEl.textContent);

    const isLong = lines > 10;
    const wrapper = document.createElement('div');
    wrapper.className = 'code-block-wrapper streaming-code-block';
    wrapper.dataset.long = isLong ? 'true' : 'false';
    if (streamCodeExpanded) {
        wrapper.classList.add('code-expanded');
    } else if (isLong) {
        wrapper.classList.add('code-collapsed');
    }

    const header = document.createElement('div');
    header.className = 'code-header';
    header.innerHTML = `
        <span class="code-lang">${lang || 'code'}</span>
        <div class="code-actions">
            <span class="streaming-line-count">${t('chat.streaming_code_lines', { lines: lines })}</span>
            ${isLong ? `<button class="code-btn expand-toggle" onpointerdown="toggleCodeBlock(this)" title="${t('chat.expand_collapse')}">
                ${streamCodeExpanded ? '<i class="bi bi-arrows-collapse"></i> ' + t('chat.collapse') : '<i class="bi bi-arrows-expand"></i> ' + t('chat.expand')}
            </button>` : ''}
        </div>
    `;

    pre.parentNode.insertBefore(wrapper, pre);
    wrapper.appendChild(header);
    wrapper.appendChild(pre);
}

// Update the live line count shown in a streaming code block header.
function updateStreamingLineCount(pre) {
    const wrapper = pre.closest('.code-block-wrapper.streaming-code-block');
    if (!wrapper) return;
    const codeEl = pre.querySelector('code');
    if (!codeEl) return;
    const lines = countCodeLines(codeEl.textContent);
    const badge = wrapper.querySelector('.streaming-line-count');
    if (badge) badge.textContent = t('chat.streaming_code_lines', { lines: lines });
}

// Capture which streaming code blocks the user expanded within a container,
// by document order index, so the state survives the final re-render. The
// body (.message-content) and the thinking panel (.thinking-content) are
// separate index spaces, so each must be captured from its own container — a
// global capture would shift the body's indexes by the number of thinking
// wrappers and drop the thinking panel's state entirely.
function captureExpandedStreamingWrappers(container) {
    const expandedIndexes = [];
    container.querySelectorAll('.code-block-wrapper.streaming-code-block').forEach(function(wrapper, i) {
        if (wrapper.classList.contains('code-expanded') || !wrapper.classList.contains('code-collapsed')) {
            expandedIndexes.push(i);
        }
    });
    return expandedIndexes;
}

// Restore expansion on the finalized wrappers in a container that sit at the
// same document positions as the captured streaming wrappers.
function restoreExpandedWrappers(container, expandedIndexes) {
    if (!expandedIndexes || !expandedIndexes.length) return;
    const wrappers = container.querySelectorAll('.code-block-wrapper');
    expandedIndexes.forEach(function(idx) {
        const wrapper = wrappers[idx];
        if (wrapper && wrapper.dataset.long === 'true') {
            wrapper.classList.add('code-expanded');
            wrapper.classList.remove('code-collapsed');
            const expandBtn = wrapper.querySelector('.expand-toggle');
            if (expandBtn) {
                expandBtn.innerHTML = '<i class="bi bi-arrows-collapse"></i> ' + t('chat.collapse');
            }
        }
    });
}

function handleMessage(data, chatId) {
    if (data.type === 'stream_end' || data.type === 'error') {
        const targetChatId = data.chatId || currentChatSessionId;
        if (targetChatId) {
            const session = sessions.find(s => s.chatId === targetChatId);
            if (session) session.isGenerating = false;
            const pinnedSession = pinnedSessions.find(s => s.chatId === targetChatId);
            if (pinnedSession) pinnedSession.isGenerating = false;
            requestAnimationFrame(() => {
                renderSessionList();
                renderPinnedList();
            });
        }
    }
    if (data.type === 'session_recovery') {
        const targetChatId = data.chatId || currentChatSessionId;
        if (targetChatId) {
            const session = sessions.find(s => s.chatId === targetChatId);
            if (session) session.isGenerating = true;
            const pinnedSession = pinnedSessions.find(s => s.chatId === targetChatId);
            if (pinnedSession) pinnedSession.isGenerating = true;
            renderSessionList();
            renderPinnedList();
        }
    }
    if (data.chatId && data.chatId !== currentChatSessionId) {
        return;
    }
    
    switch (data.type) {
        case 'stream_start':
            if (!isRecovering && !currentMessageElement) {
                const existingAsstEls = document.querySelectorAll('#messagesContainer .message.assistant');
                let foundExisting = null;
                if (existingAsstEls.length > 0) {
                    const lastEl = existingAsstEls[existingAsstEls.length - 1];
                    const goingBadge = lastEl.querySelector('.message-status.going');
                    if (goingBadge) {
                        foundExisting = lastEl;
                    }
                }

                currentStreamModelName = selectedChatModel ? selectedChatModel.modelName : null;
                if (foundExisting) {
                    currentMessageElement = foundExisting;
                    currentAssistantMessage = '';
                    currentThinkingContent = '';
                    streamThinkingDone = false;
                    streamThinkingTimeMs = null;
                } else {
                    currentAssistantMessage = '';
                    currentThinkingContent = '';
                    streamThinkingDone = false;
                    streamThinkingTimeMs = null;
                    currentMessageElement = createAssistantMessage('', '', null, false, 'INIT_BEFORE', null, currentStreamModelName);
                    updateSessionMessageCount(currentChatSessionId, 1);
                    updateSessionTitleOnFirstMessage(currentChatSessionId, lastUserMessage);
                }
            } else if (isRecovering) {
                isRecovering = false;
            }
            isGenerating = true;
            const genSession = sessions.find(s => s.chatId === currentChatSessionId);
            if (genSession) genSession.isGenerating = true;
            const genPinnedSession = pinnedSessions.find(s => s.chatId === currentChatSessionId);
            if (genPinnedSession) genPinnedSession.isGenerating = true;
            // 新一轮生成开始: 保留上一轮的 decode/generate 速度与上下文占用显示,
            // 本轮结束后(stream_end/error 的 displayStats)才更新, 避免生成过程中消失
            updateContextUsage();
            renderSessionList();
            renderPinnedList();
            updateSendButtonState();
            break;
        case 'thinking_chunk':
            isGenerating = true;
            {
                const genSession2 = sessions.find(s => s.chatId === currentChatSessionId);
                if (genSession2) genSession2.isGenerating = true;
                updateSendButtonState();
            }
            if (data.thinking) {
                currentThinkingContent += data.thinking;
                // 后端实时下发的思考耗时，渲染时刷新显示
                if (data.thinkingTimeMs != null) {
                    streamThinkingTimeMs = data.thinkingTimeMs;
                }
            }

            if (currentThinkingContent?.trim() && currentMessageElement) {
                streamChunkCount++;
                scheduleStreamingUpdate();
                if (shouldAutoScroll() && isGenerating) scrollToBottom();
            }
            break;
        case 'content_chunk':
            isGenerating = true;
            {
                const genSession2 = sessions.find(s => s.chatId === currentChatSessionId);
                if (genSession2) genSession2.isGenerating = true;
                updateSendButtonState();
            }
            if (data.content) {
                currentAssistantMessage += data.content;
            }
            if (data.thinkingTimeMs != null) {
                streamThinkingTimeMs = data.thinkingTimeMs;
            }

           if (currentAssistantMessage?.trim() && currentMessageElement) {
                streamChunkCount++;
                scheduleStreamingUpdate();
                if (shouldAutoScroll() && isGenerating) scrollToBottom();
            }
            break;
        case 'stream_end':
            streamChunkCount = 0;
            if (streamRenderTimer) {
                clearTimeout(streamRenderTimer);
                streamRenderTimer = null;
            }
            const lastMsgStatus = data.lastAssistantMessage ? data.lastAssistantMessage.status : null;
            const endStats = data.lastAssistantMessage ? data.lastAssistantMessage.stats : null;
            const lastMsgTimestamp = data.lastAssistantMessage ? data.lastAssistantMessage.timestamp : null;
            const endModelName = (data.lastAssistantMessage && data.lastAssistantMessage.modelName) || currentStreamModelName || null;

            // Push streamed message into allMessages so it survives re-render
            if (currentAssistantMessage?.trim() || currentThinkingContent?.trim()) {
                const streamedMsg = {
                    role: 'assistant',
                    content: currentAssistantMessage || '',
                    thinkingContent: currentThinkingContent || '',
                    modelName: endModelName,
                    stats: endStats,
                    status: lastMsgStatus,
                    timestamp: lastMsgTimestamp
                };
                allMessages.push(streamedMsg);
            }
            updateSessionMessageCount(currentChatSessionId, 1);
            let wasAtBottom = false;
            if (currentMessageElement) {
                const bodyDiv = currentMessageElement.querySelector('.message-body');
                // Remove generating badge
                const goingBadge = bodyDiv.querySelector('.message-status.going');
                if (goingBadge) goingBadge.remove();

                // Remove thinking spinner (edge case: openapi error, no thinking/content arrived)
                const thinkingBlock = bodyDiv.querySelector('.thinking-block');
                if (thinkingBlock) {
                    removeThinkingSpinner(thinkingBlock);
                }

                // Full render with markdown parsing and syntax highlighting
                wasAtBottom = shouldAutoScroll();
                const contentDiv = bodyDiv?.querySelector('.message-content');
                // Capture each container's expansion state before the re-render
                // destroys its streaming wrappers (body and thinking are
                // separate index spaces).
                const bodyExpanded = contentDiv ? captureExpandedStreamingWrappers(contentDiv) : [];
                const thinkingExpanded = thinkingBlock ? captureExpandedStreamingWrappers(thinkingBlock) : [];
                if (contentDiv && currentAssistantMessage?.trim()) {
                    contentDiv.classList.remove('streaming');
                    contentDiv.innerHTML = safeMarkedParse(currentAssistantMessage, true);
                }

                if (thinkingBlock) {
                    // Streaming is over: restore normal scrolling no matter how
                    // the turn ended — a leftover streaming-follow would hide
                    // the scrollbar and freeze the wheel on an expanded panel.
                    const thinkingContent = thinkingBlock.querySelector('.thinking-content');
                    if (thinkingContent) {
                        thinkingContent.classList.remove('streaming-follow');
                        if (currentThinkingContent?.trim()) {
                            thinkingContent.innerHTML = safeMarkedParse(currentThinkingContent, true);
                        }
                    }
                    if (currentThinkingContent?.trim()) {
                        if (endStats) {
                            finalizeThinkingBlock(thinkingBlock, endStats, lastMsgTimestamp);
                        } else {
                            thinkingBlock.classList.add('collapsed');
                        }
                    }
                }

                if (lastMsgStatus === 'STOP') {
                    showStoppedBadge(bodyDiv, currentAssistantMessage, currentThinkingContent);
                }
                // The final render and any un-collapse above can leave an
                // expanded panel off its newest line; re-pin once the expand
                // transition has settled.
                pinExpandedThinkingBlock(bodyDiv);
                processCodeBlocks(currentMessageElement, null, true);
                // The thinking content was just re-rendered raw above (or kept
                // its streaming wrappers); give it the same finalized treatment
                // as the body so its code blocks get headers, highlighting and
                // line numbers exactly like a history-loaded message.
                if (thinkingBlock) processThinkingCodeBlocks(thinkingBlock, true);
                // Restore each container's expansion state onto its own
                // finalized wrappers (the body's indexes are no longer shifted
                // by thinking wrappers, and the thinking panel's state is kept).
                if (contentDiv) restoreExpandedWrappers(contentDiv, bodyExpanded);
                if (thinkingBlock) restoreExpandedWrappers(thinkingBlock, thinkingExpanded);
                isGenerating = false;
                if (endStats) {
                    renderStats(currentMessageElement, endStats);
                }
                // 即使本轮没有 stats 也要刷新: 清空上轮残留的速度显示
                displayStats(endStats || {});
            }
            document.getElementById('btnStop').style.display = 'none';
            document.querySelector('.btn-send').style.display = 'flex';
            if (wasAtBottom) scrollToBottom();
            currentThinkingContent = null;
            currentAssistantMessage = '';
            streamThinkingDone = false;
            streamThinkingTimeMs = null;
            streamCodeExpanded = false;
            currentMessageElement = null;
            const endSession = sessions.find(s => s.chatId === currentChatSessionId);
            if (endSession) endSession.isGenerating = false;
            const endPinnedSession = pinnedSessions.find(s => s.chatId === currentChatSessionId);
            if (endPinnedSession) endPinnedSession.isGenerating = false;
            renderSessionList();
            renderPinnedList();
            updateSendButtonState();
            loadChatModels();
            break;
        case 'error':
            showAlert((data.msgKey && hasMsgKey(data.msgKey)) ? t(data.msgKey, data.params) : (data.message || t('chat.error')), 'error');
            const errStats = data.stats || (data.lastAssistantMessage ? data.lastAssistantMessage.stats : null);
            const errTimestamp = data.lastAssistantMessage ? data.lastAssistantMessage.timestamp : null;
            const errModelName = (data.lastAssistantMessage && data.lastAssistantMessage.modelName) || currentStreamModelName || null;

            // Push streamed message into allMessages so it survives re-render
            if (currentAssistantMessage?.trim() || currentThinkingContent?.trim()) {
                const streamedMsg = {
                    role: 'assistant',
                    content: currentAssistantMessage || '',
                    thinkingContent: currentThinkingContent || '',
                    modelName: errModelName,
                    stats: errStats,
                    status: 'ERROR',
                    timestamp: errTimestamp
                };
                allMessages.push(streamedMsg);
            }
            if (currentMessageElement) {
                const bodyDiv = currentMessageElement.querySelector('.message-body');
                const goingBadge = bodyDiv.querySelector('.message-status.going');
                if (goingBadge) {
                    goingBadge.remove();
                }
                const thinkingBlock = bodyDiv.querySelector('.thinking-block');
                if (thinkingBlock) {
                    if (errStats) {
                        finalizeThinkingBlock(thinkingBlock, errStats, errTimestamp);
                    } else {
                        removeThinkingSpinner(thinkingBlock);
                        thinkingBlock.classList.add('collapsed');
                    }
                    // Streaming is over: restore normal scrolling
                    const thinkingContent = thinkingBlock.querySelector('.thinking-content');
                    if (thinkingContent) thinkingContent.classList.remove('streaming-follow');
                }
                if (errStats) {
                    renderStats(currentMessageElement, errStats);
                }
                // 即使本轮没有 stats 也要刷新: 清空上轮残留的速度显示
                displayStats(errStats || {});
                // Capture each container's expansion state (body and thinking
                // are separate index spaces) before finalizing its wrappers.
                const contentDiv = bodyDiv?.querySelector('.message-content');
                const bodyExpanded = contentDiv ? captureExpandedStreamingWrappers(contentDiv) : [];
                const thinkingExpanded = thinkingBlock ? captureExpandedStreamingWrappers(thinkingBlock) : [];
                processCodeBlocks(currentMessageElement, null, true);
                // The thinking panel still holds its streaming wrappers here;
                // finalize them too so its code blocks match the body's.
                if (thinkingBlock) processThinkingCodeBlocks(thinkingBlock, true);
                if (contentDiv) restoreExpandedWrappers(contentDiv, bodyExpanded);
                if (thinkingBlock) restoreExpandedWrappers(thinkingBlock, thinkingExpanded);
            }
            document.getElementById('btnStop').style.display = 'none';
            document.querySelector('.btn-send').style.display = 'flex';
            currentMessageElement = null;
            streamThinkingDone = false;
            streamThinkingTimeMs = null;
            streamCodeExpanded = false;
            isGenerating = false;
            const errSession = sessions.find(s => s.chatId === currentChatSessionId);
            if (errSession) errSession.isGenerating = false;
            const errPinnedSession = pinnedSessions.find(s => s.chatId === currentChatSessionId);
            if (errPinnedSession) errPinnedSession.isGenerating = false;
            renderSessionList();
            renderPinnedList();
            updateSendButtonState();
            loadChatModels();
            break;
        case 'session_expiry':
            sessionExpiryHandled = true;
            showAlert(t(data.msgKey, data.params) || data.message, 'info');
            setTimeout(() => {
                window.location.reload();
            }, 2000);
            break;
        case 'title_update':
            if (data.title) {
                const session = sessions.find(s => s.chatId === currentChatSessionId);
                if (session) {
                    session.title = data.title;
                    renderSessionList();
                    renderPinnedList();
                }
            }
            break;
      case 'session_recovery': {
            const lastMsg = data.lastAssistantMessage;
            const recovStatus = lastMsg.status;
            const lastUserMsg = data.lastUserMessage;
            const container = document.getElementById('messagesContainer');

            // INIT_BEFORE: user message not persisted yet, render it
            if (recovStatus === 'INIT_BEFORE' && lastUserMsg) {
                const mediaList = [];
                (lastUserMsg.imageUrlList || []).forEach(function(url) {
                    mediaList.push({ type: 'image', path: url, previewUrl: url });
                });
                (lastUserMsg.videoUrlList || []).forEach(function(url) {
                    mediaList.push({ type: 'video', path: url, previewUrl: url });
                });
                (lastUserMsg.audioUrlList || []).forEach(function(url) {
                    mediaList.push({ type: 'audio', path: url, previewUrl: url });
                });
                const textFiles = lastUserMsg.chatMediaTextList || [];
                const recoveryMsgIndex = allMessages.length;
                allMessages.push({
                    role: 'user',
                    content: lastUserMsg.content || '',
                    chatMediaTextList: textFiles
                });
                const userMsgDiv = createUserMessageDOM(lastUserMsg.content, mediaList, textFiles, recoveryMsgIndex);
                container.appendChild(userMsgDiv);
            }

            // A stale element from the interrupted stream may still be in the
            // DOM (its expanded thinking panel could even keep a leftover
            // streaming-follow that hides the scrollbar forever); drop it so
            // the recovery render is the only message for this turn.
            if (currentMessageElement && currentMessageElement.isConnected) {
                currentMessageElement.remove();
            }

            // Render assistant message
            // INIT_BEFORE/INIT_FINISHED: badge shown, thinking/content streamed later
            // THINKING: thinking block with spinner
            // GENERATION: thinking (collapsed) + content
            currentStreamModelName = lastMsg.modelName || (selectedChatModel ? selectedChatModel.modelName : null);
            const recoveryElement = createAssistantMessage(lastMsg.content, lastMsg.thinkingContent, lastMsg.stats, false, recovStatus, lastMsg.timestamp, currentStreamModelName);

            currentMessageElement = recoveryElement;
            currentAssistantMessage = lastMsg.content || '';
            currentThinkingContent = lastMsg.thinkingContent || '';
            isRecovering = true;
            streamThinkingDone = recovStatus === 'GENERATION';
            streamThinkingTimeMs = (lastMsg.stats && lastMsg.stats.thinkingTimeMs != null) ? lastMsg.stats.thinkingTimeMs : null;

            if (isInRunning(recovStatus)) {
                isGenerating = true;
                document.querySelector('.btn-send').style.display = 'none';
                document.getElementById('btnStop').style.display = 'flex';
                const recovSession = sessions.find(s => s.chatId === currentChatSessionId);
                if (recovSession) recovSession.isGenerating = true;
                const recovPinnedSession = pinnedSessions.find(s => s.chatId === currentChatSessionId);
                if (recovPinnedSession) recovPinnedSession.isGenerating = true;
                renderSessionList();
                renderPinnedList();
            }

            scrollToBottom();
            break;
        }
    }
}

function finalizeThinkingBlock(thinkingBlock, stats, timestamp) {
    const spinner = thinkingBlock.querySelector('.thinking-spinner');
    if (spinner) {
        spinner.remove();
    }
    const statusIndicator = thinkingBlock.querySelector('.thinking-status-indicator');
    if (statusIndicator) {
        statusIndicator.remove();
    }
    if (stats && stats.thinkingTimeMs) {
        let timeSpan = thinkingBlock.querySelector('.thinking-time');
        if (!timeSpan) {
            timeSpan = document.createElement('span');
            timeSpan.className = 'thinking-time';
            const headerRight = thinkingBlock.querySelector('.thinking-header-right');
            if (headerRight) {
                headerRight.appendChild(timeSpan);
            }
        }
        timeSpan.textContent = formatDuration(stats.thinkingTimeMs);
    }
    if (timestamp) {
        thinkingBlock.dataset.timestamp = timestamp;
    }
    thinkingBlock.classList.add('collapsed');
}

function removeThinkingSpinner(thinkingBlock) {
    if (!thinkingBlock) return;
    const spinner = thinkingBlock.querySelector('.thinking-spinner');
    if (spinner) spinner.remove();
    const statusIndicator = thinkingBlock.querySelector('.thinking-status-indicator');
    if (statusIndicator) statusIndicator.remove();
}

function showStoppedBadge(bodyDiv, content, thinking) {
    const hasContent = content && content.trim();
    const hasThinking = thinking && thinking.trim();
    const thinkingBlock = bodyDiv.querySelector('.thinking-block');
    const contentDiv = bodyDiv.querySelector('.message-content');

    if (hasContent) {
        const badge = document.createElement('div');
        badge.className = 'message-stopped-badge';
        badge.textContent = t('chat.stopped');
        if (contentDiv) {
            contentDiv.appendChild(badge);
        } else {
            bodyDiv.appendChild(badge);
        }
        if (currentMessageElement) currentMessageElement.classList.add('message-stopped');
    } else if (hasThinking && thinkingBlock) {
        removeThinkingSpinner(thinkingBlock);
        const badge = document.createElement('span');
        badge.className = 'thinking-stopped-badge';
        badge.textContent = t('chat.stopped');
        const headerRight = thinkingBlock.querySelector('.thinking-header-right');
        if (headerRight) {
            headerRight.appendChild(badge);
            thinkingBlock.classList.remove('collapsed');
        }
        // Streaming is over: restore normal scrolling on the expanded block
        const thinkingContent = thinkingBlock.querySelector('.thinking-content');
        if (thinkingContent) thinkingContent.classList.remove('streaming-follow');
        // The un-collapse above re-opens the panel; pin it to its newest line
        // once the expand transition has settled.
        pinExpandedThinkingBlock(thinkingBlock);
    } else {
        const badge = document.createElement('span');
        badge.className = 'message-status stop';
        badge.textContent = t('chat.stopped');
        bodyDiv.appendChild(badge);
    }
}

// Pin the expanded thinking panel to its newest line. The rAF re-pin covers
// the case where the scrollTop write lands before the replaced content is
// laid out; .streaming-follow keeps the element scrollable (scrollbar hidden,
// not overflow:hidden) so the write can't be silently ignored.
function pinThinkingContentToBottom(thinkingContent, thinkingBlock) {
    thinkingContent.scrollTop = thinkingContent.scrollHeight;
    requestAnimationFrame(function () {
        if (thinkingContent.isConnected && thinkingBlock && !thinkingBlock.classList.contains('collapsed')) {
            thinkingContent.scrollTop = thinkingContent.scrollHeight;
        }
    });
}

// After streaming ends, an expanded thinking panel must sit on its newest
// line. The final render can change the content height, a STOP badge may
// un-collapse the block right after the collapse logic ran, and the expand
// transition takes ~300ms to settle — so re-pin now and again once the
// transition has landed (rAF + timeout cover reduced-motion browsers where
// no transitionend fires).
function pinExpandedThinkingBlock(container) {
    const thinkingBlock = container && container.classList.contains('thinking-block')
        ? container : (container ? container.querySelector('.thinking-block') : null);
    if (!thinkingBlock || thinkingBlock.classList.contains('collapsed')) return;
    const thinkingContent = thinkingBlock.querySelector('.thinking-content');
    if (!thinkingContent) return;
    const pin = () => {
        if (thinkingBlock.isConnected && !thinkingBlock.classList.contains('collapsed')) {
            thinkingContent.scrollTop = thinkingContent.scrollHeight;
        }
    };
    pin();
    requestAnimationFrame(pin);
    setTimeout(pin, 100);
    setTimeout(pin, 400);
}

// While the streaming pin (streaming-follow) is active, keep the user from
// scrolling the thinking content away from its newest line — the mirror of
// the streaming code blocks in the message body, whose collapsed tail window
// is simply not scrollable. The element itself stays genuinely scrollable
// for the programmatic pin, so the lock is enforced on the input side: wheel,
// touch and keyboard scrolling are all suppressed while the class is present.
// Once streaming ends the streaming-follow class is removed and scrolling is
// free again. Idempotent — several render paths attach the guard to the same
// element.
function attachThinkingFollowGuard(thinkingContent) {
    if (!thinkingContent || thinkingContent.__followGuardAttached) return;
    thinkingContent.__followGuardAttached = true;
    const locked = function () {
        return thinkingContent.classList.contains('streaming-follow');
    };
    thinkingContent.addEventListener('wheel', function (e) {
        if (locked()) e.preventDefault();
    }, { passive: false });
    thinkingContent.addEventListener('touchmove', function (e) {
        if (locked()) e.preventDefault();
    }, { passive: false });
    thinkingContent.addEventListener('keydown', function (e) {
        if (!locked()) return;
        // Never eat keys meant for interactive children (copy buttons, ...).
        const tag = (e.target.tagName || '').toLowerCase();
        if (tag === 'button' || tag === 'input' || tag === 'textarea' || tag === 'select' || tag === 'a') return;
        const scrollKeys = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'PageUp', 'PageDown', 'Home', 'End', ' '];
        if (scrollKeys.indexOf(e.key) !== -1) e.preventDefault();
    });
}

function toggleThinking(header) {
    const container = header.parentElement;
    if (!container) return;
    const thinkingContent = container.querySelector('.thinking-content');
    const wasCollapsed = container.classList.contains('collapsed');
    // 仅当思考阶段仍在流式输出时, 展开的面板才跟随最新一行(钉在底部);
    // 对已结束/历史消息的手动展开, 面板从顶部开始, 主容器不跳动。
    const followStream = isGenerating && !streamThinkingDone;
    // Captured before the class change: if the user was pinned to the newest
    // message before expanding, the growth from the expand transition must be
    // re-pinned so the latest content stays visible at the bottom.
    const wasAtBottom = !wasCollapsed || isAtBottom();
    const settleMainScrollAfterToggle = () => {
        // 流式跟随展开时保持主容器贴底(自动跟随语义);
        // 手动展开已完成思考时不跳到对话底部 — 面板从可见的头部向下生长,
        // 原地保持可见即可, 跳到底部反而会把刚展开的内容推出视口。
        if (wasCollapsed && followStream && wasAtBottom && !autoScrollDisabled) scrollToBottom();
        syncAutoScrollState();
    };
    if (wasCollapsed) {
        container.classList.remove('collapsed');
        // Only while the thinking phase is still streaming does an expanded
        // block follow its newest content with the scrollbar hidden; once the
        // thinking phase ends (even if the answer is still generating) the
        // panel is static: keep the scrollbar and free scrolling.
        if (followStream && thinkingContent) {
            thinkingContent.classList.add('streaming-follow');
        } else if (thinkingContent) {
            thinkingContent.classList.remove('streaming-follow');
        }
        // 流式输出中展开: 钉在最新一行; 思考已结束: 从顶部开始阅读
        // (折叠前残留的 scrollTop 会让展开后只看到内容尾部)。
        // The timeout covers browsers where the expand transition doesn't run
        // (e.g. reduced motion); the transitionend handler re-applies once the
        // max-height animation lands and scrollHeight is fully settled.
        const pinPanelToBottom = () => {
            if (thinkingContent && !container.classList.contains('collapsed')) {
                thinkingContent.scrollTop = thinkingContent.scrollHeight;
            }
        };
        const scrollPanelToTop = () => {
            if (thinkingContent && !container.classList.contains('collapsed')) {
                thinkingContent.scrollTop = 0;
            }
        };
        const applyPanelScroll = followStream ? pinPanelToBottom : scrollPanelToTop;
        setTimeout(applyPanelScroll, 100);
        if (thinkingContent) {
            const onExpandSettled = (e) => {
                if (e.target !== thinkingContent || e.propertyName !== 'max-height') return;
                thinkingContent.removeEventListener('transitionend', onExpandSettled);
                applyPanelScroll();
                settleMainScrollAfterToggle();
            };
            thinkingContent.addEventListener('transitionend', onExpandSettled);
            // The expand transition (~300ms) grows the message without firing
            // a scroll event; in reduced-motion browsers it never fires, so
            // this timeout is the guaranteed settle path — it also detaches
            // the transitionend listener so no stale handler lingers.
            setTimeout(function () {
                thinkingContent.removeEventListener('transitionend', onExpandSettled);
                applyPanelScroll();
                settleMainScrollAfterToggle();
            }, 400);
        }
    } else {
        container.classList.add('collapsed');
        if (thinkingContent) thinkingContent.classList.remove('streaming-follow');
    }
    // expanding/collapsing changes page height without firing a scroll event;
    // re-evaluate the pin state so an expanded block above the viewport stops auto-follow
    requestAnimationFrame(() => syncAutoScrollState());
    const ts = container.dataset?.timestamp;
    if (currentChatSessionId && ts) {
        thinkingStateMap.set(currentChatSessionId + ':' + ts, container.classList.contains('collapsed') ? 'collapsed' : 'expanded');
    }
}

function renderStats(messageElement, stats) {
    const statsDiv = document.createElement('div');
    statsDiv.className = 'message-stats';
    statsDiv.innerHTML = `
        <span class="stats-toggle" onclick="toggleStats(this)">!</span>
        <div class="stats-details-popup">
            <div class="stats-row">
                <span>${t('chat.stats_first_token_latency')}:</span>
                <span>${stats.firstTokenLatencyMs != null ? formatDuration(stats.firstTokenLatencyMs) : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_total_time')}:</span>
                <span>${stats.processingTimeMs != null ? formatDuration(stats.processingTimeMs) : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_thinking_time')}:</span>
                <span>${stats.thinkingTimeMs != null ? formatDuration(stats.thinkingTimeMs) : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_thinking_tokens')}:</span>
                <span>${stats.thinkingTokens != null ? stats.thinkingTokens : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_prompt_tokens')}:</span>
                <span>${stats.promptTokens != null ? stats.promptTokens : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_cached_tokens')}:</span>
                <span>${stats.cachedTokens != null ? stats.cachedTokens : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_prompt_time')}:</span>
                <span>${stats.promptMs != null ? formatDuration(stats.promptMs) : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_prompt_speed')}:</span>
                <span>${stats.promptPerSecond != null ? stats.promptPerSecond.toFixed(1) + ' tok/s' : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_output_tokens')}:</span>
                <span>${stats.completionTokens != null ? stats.completionTokens : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_total_tokens')}:</span>
                <span>${stats.totalTokens != null ? stats.totalTokens : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_generation_time')}:</span>
                <span>${stats.predictedMs != null ? formatDuration(stats.predictedMs) : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_generation_speed')}:</span>
                <span>${stats.predictedPerSecond != null ? stats.predictedPerSecond.toFixed(1) + ' tok/s' : '-'}</span>
            </div>
            <div class="stats-row">
                <span>${t('chat.stats_context_window')}:</span>
                <span>${stats.nCtx != null ? stats.nCtx : '-'}</span>
            </div>
        </div>
    `;
    
    const messageBody = messageElement.querySelector('.message-body');
    if (messageBody) {
        messageBody.appendChild(statsDiv);
    } else {
        messageElement.appendChild(statsDiv);
    }
}

function toggleStats(toggle) {
    const statsDiv = toggle.parentElement;
    if (!statsDiv) return;
    const details = statsDiv.querySelector('.stats-details-popup');
    if (!details) return;

    const wasActive = details.classList.contains('stats-active');
    closeAllStatsPopups();

    if (!wasActive) {
        const toggleRect = toggle.getBoundingClientRect();
        details._statsDiv = statsDiv;
        details.style.position = 'fixed';
        details.style.left = toggleRect.left + 'px';
        details.style.bottom = 'calc(100vh - ' + toggleRect.bottom + 'px)';
        details.style.top = 'auto';
        details.style.zIndex = '10000';
        document.body.appendChild(details);

        requestAnimationFrame(() => {
            const adjustedRect = details.getBoundingClientRect();
            if (adjustedRect.bottom > window.innerHeight) {
                details.style.bottom = 'auto';
                details.style.top = (toggleRect.top - adjustedRect.height - 8) + 'px';
            }
            if (adjustedRect.top < 0) {
                details.style.top = '8px';
            }
        });

        details.classList.add('stats-active');
    }
}

function closeAllStatsPopups() {
    document.querySelectorAll('.stats-details-popup.stats-active').forEach(el => {
        el.classList.remove('stats-active');
        el.style.position = '';
        el.style.bottom = '';
        el.style.top = '';
        el.style.left = '';
        el.style.zIndex = '';

        const originalStatsDiv = el._statsDiv;
        if (originalStatsDiv && originalStatsDiv.parentElement) {
            originalStatsDiv.appendChild(el);
            el._statsDiv = null;
        }
    });
}

function renderMessages(messages) {
    allMessages = messages;
    renderedMessageStart = 0;
    const container = document.getElementById('messagesContainer');
    container.innerHTML = '';
    
    if (messages.length === 0) {
        container.innerHTML = `<div class="welcome-message"><h1>${t('chat.welcome_title')}</h1><p>${t('chat.welcome_subtitle')}</p></div>`;
        return;
    }
    
    try {
        if (messages.length <= MESSAGE_RENDER_BATCH) {
            renderMessageBatch(0, messages.length);
        } else {
            renderedMessageStart = messages.length - MESSAGE_RENDER_BATCH;
            renderMessageBatch(renderedMessageStart, messages.length);
        }
    } catch (e) {
        // A mid-batch failure must not leave a dangling renderedMessageStart:
        // the scroll fallback would then load a middle chunk into an empty
        // container. Reset so the container and pagination state agree, then
        // rethrow so the caller (loadSession) can surface the error.
        renderedMessageStart = 0;
        container.innerHTML = '';
        throw e;
    }

    initMessageScroll();

    // Force scroll to bottom with retry for async image loading. The loop is
    // bound to this render's token: a session switch bumps the token and this
    // loop stops on its next tick, so a previous session's loop can never keep
    // scrolling the container the new session just replaced (that overlap was
    // the visible shake when switching quickly).
    const token = sessionRenderToken;
    let scrollRetryActive = true;
    const doScroll = () => {
        if (!scrollRetryActive) return;
        void container.offsetHeight;
        scrollToBottom();
    };

    doScroll();

    // Stop retry on user scroll/interaction
    const stopRetry = () => {
        scrollRetryActive = false;
        container.removeEventListener('wheel', stopRetry);
        container.removeEventListener('touchmove', stopRetry);
    };
    container.addEventListener('wheel', stopRetry, { once: true, passive: true });
    container.addEventListener('touchmove', stopRetry, { once: true, passive: true });

    // Retry for up to 3 seconds
    let retries = 0;
    const maxRetries = 30;
    const retry = () => {
        if (token !== sessionRenderToken || retries >= maxRetries || !scrollRetryActive) {
            stopRetry();
            return;
        }
        retries++;
        requestAnimationFrame(() => {
            if (token !== sessionRenderToken) { stopRetry(); return; }
            doScroll();
            setTimeout(retry, 100);
        });
    };
    retry();
}


function renderMessageBatch(start, end, prepend) {
    const container = document.getElementById('messagesContainer');
    const fragment = document.createDocumentFragment();
    
    // Sentinel index: the 11th message in this rendered batch
    const sentinelIdx = renderedMessageStart > 0 ? start + MESSAGE_SENTINAL_OFFSET : -1;

    for (let i = start; i < end; i++) {
        const msg = allMessages[i];
        const isSentinel = (i === sentinelIdx);
        if (msg.role === 'user') {
            const mediaList = [];
            (msg.imageUrlList || []).forEach(url => mediaList.push({ type: 'image', path: url, previewUrl: url }));
            (msg.videoUrlList || []).forEach(url => mediaList.push({ type: 'video', path: url, previewUrl: url }));
            (msg.audioUrlList || []).forEach(url => mediaList.push({ type: 'audio', path: url, previewUrl: url }));
            const textFiles = msg.chatMediaTextList || [];
            const msgDiv = createUserMessageDOM(msg.content, mediaList, textFiles, i);
            if (isSentinel) msgDiv.dataset.sentinel = 'true';
            fragment.appendChild(msgDiv);
        } else if (msg.role === 'assistant') {
            const msgDiv = createAssistantMessageDOM(msg.content, msg.thinkingContent, msg.stats, msg.status, msg.timestamp, msg.modelName);
            if (msg.stats) {
                renderStats(msgDiv, msg.stats);
            }
            if (isSentinel) msgDiv.dataset.sentinel = 'true';
            fragment.appendChild(msgDiv);
        }
    }
    
    // Every message is fully post-processed inside its own DOM builder
    // (createAssistantMessageDOM / createUserMessageDOM): code blocks are
    // wrapped and queued for deferred highlighting, thinking blocks already
    // carry their collapse state, header handlers and processed code blocks.
    // The batch therefore only builds the fragment and inserts it — and only
    // after the whole batch is built, so a throw mid-batch can never leave a
    // partially inserted range in the DOM (a caller retry would otherwise
    // render those messages a second time).
    if (prepend) {
        container.insertBefore(fragment, container.firstChild);
    } else {
        container.appendChild(fragment);
    }
}

function createUserMessageDOM(content, mediaList, textFiles, msgIndex) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message user';
    if (typeof msgIndex === 'number') {
        messageDiv.dataset.msgIndex = msgIndex;
    }
    const hasMedia = (mediaList || []).length > 0;
    const hasTextFiles = (textFiles || []).length > 0;
    const mediaHtml = (mediaList || []).map(m => {
        if (m.type === 'image') return `<img src="${escapeAttr(m.previewUrl)}" alt="${escapeAttr(m.name)}" onerror="handleImageError(this)">`;
        if (m.type === 'video') return `<video controls src="${escapeAttr(m.previewUrl)}"></video>`;
        if (m.type === 'audio') return `<audio controls src="${escapeAttr(m.previewUrl)}"></audio>`;
        return '';
    }).join('');
    const textFileHtml = (textFiles || []).map(f => {
        return `<div class="text-file-item" data-path="${escapeAttr(f.url)}" data-name="${escapeAttr(f.name)}" onclick="previewTextFileFromData(this)"><i class="bi bi-file-earmark-text-fill"></i><span class="text-file-name">${escapeHtml(f.name)}</span><i class="bi bi-eye text-file-preview-icon"></i></div>`;
    }).join('');
    const showWrapper = hasMedia || hasTextFiles;
    messageDiv.innerHTML = `
        <div class="message-content">
            <div class="message-user-actions">
                <button class="btn-copy-user-msg" title="${t('chat.copy_content')}"><i class="bi bi-clipboard"></i></button>
            </div>
            ${showWrapper ? `<div class="message-input-wrapper">
                ${hasMedia ? `<div class="message-media-container">${mediaHtml}</div>` : ''}
                ${hasTextFiles ? `<div class="message-text-files-container">${textFileHtml}</div>` : ''}
                <div class="message-text-container"></div>
            </div>` : `<div class="message-text-container"></div>`}
        </div>
        <div class="message-role-icon"><i class="bi bi-person-fill"></i></div>
    `;
    const textContainer = messageDiv.querySelector('.message-text-container');
    if (textContainer && content) {
        textContainer.textContent = content;
    }
    return messageDiv;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function createAssistantMessageDOM(content, thinkingContent = null, stats = null, status = null, timestamp = null, modelName = null) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message assistant';

    const thinkingTime = stats?.thinkingTimeMs ? formatDuration(stats.thinkingTimeMs) : '';

    messageDiv.innerHTML = `
        <div class="message-role-icon"><i class="bi bi-robot"></i></div>
        <div class="message-body"></div>
    `;

    const bodyDiv = messageDiv.querySelector('.message-body');

    if (thinkingContent && thinkingContent.trim()) {
        const thinkingBlock = document.createElement('div');
        const stateKey = currentChatSessionId && timestamp ? currentChatSessionId + ':' + timestamp : '';
        const isExpanded = stateKey ? thinkingStateMap.get(stateKey) === 'expanded' : false;
        thinkingBlock.className = 'thinking-block' + (isExpanded ? '' : ' collapsed');
        if (timestamp) {
            thinkingBlock.dataset.timestamp = timestamp;
        }
        let headerRightHtml = '';
        if (thinkingTime) {
            headerRightHtml += `<span class="thinking-time">${thinkingTime}</span>`;
        }
        if (status === 'THINKING') {
            headerRightHtml += '<span class="thinking-spinner"></span>';
        }
        thinkingBlock.innerHTML = `
            <div class="thinking-header" onclick="toggleThinking(this)">
                <div class="thinking-header-left">
                    <span class="thinking-icon">${t('chat.thinking_process')}</span>
                </div>
                <div class="thinking-header-right">
                    ${headerRightHtml}
                </div>
            </div>
            <div class="thinking-content">${safeMarkedParse(thinkingContent)}</div>
        `;
        bodyDiv.insertBefore(thinkingBlock, bodyDiv.firstChild);
        attachThinkingFollowGuard(thinkingBlock.querySelector('.thinking-content'));
        processThinkingCodeBlocks(thinkingBlock, true);
    }

    // 模型名称标签放在消息最前面(思考块/正文之前), 为空时不渲染
    const modelLabel = createModelNameLabel(modelName);
    if (modelLabel) bodyDiv.insertBefore(modelLabel, bodyDiv.firstChild);

    if (content && content.trim()) {
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.innerHTML = safeMarkedParse(content);
        bodyDiv.appendChild(contentDiv);

        processCodeBlocks(messageDiv, 'message-content', true);
    }

    if (status === 'INIT_BEFORE' || status === 'INIT_FINISHED') {
        const statusBadge = document.createElement('span');
        statusBadge.className = 'message-status going';
        statusBadge.innerHTML = '<span class="status-spinner"></span> ' + t('chat.generating');
        bodyDiv.appendChild(statusBadge);
    } else if (status === 'STOP') {
        showStoppedBadge(bodyDiv, content, thinkingContent);
        if (content && content.trim()) {
            messageDiv.classList.add('message-stopped');
        }
    }

    return messageDiv;
}

function initMessageScroll() {
    if (messageSentinelObserver) return;
    messageScrollLoading = false;
    const container = document.getElementById('messagesContainer');
    // threshold 必须为 0: 若用 0.1, 哨兵消息高度超过视口 10 倍时(长对话中的
    // 大消息/长代码块)交叠比永远达不到 10%, 回调不会触发, 更早的消息就无法
    // 继续加载。rootMargin 在哨兵进入视口前提前预取下一批。
    messageSentinelObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting && renderedMessageStart > 0 && !messageScrollLoading) {
                messageSentinelObserver.disconnect();
                loadMoreMessages();
            }
        });
    }, { root: container, threshold: 0, rootMargin: '300px 0px 0px 0px' });
    const sentinel = container.querySelector('[data-sentinel="true"]');
    if (sentinel) {
        messageSentinelObserver.observe(sentinel);
    }
}

function loadMoreMessages() {
    if (renderedMessageStart <= 0) {
        if (messageSentinelObserver) {
            messageSentinelObserver.disconnect();
            messageSentinelObserver = null;
        }
        return;
    }
    messageScrollLoading = true;

    const container = document.getElementById('messagesContainer');
    const scrollTop = container.scrollTop;
    const scrollHeightBefore = container.scrollHeight;

    try {
        const loadStart = Math.max(0, renderedMessageStart - MESSAGE_RENDER_BATCH);

        if (loadStart < renderedMessageStart) {
            renderMessageBatch(loadStart, renderedMessageStart, true);
            renderedMessageStart = loadStart;

            const scrollHeightAfter = container.scrollHeight;
            container.scrollTop = scrollTop + (scrollHeightAfter - scrollHeightBefore);
        }

        if (renderedMessageStart <= 0) {
            if (messageSentinelObserver) {
                messageSentinelObserver.disconnect();
                messageSentinelObserver = null;
            }
        } else {
            // Re-bind sentinel to the new 11th message
            if (messageSentinelObserver) {
                messageSentinelObserver.disconnect();
                const newSentinel = container.querySelector('[data-sentinel="true"]');
                if (newSentinel) {
                    messageSentinelObserver.observe(newSentinel);
                }
            }
        }
    } catch (e) {
        // 渲染异常不能卡死分页: 复位加载标志并保留哨兵, 下次滚动仍可重试
        console.error('Load more messages failed:', e);
    } finally {
        messageScrollLoading = false;
    }
}

function createUserMessage(content, mediaList, saveToSession = true) {
    const textFiles = currentTextFileList.map(f => ({ url: f.path, name: f.name, relativePath: f.relativePath || '' }));
    const container = document.getElementById('messagesContainer');
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message user';
    const hasMedia = (mediaList || []).length > 0;
    const hasTextFiles = textFiles.length > 0;
    const mediaHtml = (mediaList || []).map(m => {
        if (m.type === 'image') return `<img src="${escapeAttr(m.previewUrl)}" alt="${escapeAttr(m.name)}" onerror="handleImageError(this)">`;
        if (m.type === 'video') return `<video controls src="${escapeAttr(m.previewUrl)}"></video>`;
        if (m.type === 'audio') return `<audio controls src="${escapeAttr(m.previewUrl)}"></audio>`;
        return '';
    }).join('');
    const textFileHtml = textFiles.map(f => {
        return `<div class="text-file-item" data-path="${escapeAttr(f.url)}" data-name="${escapeAttr(f.name)}" onclick="previewTextFileFromData(this)"><i class="bi bi-file-earmark-text-fill"></i><span class="text-file-name">${escapeHtml(f.name)}</span><i class="bi bi-eye text-file-preview-icon"></i></div>`;
    }).join('');
    const showWrapper = hasMedia || hasTextFiles;
    messageDiv.innerHTML = `
        <div class="message-content">
            <div class="message-user-actions">
                <button class="btn-copy-user-msg" title="${t('chat.copy_content')}"><i class="bi bi-clipboard"></i></button>
            </div>
            ${showWrapper ? `<div class="message-input-wrapper">
                ${hasMedia ? `<div class="message-media-container">${mediaHtml}</div>` : ''}
                ${hasTextFiles ? `<div class="message-text-files-container">${textFileHtml}</div>` : ''}
                <div class="message-text-container"></div>
            </div>` : `<div class="message-text-container"></div>`}
        </div>
        <div class="message-role-icon"><i class="bi bi-person-fill"></i></div>
    `;
    const textContainer = messageDiv.querySelector('.message-text-container');
    if (textContainer && content) {
        textContainer.textContent = content;
    }
    const userMsgIndex = allMessages.length;
    messageDiv.dataset.msgIndex = userMsgIndex;
    allMessages.push({
        role: 'user',
        content: content || '',
        chatMediaTextList: textFiles
    });
    container.appendChild(messageDiv);
    scrollToBottom();
}

function createAssistantMessage(content, thinkingContent = null, stats = null, saveToSession = true, status = null, timestamp = null, modelName = null) {
    const container = document.getElementById('messagesContainer');
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message assistant';

    const thinkingTime = stats?.thinkingTimeMs ? formatDuration(stats.thinkingTimeMs) : '';

    messageDiv.innerHTML = `
        <div class="message-role-icon"><i class="bi bi-robot"></i></div>
        <div class="message-body"></div>
    `;
    container.appendChild(messageDiv);

    const bodyDiv = messageDiv.querySelector('.message-body');

    if (thinkingContent && thinkingContent.trim()) {
        const thinkingBlock = document.createElement('div');
        const stateKey = currentChatSessionId && timestamp ? currentChatSessionId + ':' + timestamp : '';
        const isExpanded = stateKey ? thinkingStateMap.get(stateKey) === 'expanded' : false;
        thinkingBlock.className = 'thinking-block' + (isExpanded ? '' : ' collapsed');
        if (timestamp) {
            thinkingBlock.dataset.timestamp = timestamp;
        }
        let headerRightHtml = '';
        if (thinkingTime) {
            headerRightHtml += `<span class="thinking-time">${thinkingTime}</span>`;
        }
        if (status === 'THINKING') {
            headerRightHtml += '<span class="thinking-spinner"></span>';
        }
        thinkingBlock.innerHTML = `
            <div class="thinking-header" onclick="toggleThinking(this)">
                <div class="thinking-header-left">
                    <span class="thinking-icon">${t('chat.thinking_process')}</span>
                </div>
                <div class="thinking-header-right">
                    ${headerRightHtml}
                </div>
            </div>
            <div class="thinking-content">${safeMarkedParse(thinkingContent)}</div>
        `;
        bodyDiv.insertBefore(thinkingBlock, bodyDiv.firstChild);
        attachThinkingFollowGuard(thinkingBlock.querySelector('.thinking-content'));
        processThinkingCodeBlocks(thinkingBlock, true);
    }

    // 模型名称标签放在消息最前面(思考块/正文之前), 为空时不渲染
    const modelLabel = createModelNameLabel(modelName);
    if (modelLabel) bodyDiv.insertBefore(modelLabel, bodyDiv.firstChild);

    if (content && content.trim()) {
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content' + (isInRunning(status) ? ' streaming' : '');
        contentDiv.innerHTML = safeMarkedParse(content);
        bodyDiv.appendChild(contentDiv);

        if (isInRunning(status)) {
            // Initialize _streamPrevHtml so incrementalAppendContent doesn't do full re-render
            contentDiv._streamPrevHtml = safeMarkedParseStreaming(content);
        } else {
            processCodeBlocks(messageDiv, 'message-content', true);
        }
    }

    if (status === 'INIT_BEFORE' || status === 'INIT_FINISHED') {
        const statusBadge = document.createElement('span');
        statusBadge.className = 'message-status going';
        statusBadge.innerHTML = '<span class="status-spinner"></span> ' + t('chat.generating');
        bodyDiv.appendChild(statusBadge);
    } else if (status === 'STOP') {
        showStoppedBadge(bodyDiv, content, thinkingContent);
        if (content && content.trim()) {
            messageDiv.classList.add('message-stopped');
        }
    }

    currentMessageElement = messageDiv;
    const wasAtBottom = isAtBottom();
    requestAnimationFrame(() => {
        if (wasAtBottom || !isGenerating) {
            scrollToBottom();
        }
    });
    return messageDiv;
}

function clearMessages() {
    const container = document.getElementById('messagesContainer');
    if (messageSentinelObserver) {
        messageSentinelObserver.disconnect();
        messageSentinelObserver = null;
    }
    allMessages = [];
    renderedMessageStart = 0;
    messageScrollLoading = false;
    resetAutoScroll();
        container.innerHTML = `
        <div class="welcome-message">
            <h1>${t('chat.welcome_title')}</h1>
            <p>${t('chat.welcome_subtitle')}</p>
        </div>
    `;
    document.getElementById('headerStats').style.display = 'none';
    document.getElementById('contextUsage').style.display = 'none';
}

// Tags models commonly emit that are not hljs language ids or aliases,
// mapped to the closest grammar. The vendored build ships every
// highlight.js grammar, so common tags (sh, docker, toml, powershell, ...)
// already resolve natively; only the genuinely missing ones are listed here.
const HLJS_LANG_ALIAS = {
    jsonc: 'json',
    json5: 'json',
    vue: 'xml',
    svelte: 'xml',
    html5: 'xml',
    css3: 'css',
    git: 'diff',
    mysql: 'sql',
    mariadb: 'sql',
    sqlite: 'sql',
    mssql: 'sql',
    tsql: 'sql',
    python3: 'python',
    py3: 'python',
    zsh: 'bash',
    ksh: 'bash',
    fish: 'bash',
    rscript: 'r',
    env: 'ini',
    conf: 'ini',
    cfg: 'ini',
    plain: 'plaintext',
    code: 'plaintext',
    log: 'plaintext',
    output: 'plaintext',
    result: 'plaintext'
};

// Grammars tried when a block has no usable language tag. A subset keeps
// highlightAuto fast (dozens of grammars instead of all ~190) while covering
// the languages model output most often.
const HLJS_AUTO_DETECT_LANGS = [
    'python', 'javascript', 'typescript', 'java', 'c', 'cpp', 'csharp', 'go',
    'rust', 'ruby', 'php', 'swift', 'kotlin', 'sql', 'bash', 'shell', 'xml',
    'css', 'scss', 'less', 'yaml', 'json', 'ini', 'toml', 'properties',
    'markdown', 'makefile', 'lua', 'perl', 'r', 'graphql', 'scala', 'groovy',
    'gradle', 'fsharp', 'dart', 'powershell', 'cmake', 'dockerfile', 'nginx',
    'http', 'proto', 'diff'
];

// Minimum highlightAuto relevance before an auto-detected language is
// applied; below it the block is left plain so prose is never mis-colored.
const HLJS_AUTO_DETECT_MIN_RELEVANCE = 3;

// Tags that mean "no highlighting" (plaintext itself plus its native aliases).
const HLJS_PLAINTEXT_TAGS = new Set(['plaintext', 'text', 'txt']);

// highlightAuto is the most expensive highlight path (it tries every grammar
// in the subset, ~1.6ms/line). Only auto-detect small untagged blocks; bigger
// ones (often logs) are left plain. Tagged highlighting is linear and cheap,
// but huge blocks are still not worth it — skip them entirely.
const HLJS_AUTODETECT_MAX_LINES = 150;
const HLJS_HARD_MAX_LINES = 4000;

function processCodeBlocks(element, contentClass, collapsed) {
    let contentDiv;
    const targetClass = contentClass || 'message-content';
    if (element.classList && element.classList.contains(targetClass)) {
        contentDiv = element;
    } else {
        contentDiv = element.querySelector('.' + targetClass);
    }
    if (!contentDiv) return;

    const deferred = [];

    contentDiv.querySelectorAll('pre code').forEach((block) => {
        const parentPre = block.parentElement;
        if (!parentPre) return;
        const existingWrapper = parentPre.closest('.code-block-wrapper');
        if (existingWrapper) {
            if (existingWrapper.classList.contains('streaming-code-block')) {
                // Finalize: replace the streaming wrapper with the full one
                existingWrapper.parentNode.insertBefore(parentPre, existingWrapper);
                existingWrapper.remove();
            } else {
                return; // already wrapped, skip
            }
        }

        const langClass = block.className.split(' ').find(c => c.startsWith('lang-') || c.startsWith('language-'));
        const lang = langClass ? langClass.replace('lang-', '').replace('language-', '') : '';
        const code = block.textContent;

        // Wrap the block and add line numbers on the plain text immediately so
        // the message is paintable without waiting for the (expensive) syntax
        // highlighting. The actual coloring runs in idle time — opening or
        // switching a session must not block on highlightAuto/highlight.
        addLineNumbersToCode(block);
        wrapCodeBlock(parentPre, lang || 'code', code, collapsed);

        deferred.push({ block, lang, code });
    });

    scheduleCodeHighlighting(deferred);
}

// Deferred syntax highlighting queue. processCodeBlocks only wraps/numbers
// blocks synchronously; the costly hljs work is drained here, a few blocks per
// idle slice, so a long conversation opens instantly and colors in shortly
// after. Blocks whose element left the document (session switched away) are
// skipped, which also lets a stale queue drain cheaply.
let codeHighlightQueue = [];
let codeHighlightScheduled = false;

function scheduleCodeHighlighting(items) {
    if (!items.length) return;
    for (let i = 0; i < items.length; i++) codeHighlightQueue.push(items[i]);
    if (codeHighlightScheduled) return;
    codeHighlightScheduled = true;
    const run = () => {
        codeHighlightScheduled = false;
        // Time budget is enforced between blocks; a single block cannot be
        // interrupted once started. Measured worst cases: ~180ms for
        // highlightAuto on a 150-line untagged block (the first such call
        // after page load pays extra JIT warmup, ~280ms) and ~90ms for a
        // tagged block near the 4000-line hard cap.
        const start = performance.now();
        while (codeHighlightQueue.length && performance.now() - start < 50) {
            highlightCodeBlock(codeHighlightQueue.shift());
        }
        if (codeHighlightQueue.length) {
            scheduleCodeHighlightIdle(run);
        }
    };
    scheduleCodeHighlightIdle(run);
}

function scheduleCodeHighlightIdle(run) {
    if (typeof requestIdleCallback === 'function') {
        requestIdleCallback(run, { timeout: 500 });
    } else {
        setTimeout(run, 60);
    }
}

function highlightCodeBlock(item) {
    const block = item.block;
    // Element was removed (session switch / re-render): nothing to color.
    if (!block || !block.isConnected) return;
    if (typeof hljs === 'undefined') return;
    const code = item.code;
    const lines = countCodeLines(code);
    if (lines > HLJS_HARD_MAX_LINES) return; // too big, keep plain

    try {
        const hljsLang = HLJS_LANG_ALIAS[item.lang] || item.lang;
        let highlighted;
        if (hljsLang && !HLJS_PLAINTEXT_TAGS.has(hljsLang) && hljs.getLanguage(hljsLang)) {
            // Known language: highlight with its own grammar
            highlighted = hljs.highlight(code, { language: hljsLang, ignoreIllegals: true }).value;
            block.classList.add('language-' + hljsLang);
        } else if (code.trim() && !HLJS_PLAINTEXT_TAGS.has(hljsLang) && lines <= HLJS_AUTODETECT_MAX_LINES) {
            // No language tag (or a tag with no matching grammar):
            // auto-detect within the common subset, and only apply
            // the result when the match is convincing enough.
            // Explicitly-plaintext tags (text/txt/plaintext) are never
            // auto-detected: the model asked for no highlighting.
            const auto = hljs.highlightAuto(code, HLJS_AUTO_DETECT_LANGS);
            if (auto.language && auto.relevance >= HLJS_AUTO_DETECT_MIN_RELEVANCE) {
                highlighted = auto.value;
                block.classList.add('language-' + auto.language);
            }
        }
        if (highlighted !== undefined) {
            block.innerHTML = highlighted;
            block.classList.add('hljs');
            // hljs output replaces the plain-text line spans; re-wrap so the
            // gutter line numbers survive the coloring.
            addLineNumbersToCode(block);
        }
    } catch (e) {
        // fallback: keep plain
    }
}

// Wrap each line of a highlighted <code> element in a .code-line span so CSS
// counters can render gutter line numbers. Nodes are distributed into per-line
// containers in document order; multi-line hljs tokens (e.g. triple-quoted
// strings) are split across lines, keeping their token classes. Newlines stay
// as real text nodes so copy/save still produce the original code.
function addLineNumbersToCode(codeEl) {
    const lines = [[]];

    // Split node into per-line fragments [{line, node}] starting at startLine.
    // Elements are cloned; a multi-line element (e.g. the
    // <span class="language-javascript"> sub-language wrapper inside an HTML
    // block) gets one clone per line, keeping its attributes and the token
    // spans nested inside it, so sub-language highlighting survives the split.
    function splitNode(node, startLine) {
        const text = node.textContent;
        if (node.nodeType === 3) {
            const parts = text.split('\n');
            const out = [];
            for (let i = 0; i < parts.length; i++) {
                if (parts[i]) out.push({ line: startLine + i, node: document.createTextNode(parts[i]) });
            }
            return out;
        }
        if (text.indexOf('\n') < 0) {
            return [{ line: startLine, node: node.cloneNode(true) }];
        }
        const fragments = [];
        const perLine = new Map();
        let cursor = startLine;
        Array.prototype.forEach.call(node.childNodes, function(child) {
            const childFragments = splitNode(child, cursor);
            for (let i = 0; i < childFragments.length; i++) {
                const f = childFragments[i];
                if (!perLine.has(f.line)) perLine.set(f.line, []);
                perLine.get(f.line).push(f.node);
            }
            cursor += (child.textContent.match(/\n/g) || []).length;
        });
        perLine.forEach(function(frags, line) {
            const clone = node.cloneNode(false);
            for (let i = 0; i < frags.length; i++) clone.appendChild(frags[i]);
            fragments.push({ line: line, node: clone });
        });
        return fragments;
    }

    let cursor = 0;
    Array.prototype.forEach.call(codeEl.childNodes, function(child) {
        const fragments = splitNode(child, cursor);
        for (let i = 0; i < fragments.length; i++) {
            const f = fragments[i];
            while (lines.length <= f.line) lines.push([]);
            lines[f.line].push(f.node);
        }
        cursor += (child.textContent.match(/\n/g) || []).length;
    });

    const frag = document.createDocumentFragment();
    lines.forEach(function(lineNodes, i) {
        if (i > 0) frag.appendChild(document.createTextNode('\n'));
        const lineSpan = document.createElement('span');
        lineSpan.className = 'code-line';
        lineNodes.forEach(function(n) { lineSpan.appendChild(n); });
        frag.appendChild(lineSpan);
    });
    codeEl.innerHTML = '';
    codeEl.appendChild(frag);

    // Drop the phantom empty last line created by a trailing newline
    if (codeEl.textContent.endsWith('\n')) {
        const lineSpans = codeEl.querySelectorAll('.code-line');
        const lastLine = lineSpans[lineSpans.length - 1];
        if (lastLine && lastLine.textContent === '') lastLine.remove();
    }
}

function processThinkingCodeBlocks(thinkingBlock, collapsed) {
    processCodeBlocks(thinkingBlock, 'thinking-content', collapsed);
}

// Count visible code lines: a single trailing newline only terminates the
// last line, it does not create an extra one.
function countCodeLines(code) {
    return (code.replace(/\n$/, '').match(/\n/g) || []).length + 1;
}

function wrapCodeBlock(preElement, lang, code, collapsed) {
    const wrapper = document.createElement('div');
    wrapper.className = 'code-block-wrapper';

    const lines = countCodeLines(code);
    const isLong = lines > 10;
    wrapper.dataset.long = isLong ? 'true' : 'false';

    if (isLong && collapsed) {
        preElement.scrollTop = 0;
        wrapper.classList.add('code-collapsed');
    }

    const isHtmlPreviewable = /^(html|htm)$/i.test(lang);

    const header = document.createElement('div');
    header.className = 'code-header';
    header.innerHTML = `
        <span class="code-lang">${lang} (${lines}${t('chat.unit_lines')})</span>
        <div class="code-actions">
            <button class="code-btn" onclick="copyCode(this)" title="${t('chat.copy_code')}">
                <i class="bi bi-clipboard"></i> ${t('chat.copy')}
            </button>
            <button class="code-btn" onclick="saveCode(this)" title="${t('chat.save_local')}">
                <i class="bi bi-download"></i> ${t('chat.save')}
            </button>
            ${isLong ? `
            <button class="code-btn expand-toggle" onclick="toggleCodeBlock(this)" title="${t('chat.expand_collapse')}">
                ${collapsed ? '<i class="bi bi-arrows-expand"></i> ' + t('chat.expand') : '<i class="bi bi-arrows-collapse"></i> ' + t('chat.collapse')}
            </button>
            ` : ''}
            ${isHtmlPreviewable ? `
            <button class="code-btn html-preview-toggle" onclick="toggleHtmlPreview(this)" title="${t('chat.html_preview_title')}">
                <i class="bi bi-eye"></i> ${t('chat.preview')}
            </button>
            ` : ''}
        </div>
    `;

    preElement.parentNode.insertBefore(wrapper, preElement);
    wrapper.appendChild(header);
    wrapper.appendChild(preElement);
}

// HTML preview: an in-flow pane docked at the far right of the chat row
// (.chat-container). When open, the chat area (header stats, messages and
// input) shrinks to its left, so the preview spans the full container height
// below the top toolbar and the input's right boundary stays fixed regardless
// of sidebar collapse/expand. The rendered page runs inside a sandboxed
// iframe fed by a blob URL: blob URLs keep a real document base URL (relative
// resources resolve) and behave like a normal tab for WebGL2 / GPU content;
// the CodePen-style sandbox flags are required for hardware-accelerated WebGL
// in Chrome (an opaque-origin frame falls back to software rendering, which
// fails failIfMajorPerformanceCaveat checks).
let htmlPreviewPanel = null;
// True while an open/switch is in flight, so a second click can't re-enter.
let htmlPreviewBusy = false;

function getHtmlPreviewPanel() {
    if (htmlPreviewPanel && htmlPreviewPanel.isConnected) return htmlPreviewPanel;
    const container = document.querySelector('.chat-container');
    if (!container) return null;

    const panel = document.createElement('div');
    panel.className = 'html-preview-frame';
    panel.innerHTML = `
        <div class="html-preview-header">
            <span class="html-preview-title"><i class="bi bi-eye"></i> ${t('chat.html_preview_title')}</span>
            <button class="html-preview-close" onclick="closeHtmlPreview()" title="${t('chat.close_preview')}">
                <i class="bi bi-x-lg"></i>
            </button>
        </div>
        <div class="html-preview-resizer" title=""></div>
        <iframe class="html-preview-iframe" sandbox="allow-scripts allow-same-origin"></iframe>
    `;
    // Last child of the chat row: always at the far right
    container.appendChild(panel);
    htmlPreviewPanel = panel;

    initHtmlPreviewResize(panel);

    return panel;
}

// Drag the left edge of the panel to resize it. Width is stored in px so the
// preview keeps its size (and the input's right boundary stays put) when the
// sidebar collapses or expands.
function initHtmlPreviewResize(panel) {
    const resizer = panel.querySelector('.html-preview-resizer');
    if (!resizer) return;
    let dragging = false;
    const onMove = (e) => {
        if (!dragging) return;
        const container = document.querySelector('.chat-container');
        if (!container) return;
        const rect = container.getBoundingClientRect();
        const widthPx = Math.min(rect.width * 0.8, Math.max(200, rect.right - e.clientX));
        panel.style.width = widthPx + 'px';
    };
    const onUp = () => {
        dragging = false;
        document.body.classList.remove('html-preview-resizing');
        resizer.removeEventListener('pointermove', onMove);
        resizer.removeEventListener('pointerup', onUp);
        resizer.removeEventListener('pointercancel', onUp);
    };
    resizer.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        dragging = true;
        document.body.classList.add('html-preview-resizing');
        // Capture keeps move/up events flowing even when the pointer leaves
        // the window mid-drag, so the drag can never get stuck.
        resizer.setPointerCapture(e.pointerId);
        resizer.addEventListener('pointermove', onMove);
        resizer.addEventListener('pointerup', onUp);
        resizer.addEventListener('pointercancel', onUp);
    });
}

function resetHtmlPreviewButtons() {
    document.querySelectorAll('.html-preview-toggle.active').forEach(function(b) {
        b.classList.remove('active');
        b.innerHTML = '<i class="bi bi-eye"></i> ' + t('chat.preview');
    });
}

function toggleHtmlPreview(btn) {
    const wrapper = btn.closest('.code-block-wrapper');
    if (!wrapper) return;

    // Clicking the active button again closes the preview
    if (btn.classList.contains('active')) {
        closeHtmlPreview();
        return;
    }

    const codeEl = wrapper.querySelector('code');
    if (!codeEl) return;

    if (htmlPreviewBusy) return;
    htmlPreviewBusy = true;

    // Opening the pane reflows the chat content (messages get narrower), which
    // can fire a scroll event that would be mistaken for a user scroll.
    skipAutoScroll = true;

    try {
        const panel = getHtmlPreviewPanel();
        if (!panel) return;

        const iframe = panel.querySelector('.html-preview-iframe');
        const code = codeEl.textContent;

        // Blob URL instead of srcdoc: real document base URL + normal tab
        // behavior. Assign the new source first, then revoke the previous
        // blob only once the swap has completed, so the old document finishes
        // unloading before its buffer is released (timeout covers a failed
        // swap).
        const oldSrc = iframe.src;
        iframe.src = URL.createObjectURL(new Blob([code], { type: 'text/html' }));
        if (oldSrc && oldSrc.startsWith('blob:')) {
            let revoked = false;
            const revoke = () => {
                if (revoked) return;
                revoked = true;
                URL.revokeObjectURL(oldSrc);
            };
            iframe.addEventListener('load', revoke, { once: true });
            setTimeout(revoke, 2000);
        }

        resetHtmlPreviewButtons();
        btn.classList.add('active');
        btn.innerHTML = '<i class="bi bi-eye-slash"></i> ' + t('chat.close_preview');

        panel.classList.add('open');
    } finally {
        htmlPreviewBusy = false;
        setTimeout(() => { skipAutoScroll = false; }, 200);
    }
}

function closeHtmlPreview() {
    // Closing the pane reflows the chat content (messages get wider again),
    // which can fire a scroll event that would be mistaken for a user scroll.
    skipAutoScroll = true;

    resetHtmlPreviewButtons();
    if (htmlPreviewPanel) {
        const iframe = htmlPreviewPanel.querySelector('.html-preview-iframe');
        if (iframe) {
            // Navigate away first, then revoke only once the old document has
            // actually unloaded; revoking mid-navigation would tear it down
            // while it is still shutting down. The timeout guarantees the URL
            // is eventually released even if the load event never fires.
            const oldSrc = iframe.src;
            if (oldSrc && oldSrc.startsWith('blob:')) {
                let revoked = false;
                const revoke = () => {
                    if (revoked) return;
                    revoked = true;
                    URL.revokeObjectURL(oldSrc);
                };
                iframe.addEventListener('load', revoke, { once: true });
                setTimeout(revoke, 2000);
            }
            iframe.src = 'about:blank';
        }
        htmlPreviewPanel.classList.remove('open');
    }
    setTimeout(() => { skipAutoScroll = false; }, 200);
}

function toggleCodeBlock(btn) {
    const wrapper = btn.closest('.code-block-wrapper');
    if (!wrapper) return;

    skipAutoScroll = true;

    // Effective state: neither class means "expanded by default" (full height),
    // so the first click always toggles to the opposite visual state.
    const currentlyExpanded = wrapper.classList.contains('code-expanded') || !wrapper.classList.contains('code-collapsed');
    if (currentlyExpanded) {
        wrapper.classList.remove('code-expanded');
        if (wrapper.dataset.long === 'true') wrapper.classList.add('code-collapsed');
    } else {
        wrapper.classList.add('code-expanded');
        wrapper.classList.remove('code-collapsed');
    }
    const isExpanded = !currentlyExpanded;
    if (wrapper.classList.contains('streaming-code-block')) {
        streamCodeExpanded = isExpanded;
    }
    // expanding/collapsing changes page height without firing a scroll event;
    // re-evaluate the pin state so an expanded block above the viewport stops auto-follow
    requestAnimationFrame(() => syncAutoScrollState());

    const pre = wrapper.querySelector('pre');
    if (pre && !isExpanded) {
        // Collapsing: reset to top so first n lines are visible
        pre.scrollTop = 0;
    }

    const expandBtn = wrapper.querySelector('.expand-toggle');
    if (expandBtn) {
        if (isExpanded) {
            expandBtn.innerHTML = '<i class="bi bi-arrows-collapse"></i> ' + t('chat.collapse');
        } else {
            expandBtn.innerHTML = '<i class="bi bi-arrows-expand"></i> ' + t('chat.expand');
        }
    }

    setTimeout(() => {
        skipAutoScroll = false;
    }, 100);
}

function copyCode(btn) {
    const wrapper = btn.closest('.code-block-wrapper');
    const code = wrapper.querySelector('code').textContent;
    navigator.clipboard.writeText(code).then(() => {
        const icon = btn.querySelector('i');
        icon.className = 'bi bi-check';
        btn.innerHTML = '<i class="bi bi-check"></i> ' + t('chat.copied');
        setTimeout(() => {
            btn.innerHTML = '<i class="bi bi-clipboard"></i> ' + t('chat.copy');
        }, 1500);
    });
}

function saveCode(btn) {
    const wrapper = btn.closest('.code-block-wrapper');
    const code = wrapper.querySelector('code').textContent;
    const langText = (wrapper.querySelector('.code-lang')?.textContent || 'txt').toLowerCase();
    const lang = langText.split('(')[0].split(' ')[0].trim();
    const ext = CODE_EXT_MAP[lang] || lang;
    const filename = `code_${Date.now()}.${ext}`;
    const blob = new Blob([code], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
}

(function injectCopyStyles() {
    const style = document.createElement('style');
    style.textContent = `
.message.user .message-content {
    position: relative;
}
.message-user-actions {
    position: absolute;
    right: 8px;
    bottom: 8px;
    opacity: 0;
    transition: opacity 0.2s;
    pointer-events: none;
    z-index: 10;
}
.message.user:hover .message-user-actions {
    opacity: 1;
    pointer-events: auto;
}
.btn-copy-user-msg {
    background: none;
    border: none;
    cursor: pointer;
    color: #888;
    padding: 4px 6px;
    font-size: 14px;
    border-radius: 4px;
    transition: color 0.15s, background 0.15s;
    display: flex;
    align-items: center;
    justify-content: center;
}
.btn-copy-user-msg:hover {
    color: #333;
    background: rgba(0,0,0,0.06);
}
.user-copy-menu {
    position: absolute;
    right: 0;
    top: 100%;
    margin-top: 4px;
    background: #fff;
    border: 1px solid #e0e0e0;
    border-radius: 6px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.12);
    z-index: 1000;
    min-width: 140px;
    overflow: hidden;
}
.user-copy-menu-item {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 14px;
    cursor: pointer;
    font-size: 13px;
    color: #333;
    border: none;
    background: none;
    width: 100%;
    text-align: left;
    transition: background 0.15s;
}
.user-copy-menu-item:hover {
    background: #f5f5f5;
}
.user-copy-menu-item i {
    font-size: 14px;
    color: #666;
}
`;
    document.head.appendChild(style);
})();

let currentCopyMenu = null;
let copyMenuSourceMessage = null;
let justOpenedMenu = false;

function hideCopyMenu() {
    if (justOpenedMenu) {
        justOpenedMenu = false;
        return;
    }
    if (currentCopyMenu) {
        currentCopyMenu.remove();
        currentCopyMenu = null;
    }
    copyMenuSourceMessage = null;
}

function copyUserMessageData(messageDiv) {
    const msgIndex = messageDiv.dataset.msgIndex;
    let msgData;
    if (typeof msgIndex === 'string' && allMessages && allMessages[parseInt(msgIndex)]) {
        const src = allMessages[parseInt(msgIndex)];
        msgData = {
            content: src.content || '',
            chatMediaTextList: src.chatMediaTextList || []
        };
    } else {
        const textContainer = messageDiv.querySelector('.message-text-container');
        const text = textContainer ? textContainer.textContent || '' : '';
        // Fallback: extract text files from DOM
        const textFileItems = messageDiv.querySelectorAll('.text-file-item');
        const textFiles = [];
        textFileItems.forEach(item => {
            const onclick = item.getAttribute('onclick') || '';
            const match = onclick.match(/previewTextFile\('([^']+)'\s*,\s*'([^']*)'\)/);
            if (match) {
                textFiles.push({ url: match[1], name: match[2], relativePath: '' });
            }
        });
        msgData = { content: text, chatMediaTextList: textFiles };
    }
    return fetch(`${API_BASE}/chat/user-message/copy-text?sessionId=${encodeURIComponent(getCurrentSessionId())}`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(msgData)
    }).then(r => r.json()).then(result => {
        if (result.success) return result.data;
        throw new Error(result.msg || t('chat.fetch_text_failed'));
    });
}

function copyTextToClipboard(text) {
    return navigator.clipboard.writeText(text).then(() => {
        showToast(t('chat.copied_to_clipboard'), 'success');
    }).catch(() => {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.cssText = 'position:fixed;left:-9999px';
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); showToast(t('chat.copied_to_clipboard'), 'success'); } catch(e) { showToast(t('chat.copy_failed'), 'error'); }
        document.body.removeChild(ta);
    });
}

function saveTextToFile(text, filename) {
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showToast(t('chat.file_saved'), 'success');
}

function showCopyMenu(button, messageDiv) {
    hideCopyMenu();
    const rect = button.getBoundingClientRect();
    const menu = document.createElement('div');
    menu.className = 'user-copy-menu';
    menu.innerHTML = `
        <button class="user-copy-menu-item" data-action="clipboard"><i class="bi bi-clipboard"></i> ${t('chat.copy_to_clipboard')}</button>
        <button class="user-copy-menu-item" data-action="file"><i class="bi bi-download"></i> ${t('chat.save_as_file')}</button>
    `;
    menu.style.position = 'fixed';
    menu.style.left = rect.right + 'px';
    menu.style.top = rect.bottom + 'px';
    document.body.appendChild(menu);
    currentCopyMenu = menu;
    requestAnimationFrame(() => {
        const menuRect = menu.getBoundingClientRect();
        if (menuRect.right > window.innerWidth) {
            menu.style.left = (window.innerWidth - menuRect.width - 8) + 'px';
        }
        if (menuRect.bottom > window.innerHeight) {
            menu.style.top = (rect.top - menuRect.height) + 'px';
        }
    });
    menu.querySelectorAll('.user-copy-menu-item').forEach(item => {
        item.addEventListener('click', (e) => {
            e.stopPropagation();
            const action = item.dataset.action;
            hideCopyMenu();
            if (action === 'clipboard') {
                copyUserMessageData(messageDiv).then(text => copyTextToClipboard(text));
            } else if (action === 'file') {
                const ts = new Date();
                const pad = n => String(n).padStart(2, '0');
                const fname = `chat_${ts.getFullYear()}${pad(ts.getMonth()+1)}${pad(ts.getDate())}_${pad(ts.getHours())}${pad(ts.getMinutes())}${pad(ts.getSeconds())}.txt`;
                copyUserMessageData(messageDiv).then(text => saveTextToFile(text, fname));
            }
        });
    });
}

document.addEventListener('mousedown', (e) => {
    const copyBtn = e.target.closest('.btn-copy-user-msg');
    if (copyBtn) {
        e.preventDefault();
        e.stopPropagation();
        const messageDiv = copyBtn.closest('.message');
        if (messageDiv) {
            showCopyMenu(copyBtn, messageDiv);
        }
        return;
    }
    if (currentCopyMenu && !currentCopyMenu.contains(e.target)) {
        hideCopyMenu();
    }
});

document.addEventListener('click', (e) => {
    if (currentCopyMenu && !currentCopyMenu.contains(e.target) && !e.target.closest('.message-user-actions')) {
        hideCopyMenu();
    }
});

// Click-to-zoom for images inside messages: user-sent media and markdown
// images in assistant replies both live under .message-content.
document.getElementById('messagesContainer').addEventListener('click', function(e) {
    const img = e.target.closest('.message-content img');
    if (!img || img.closest('pre')) return;
    if (typeof openImageLightbox === 'function') {
        openImageLightbox(img.src);
    }
});














