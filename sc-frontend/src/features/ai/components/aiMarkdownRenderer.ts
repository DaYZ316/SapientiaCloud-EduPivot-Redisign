import DOMPurify from 'dompurify'
import {Marked} from 'marked'
import katex from 'katex'
import renderMathInElement from 'katex/contrib/auto-render'
import 'katex/contrib/mhchem'

const aiMarked = new Marked()
const numberedFormulaMarker = String.raw`(?:\(\d+\)|\uFF08\d+\uFF09)`

aiMarked.setOptions({
  async: false,
  breaks: false,
  gfm: true,
})

export function renderAiMarkdown(content: string) {
  const html = aiMarked.parse(normalizeAiMarkdownSource(content)) as string
  return DOMPurify.sanitize(html, {
    USE_PROFILES: {
      html: true,
      mathMl: true,
    },
  })
}

function normalizeAiMarkdownSource(content: string) {
  return content
    .split(/(```[\s\S]*?```|~~~[\s\S]*?~~~)/g)
    .map((part) => isFencedCodeBlock(part) ? part : normalizeMarkdownStructure(wrapBareLatexBlocks(part)))
    .join('')
}

function normalizeMarkdownStructure(text: string) {
  return text.replace(/([:：])\s+((?:[-*+]|\d+[.)])\s+)/g, '$1\n$2')
}

export function normalizeAiDisplayMath(element: HTMLElement) {
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

export function renderAiMath(element: HTMLElement) {
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

export function formatAiFormulaRows(element: HTMLElement) {
  splitRenderedDisplayMathRows(element)

  element.querySelectorAll('p').forEach((paragraph) => {
    if (!(paragraph instanceof HTMLElement) || paragraph.classList.contains('formula-row-list')) {
      return
    }

    const mathNodes = Array.from(paragraph.childNodes).filter(isMathNode)
    if (mathNodes.length < 2 || hasNarrativeTextBetweenMath(paragraph)) {
      return
    }

    const fragment = document.createDocumentFragment()
    let currentRow: HTMLSpanElement | null = null

    Array.from(paragraph.childNodes).forEach((node) => {
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

function splitRenderedDisplayMathRows(element: HTMLElement) {
  element.querySelectorAll('.katex-display').forEach((displayMath) => {
    if (!(displayMath instanceof HTMLElement) || displayMath.classList.contains('split-display-math')) {
      return
    }

    const tex = displayMath.querySelector('annotation[encoding="application/x-tex"]')?.textContent
    if (!tex || /\\begin\{/.test(tex)) {
      return
    }

    const rows = splitCompactFormulaRows(unwrapMultilineMath(tex))
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

function hasNarrativeTextBetweenMath(paragraph: HTMLElement) {
  return Array.from(paragraph.childNodes).some((node) => {
    if (isMathNode(node)) {
      return false
    }

    return (node.textContent ?? '').trim().length > 0
  })
}

function unwrapMultilineMath(math: string) {
  return math
    .replace(/^\\begin\{gathered\}\s*/u, '')
    .replace(/\s*\\end\{gathered\}$/u, '')
    .replace(/\s*\\\\\s*/g, ' ')
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
      const beforeBlock = source.slice(0, offset + prefix.length).trimEnd()
      const afterBlock = source.slice(offset + match.length).trimStart()

      if (beforeBlock.endsWith('$$') || beforeBlock.endsWith('\\[') || afterBlock.startsWith('$$') || afterBlock.startsWith('\\]')) {
        return match
      }

      return `${prefix}$$\n${block.trim()}\n$$`
    },
  )
}

function normalizeMultilineDisplayMathBody(math: string) {
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

function shouldKeepDisplayMathBody(math: string) {
  return /\\\\(?:\s|$|\[)/.test(math) || /\\begin\{/.test(math)
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
  return math.replace(/\\partial(?=([A-Za-z]))([A-Za-z])/g, '\\partial $2')
}
