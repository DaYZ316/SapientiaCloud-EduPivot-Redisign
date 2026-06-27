import DOMPurify from 'dompurify'
import {Marked} from 'marked'
import katex from 'katex'
import renderMathInElement from 'katex/contrib/auto-render'
import 'katex/contrib/mhchem'

const richMathMarked = new Marked()
const numberedFormulaMarker = String.raw`(?:\(\d+\)|\uFF08\d+\uFF09)`
type MathProtectionState = {
    segments: string[]
}

richMathMarked.setOptions({
    async: false,
    breaks: false,
    gfm: true,
})

export function renderRichMathMarkdown(content: string) {
    const mathState: MathProtectionState = {segments: []}
    const html = richMathMarked.parse(normalizeRichMathMarkdownSource(content, mathState)) as string
    const restoredHtml = restoreProtectedMathSegments(html, mathState)
    return DOMPurify.sanitize(restoredHtml, {
        USE_PROFILES: {
            html: true,
            mathMl: true,
        },
    })
}

function normalizeRichMathMarkdownSource(content: string, mathState: MathProtectionState) {
    return content
        .split(/(```[\s\S]*?```|~~~[\s\S]*?~~~)/g)
        .map((part) => {
            if (isFencedCodeBlock(part)) {
                return part
            }

            const protectedMath = protectMathSegments(wrapBareLatexBlocks(part), mathState)
            return normalizeMarkdownStructure(preserveEscapedMathDelimiters(protectedMath))
        })
        .join('')
}

function protectMathSegments(text: string, state: MathProtectionState) {
    let result = ''
    let cursor = 0
    while (cursor < text.length) {
        const segment = readMathSegment(text, cursor)
        if (segment) {
            result += protectMathSegment(segment, state)
            cursor += segment.length
            continue
        }

        result += text[cursor]
        cursor++
    }
    return result
}

function readMathSegment(text: string, start: number) {
    if (text.startsWith('$$', start) && !isEscapedByBackslash(text, start)) {
        const end = findClosingDelimiter(text, '$$', start + 2, true)
        return end >= 0 ? text.slice(start, end + 2) : ''
    }
    if (text.startsWith('\\[', start)) {
        const end = findClosingDelimiter(text, '\\]', start + 2, true)
        return end >= 0 ? text.slice(start, end + 2) : ''
    }
    if (text.startsWith('\\(', start)) {
        const end = findClosingDelimiter(text, '\\)', start + 2, true)
        return end >= 0 ? text.slice(start, end + 2) : ''
    }
    if (text[start] === '$' && text[start + 1] !== '$' && !isEscapedByBackslash(text, start)) {
        const end = findClosingDollar(text, start + 1)
        return end >= 0 ? text.slice(start, end + 1) : ''
    }
    return ''
}

function findClosingDelimiter(text: string, delimiter: string, start: number, allowNewline: boolean) {
    for (let index = start; index < text.length; index++) {
        if (!allowNewline && /\r|\n/.test(text[index])) {
            return -1
        }
        if (text.startsWith(delimiter, index) && !isEscapedByBackslash(text, index)) {
            return index
        }
    }
    return -1
}

function findClosingDollar(text: string, start: number) {
    for (let index = start; index < text.length; index++) {
        if (/\r|\n/.test(text[index])) {
            return -1
        }
        if (text[index] === '$' && text[index + 1] !== '$' && !isEscapedByBackslash(text, index)) {
            return index
        }
    }
    return -1
}

function protectMathSegment(segment: string, state: MathProtectionState) {
    const token = protectedMathToken(state.segments.length)
    state.segments.push(segment)
    return token
}

function protectedMathToken(index: number) {
    return `@@SC_RICH_MATH_${index}@@`
}

function restoreProtectedMathSegments(html: string, state: MathProtectionState) {
    return state.segments.reduce((result, segment, index) => {
        return result.split(protectedMathToken(index)).join(escapeHtml(segment))
    }, html)
}

function escapeHtml(value: string) {
    return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
}

function normalizeMarkdownStructure(text: string) {
    return text.replace(/([:：])\s+((?:[-*+]|\d+[.)])\s+)/g, '$1\n$2')
}

function preserveEscapedMathDelimiters(text: string) {
    return text.replace(/(^|[^\\])\\([\[\]\(\)])/g, '$1\\\\$2')
}

function isEscapedByBackslash(text: string, index: number) {
    let slashCount = 0
    for (let cursor = index - 1; cursor >= 0 && text[cursor] === '\\'; cursor--) {
        slashCount++
    }
    return slashCount % 2 === 1
}

export function normalizeRichDisplayMath(element: HTMLElement) {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, {
        acceptNode(node) {
            const parent = node.parentElement
            if (!parent || parent.closest('script, noscript, style, textarea, pre, code, option')) {
                return NodeFilter.FILTER_REJECT
            }

            return NodeFilter.FILTER_ACCEPT
        },
    })

    const textNodes: Text[] = []
    while (walker.nextNode()) {
        textNodes.push(walker.currentNode as Text)
    }

    textNodes.forEach((node) => {
        const normalized = normalizeMathText(node.data)
        if (normalized !== node.data) {
            node.data = normalized
        }
    })
}

export function renderRichMath(element: HTMLElement) {
    renderMathInElement(element, {
        delimiters: [
            {left: '$$', right: '$$', display: true},
            {left: '\\[', right: '\\]', display: true},
            {left: '\\(', right: '\\)', display: false},
            {left: '$', right: '$', display: false},
        ],
        ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code', 'option'],
        throwOnError: false,
        trust: false,
        strict: 'warn',
    })
}

export function formatRichFormulaRows(element: HTMLElement) {
    splitRenderedDisplayMathRows(element)

    element.querySelectorAll('p').forEach((paragraph) => {
        if (!(paragraph instanceof HTMLElement) || paragraph.classList.contains('formula-row-list')) {
            return
        }

        const childNodes = Array.from(paragraph.childNodes)
        const mathNodes = childNodes.filter(isMathNode)
        if (mathNodes.length < 2 || !isFormulaRowList(childNodes)) {
            return
        }

        const fragment = document.createDocumentFragment()
        let currentRow: HTMLSpanElement | null = null

        childNodes.forEach((node) => {
            if (isMathNode(node)) {
                currentRow = document.createElement('span')
                currentRow.className = 'formula-row'
                fragment.appendChild(currentRow)
            }

            if (currentRow) {
                currentRow.appendChild(node)
            } else {
                fragment.appendChild(node)
            }
        })

        paragraph.classList.add('formula-row-list')
        paragraph.replaceChildren(fragment)
    })
}

export function fitRichMathToContainer(element: HTMLElement) {
    element
        .querySelectorAll<HTMLElement>('.katex-display, .formula-row, .split-display-math-row')
        .forEach(fitMathBlockToContainer)
}

function fitMathBlockToContainer(container: HTMLElement) {
    const math = container.classList.contains('katex-display')
        ? container.querySelector<HTMLElement>(':scope > .katex')
        : container.querySelector<HTMLElement>('.katex')
    if (!math) {
        return
    }

    container.style.removeProperty('--rich-math-scale')
    container.classList.remove('rich-math-scaled')

    const availableWidth = container.clientWidth
    const naturalWidth = Math.ceil(math.scrollWidth || math.getBoundingClientRect().width)
    if (availableWidth <= 0 || naturalWidth <= availableWidth) {
        return
    }

    const scale = Math.min(1, availableWidth / naturalWidth)
    container.style.setProperty('--rich-math-scale', scale.toFixed(4))
    container.classList.add('rich-math-scaled')
}

function splitRenderedDisplayMathRows(element: HTMLElement) {
    element.querySelectorAll('.katex-display').forEach((displayMath) => {
        if (!(displayMath instanceof HTMLElement) || displayMath.classList.contains('split-display-math')) {
            return
        }

        const tex = displayMath.querySelector('annotation[encoding="application/x-tex"]')?.textContent
        if (!tex) {
            return
        }

        const compactMath = getSplittableDisplayMath(tex)
        if (!compactMath) {
            return
        }

        const rows = splitCompactFormulaRows(compactMath)
        if (rows.length < 2) {
            return
        }

        const list = document.createElement('span')
        list.className = 'split-display-math'

        rows.forEach((row) => {
            const rowElement = document.createElement('span')
            rowElement.className = 'split-display-math-row'
            rowElement.innerHTML = katex.renderToString(row, {
                displayMode: true,
                throwOnError: false,
                trust: false,
                strict: 'warn',
            })
            list.appendChild(rowElement)
        })

        displayMath.replaceWith(list)
    })
}

function isMathNode(node: ChildNode) {
    if (!(node instanceof HTMLElement)) {
        return false
    }

    return node.classList.contains('katex-display') || node.querySelector('.katex') !== null
}

function isFormulaRowList(nodes: ChildNode[]) {
    const formulaNodes = nodes.filter((node) => isMathNode(node) && isFormulaLikeMathNode(node))
    if (formulaNodes.length < 2) {
        return false
    }

    return nodes.every((node) => {
        if (isMathNode(node)) {
            return isFormulaLikeMathNode(node)
        }

        return isFormulaRowLabelText(node.textContent ?? '')
    })
}

function isFormulaLikeMathNode(node: ChildNode) {
    const tex = getMathTex(node)
    return /[=<>]|\\(?:approx|equiv|geq?|leq?|propto|sim|to|rightarrow|leftarrow)\b/.test(tex)
}

function isFormulaRowLabelText(text: string) {
    const compact = text.replace(/\s+/g, '')
    if (!compact) {
        return true
    }

    return compact
        .replace(/(?:（[^（）]*）|\([^()]*\)|【[^【】]*】|\[[^\[\]]*\]|[,，;；、。:：])/g, '')
        .length === 0
}

function getMathTex(node: ChildNode) {
    if (!(node instanceof HTMLElement)) {
        return ''
    }

    return node.querySelector('annotation[encoding="application/x-tex"]')?.textContent?.trim() ?? ''
}

function getSplittableDisplayMath(math: string) {
    if (!/\\begin\{/.test(math)) {
        return unwrapMultilineMath(math)
    }

    const unwrapped = unwrapSingleLineFormulaEnvironment(math)
    return unwrapped ? unwrapMultilineMath(unwrapped) : ''
}

function unwrapMultilineMath(math: string) {
    return math
        .replace(/^\\begin\{gathered\}\s*/u, '')
        .replace(/\s*\\end\{gathered\}$/u, '')
        .replace(/\s*\\\\\s*/g, ' ')
}

function unwrapSingleLineFormulaEnvironment(math: string) {
    const trimmed = math.trim()
    const match = trimmed.match(/^\\begin\{(aligned|gathered)\}\s*([\s\S]*?)\s*\\end\{\1\}$/u)
    if (!match || hasLatexRowSeparator(match[2])) {
        return ''
    }

    return match[2].replace(/(^|[^\\])&/g, '$1 ')
}

function hasLatexRowSeparator(math: string) {
    return /(?:^|[^\\])(?:\\\\|\\cr)(?:\s|$|\[)/u.test(math)
}

function hasLatexColumnSeparator(math: string) {
    return /(^|[^\\])&/u.test(math)
}

function normalizeMathText(text: string) {
    return text
        .replace(/\$\$([\s\S]+?)\$\$/g, (_, math: string) => {
            return `$$${normalizeMultilineDisplayMathBody(normalizeLatexTypos(math))}$$`
        })
        .replace(/\\\[([\s\S]+?)\\\]/g, (_, math: string) => {
            return `\\[${normalizeMultilineDisplayMathBody(normalizeLatexTypos(math))}\\]`
        })
        .replace(/\\\(([\s\S]+?)\\\)/g, (_, math: string) => {
            return `\\(${normalizeLatexTypos(math)}\\)`
        })
        .replace(/(^|[^$])\$([^$\n]+?)\$(?!\$)/g, (_, prefix: string, math: string) => {
            return `${prefix}$${normalizeLatexTypos(math)}$`
        })
}

function wrapBareLatexBlocks(text: string) {
    return text.replace(
        /(^|\n)([ \t]*\\begin\{(aligned|alignedat|gathered|cases|array|matrix|pmatrix|bmatrix|vmatrix|Vmatrix|split|align\*?|gather\*?|equation\*?)\}[\s\S]*?\\end\{\3\}[ \t]*)(?=\n|$)/g,
        (match: string, prefix: string, block: string, _environment: string, offset: number, source: string) => {
            const blockStart = offset + prefix.length
            const beforeBlock = source.slice(0, blockStart).trimEnd()
            const afterBlock = source.slice(offset + match.length).trimStart()

            if (isInsideDisplayMath(source, blockStart)
                || beforeBlock.endsWith('$$')
                || beforeBlock.endsWith('\\[')
                || afterBlock.startsWith('$$')
                || afterBlock.startsWith('\\]')) {
                return match
            }

            return `${prefix}$$\n${block.trim()}\n$$`
        },
    )
}

function isInsideDisplayMath(text: string, position: number) {
    let insideDollarDisplay = false
    let insideBracketDisplay = false
    for (let index = 0; index < position; index++) {
        if (text.startsWith('$$', index) && !isEscapedByBackslash(text, index)) {
            insideDollarDisplay = !insideDollarDisplay
            index++
            continue
        }
        if (text.startsWith('\\[', index) && !isEscapedByBackslash(text, index)) {
            insideBracketDisplay = true
            index++
            continue
        }
        if (text.startsWith('\\]', index) && !isEscapedByBackslash(text, index)) {
            insideBracketDisplay = false
            index++
        }
    }
    return insideDollarDisplay || insideBracketDisplay
}

function normalizeMultilineDisplayMathBody(math: string) {
    const compactRows = normalizeCompactFormulaEnvironment(math)
    if (compactRows !== math) {
        return compactRows
    }

    if (shouldKeepDisplayMathBody(math)) {
        return math
    }

    const explicitLines = math
        .trim()
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean)
    if (explicitLines.length < 2) {
        return math
    }

    return `\n${explicitLines.join(' \\\\\n')}\n`
}

function normalizeCompactFormulaEnvironment(math: string) {
    const unwrapped = unwrapSingleLineFormulaEnvironment(math)
    if (!unwrapped) {
        return math
    }

    const rows = splitCompactFormulaRows(unwrapped)
    if (rows.length < 2) {
        return math
    }

    return toGatheredMath(rows)
}

function toGatheredMath(rows: string[]) {
    return `\n\\begin{gathered}\n${rows.join(' \\\\\n')}\n\\end{gathered}\n`
}

function shouldKeepDisplayMathBody(math: string) {
    return hasLatexRowSeparator(math) || /\\begin\{/.test(math)
}

function splitCompactFormulaRows(math: string) {
    const normalized = math.trim().replace(/\s+/g, ' ')
    const numberedRows = splitNumberedFormulaRows(normalized)
    if (numberedRows.length > 1) {
        return numberedRows
    }

    return normalized
        .split(/\s+(?=\\nabla\s*(?:\\cdot|\\times))/)
        .map((line) => line.trim())
        .filter(Boolean)
}

function splitNumberedFormulaRows(math: string) {
    const markerPattern = new RegExp(numberedFormulaMarker, 'g')
    if ((math.match(markerPattern) ?? []).length < 2) {
        return []
    }

    return math
        .split(new RegExp(`\\s*(?=${numberedFormulaMarker})`, 'g'))
        .map(stripTrailingMathSpacing)
        .filter(Boolean)
}

function stripTrailingMathSpacing(line: string) {
    return line.replace(/(?:\\(?:quad|qquad|,|;|:|!)\s*)+$/g, '').trim()
}

function isFencedCodeBlock(content: string) {
    return content.startsWith('```') || content.startsWith('~~~')
}

function normalizeLatexTypos(math: string) {
    return normalizeInlineMultilineMath(normalizeLooseMatrixRows(math.replace(/\\partial(?=([A-Za-z]))([A-Za-z])/g, '\\partial $2')))
}

function normalizeLooseMatrixRows(math: string) {
    return math.replace(
        /\\begin\{(matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|smallmatrix)\}([\s\S]*?)\\end\{\1\}/g,
        (match: string, environment: string, body: string) => {
            const normalizedBody = normalizeLooseMatrixBody(body)
            return normalizedBody === body ? match : `\\begin{${environment}}${normalizedBody}\\end{${environment}}`
        },
    )
}

function normalizeLooseMatrixBody(body: string) {
    const trimmed = body.trim()
    if (!trimmed) {
        return body
    }
    if (hasLatexColumnSeparator(trimmed) && !hasLatexRowSeparator(trimmed)) {
        const repaired = repairDamagedMatrixRowSeparators(trimmed)
        if (repaired !== trimmed) {
            return `\n${repaired}\n`
        }
    }
    if (hasLatexColumnSeparator(trimmed) || hasLatexRowSeparator(trimmed)) {
        return body
    }

    const explicitRows = trimmed
        .split(/\r?\n/)
        .map((line) => splitLooseMatrixCells(line))
        .filter((cells) => cells.length > 0)
    if (explicitRows.length >= 2 && hasConsistentMatrixWidth(explicitRows)) {
        return formatLooseMatrixRows(explicitRows)
    }

    const cells = splitLooseMatrixCells(trimmed)
    const inferredWidth = Math.sqrt(cells.length)
    if (cells.length >= 4 && Number.isInteger(inferredWidth)) {
        const rows: string[][] = []
        for (let index = 0; index < cells.length; index += inferredWidth) {
            rows.push(cells.slice(index, index + inferredWidth))
        }
        return formatLooseMatrixRows(rows)
    }

    return body
}

function splitLooseMatrixCells(row: string) {
    const cells = row.trim().split(/\s+/).filter(Boolean)
    return cells.length > 0 && cells.every(isSimpleLooseMatrixCell) ? cells : []
}

function isSimpleLooseMatrixCell(cell: string) {
    return /^[-+]?(?:\d+(?:\.\d+)?|[A-Za-z]|\\[A-Za-z]+)(?:[_^](?:\{[-+]?[A-Za-z0-9]+\}|[-+]?[A-Za-z0-9]))*$/u.test(cell)
}

function hasConsistentMatrixWidth(rows: string[][]) {
    const width = rows[0]?.length ?? 0
    return width >= 2 && rows.every((row) => row.length === width)
}

function formatLooseMatrixRows(rows: string[][]) {
    return `\n${rows.map((row) => row.join(' & ')).join(' \\\\\n')}\n`
}

function repairDamagedMatrixRowSeparators(body: string) {
    const rows = body
        .split(/\s\\\s+(?=[^\\]*&)/)
        .map((row) => row.trim())
        .filter(Boolean)
    return rows.length > 1 ? rows.join(' \\\\\n') : body
}

function normalizeInlineMultilineMath(math: string) {
    const trimmed = math.trim()
    if (!hasLatexRowSeparator(trimmed) || /\\begin\{/.test(trimmed)) {
        return math
    }

    const bracedRows = trimmed.match(/^\\\{([\s\S]*?)\\\}$/u)
    if (bracedRows) {
        return `\\left\\{\\begin{array}{l}${bracedRows[1]}\\end{array}\\right.`
    }

    return `\\begin{gathered}${trimmed}\\end{gathered}`
}
