import DOMPurify from 'dompurify'
import {Marked} from 'marked'
import katex from 'katex'
import renderMathInElement from 'katex/contrib/auto-render'
import 'katex/contrib/mhchem'

const richMathMarked = new Marked()
const numberedFormulaMarker = String.raw`(?:\(\d+\)|\uFF08\d+\uFF09)`

richMathMarked.setOptions({
  async: false,
  breaks: false,
  gfm: true,
})

export function renderRichMathMarkdown(content: string) {
  const html = richMathMarked.parse(normalizeRichMathMarkdownSource(content)) as string
  return DOMPurify.sanitize(html, {
    USE_PROFILES: {
      html: true,
      mathMl: true,
    },
  })
}

function normalizeRichMathMarkdownSource(content: string) {
  return content
    .split(/(```[\s\S]*?```|~~~[\s\S]*?~~~)/g)
    .map((part) => isFencedCodeBlock(part) ? part : normalizeMarkdownStructure(preserveEscapedMathDelimiters(wrapBareLatexBlocks(part))))
    .join('')
}

function normalizeMarkdownStructure(text: string) {
  return text.replace(/([:：])\s+((?:[-*+]|\d+[.)])\s+)/g, '$1\n$2')
}

function preserveEscapedMathDelimiters(text: string) {
  return text.replace(/(^|[^\\])\\([\[\]\(\)])/g, '$1\\\\$2')
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
  return /(?:^|[^\\])(?:\\\\|\\)(?:\s|$|\[)/u.test(math)
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
