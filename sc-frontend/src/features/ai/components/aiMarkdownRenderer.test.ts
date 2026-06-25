import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'

import AiMarkdownMessage from '@/features/ai/components/AiMarkdownMessage.vue'
import RichMathContent from '@/shared/components/RichMathContent.vue'
import {renderAiMarkdown} from '@/features/ai/components/aiMarkdownRenderer'
import {fitRichMathToContainer} from '@/shared/utils/richMathRenderer'

async function mountRenderedMessage(content: string) {
  const wrapper = mount(AiMarkdownMessage, {
    props: {
      content,
    },
  })
  await wrapper.vm.$nextTick()
  return wrapper
}

function renderedMathRows(wrapper: Awaited<ReturnType<typeof mountRenderedMessage>>) {
  return (wrapper.html().match(/<mtr\b/g) ?? []).length
}

describe('renderAiMarkdown', () => {
  it('renders markdown emphasis', () => {
    const html = renderAiMarkdown('这是 **重点**')

    expect(html).toContain('<strong>重点</strong>')
  })

  it('normalizes inline lists after a colon', () => {
    const html = renderAiMarkdown('Tell me: - teacher\n- student')

    expect(html).toContain('<p>Tell me:</p>')
    expect(html).toContain('<ul>')
    expect(html).toContain('<li>teacher</li>')
    expect(html).toContain('<li>student</li>')
  })

  it('does not normalize inline lists inside fenced code', () => {
    const html = renderAiMarkdown('```md\nA: - item\n```')

    expect(html).toContain('A: - item')
    expect(html).toContain('<code')
    expect(html).not.toContain('<li>item</li>')
  })

  it('renders inline math', async () => {
    const wrapper = await mountRenderedMessage('勾股定理是 $a^2+b^2=c^2$')

    expect(wrapper.find('.katex').exists()).toBe(true)
    expect(wrapper.text()).toContain('a')
    expect(wrapper.text()).toContain('c')
  })

  it('renders block math', async () => {
    const wrapper = await mountRenderedMessage('$$\nE = mc^2\n$$')

    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.text()).toContain('E')
  })

  it('renders escaped inline and display delimiters', async () => {
    const wrapper = await mountRenderedMessage('速度是 \\(v = \\frac{s}{t}\\)\n\n\\[\nF = ma\n\\]')

    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.findAll('.katex').length).toBeGreaterThanOrEqual(2)
    expect(wrapper.html()).toContain('mfrac')
  })

  it('keeps display dollar delimiters before inline dollar delimiters', async () => {
    const wrapper = await mountRenderedMessage('$$x+y=z$$，其中 $z$ 是结果。')

    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.findAll('.katex').length).toBeGreaterThanOrEqual(2)
    expect(wrapper.find('.katex-error').exists()).toBe(false)
  })

  it('renders block math when the opening delimiter shares the formula line', async () => {
    const wrapper = await mountRenderedMessage('$$\\frac{a+b}{c-d} \\quad \\text{和} \\quad x^{n+1} - y_{\\max}\n$$')

    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.html()).toContain('mfrac')
  })

  it('renders equation systems', async () => {
    const wrapper = await mountRenderedMessage('$$\n\\begin{cases}\n2x + y = 5 \\\\\nx - 3y = -1\n\\end{cases}\n$$')

    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.html()).toContain('mtable')
  })

  it('normalizes loose multiline matrix rows before rendering', async () => {
    const wrapper = await mountRenderedMessage('$$\n\\begin{bmatrix}\n1 0\n0 1\n\\end{bmatrix}\n$$')

    expect(wrapper.find('.katex-error').exists()).toBe(false)
    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.html()).toContain('mtable')
    expect(renderedMathRows(wrapper)).toBeGreaterThanOrEqual(2)
    const annotation = wrapper.find('annotation[encoding="application/x-tex"]').text()
    expect(annotation).toContain('1 & 0')
    expect(annotation).toContain('0 & 1')
  })

  it('infers square matrix rows from loose single-line matrix cells', async () => {
    const wrapper = await mountRenderedMessage('$$\\begin{bmatrix}1 0 0 1\\end{bmatrix}$$')

    expect(wrapper.find('.katex-error').exists()).toBe(false)
    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.html()).toContain('mtable')
    expect(renderedMathRows(wrapper)).toBeGreaterThanOrEqual(2)
    const annotation = wrapper.find('annotation[encoding="application/x-tex"]').text()
    expect(annotation).toContain('1 & 0')
    expect(annotation).toContain('0 & 1')
  })

  it('preserves markdown inline matrix row separators before KaTeX rendering', async () => {
    const wrapper = await mountRenderedMessage(
      '设矩阵 $A = \\begin{bmatrix} 1 & 2 & 3 \\\\ 2 & 4 & 6 \\\\ 1 & 0 & -1 \\end{bmatrix}$，则 $\\operatorname{rank}(A)$ 等于多少？',
    )

    expect(wrapper.find('.katex-error').exists()).toBe(false)
    expect(wrapper.findAll('.katex')).toHaveLength(2)
    expect(wrapper.html()).toContain('mtable')
    expect(renderedMathRows(wrapper)).toBeGreaterThanOrEqual(3)
    const annotation = wrapper.find('annotation[encoding="application/x-tex"]').text()
    expect(annotation).toContain('1 & 2 & 3 \\\\ 2 & 4 & 6')
    expect(annotation).toContain('2 & 4 & 6 \\\\ 1 & 0 & -1')
  })

  it('renders braced inline equation systems as vertical arrays', async () => {
    const wrapper = await mountRenderedMessage(
      '齐次线性方程组 $\\{c_1 + c_2 - c_3 = 0 \\\\ 2c_1 + 2c_2 - 2c_3 = 0\\}$ 的通解是什么？',
    )

    expect(wrapper.find('.katex-error').exists()).toBe(false)
    expect(wrapper.html()).toContain('mtable')
    expect(renderedMathRows(wrapper)).toBeGreaterThanOrEqual(2)
    const annotation = wrapper.find('annotation[encoding="application/x-tex"]').text()
    expect(annotation).toContain('\\left\\{\\begin{array}{l}')
    expect(annotation).toContain('c_1 + c_2 - c_3 = 0 \\\\ 2c_1 + 2c_2 - 2c_3 = 0')
  })

  it('keeps matrix row-reduction chains as one valid display formula', async () => {
    const content = '对 $A$ 进行**初等行变换**，化为行阶梯形（REF）：\n\n' +
        '$$\n' +
        'A = \n' +
        '\\begin{bmatrix}\n' +
        '1 & 2 & 3 \\\\\n' +
        '2 & 4 & 6 \\\\\n' +
        '1 & 0 & -1\n' +
        '\\end{bmatrix}\n' +
        '\\quad\n' +
        '\\overset{R_2 \\leftarrow R_2 - 2R_1}{\\longrightarrow}\n' +
        '\\quad\n' +
        '\\begin{bmatrix}\n' +
        '1 & 2 & 3 \\\\\n' +
        '0 & 0 & 0 \\\\\n' +
        '1 & 0 & -1\n' +
        '\\end{bmatrix}\n' +
        '\\quad\n' +
        '\\overset{R_3 \\leftarrow R_3 - R_1}{\\longrightarrow}\n' +
        '\\quad\n' +
        '\\begin{bmatrix}\n' +
        '1 & 2 & 3 \\\\\n' +
        '0 & 0 & 0 \\\\\n' +
        '0 & -2 & -4\n' +
        '\\end{bmatrix}\n' +
        '$$\n\n' +
        '交换 $R_2$ 与 $R_3$（不改变秩）：'
    const wrapper = await mountRenderedMessage(content)

    expect(wrapper.find('.katex-error').exists()).toBe(false)
    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.find('.split-display-math').exists()).toBe(false)
    expect(wrapper.html().match(/<mtable\b/g)).toHaveLength(3)
    expect(renderedMathRows(wrapper)).toBeGreaterThanOrEqual(9)
    expect(wrapper.find('.katex-display').text()).toContain('R2')
  })

  it('scales oversized display formulas to the available message width', async () => {
    const wrapper = await mountRenderedMessage(
      '$$det A = 1 \\cdot (4 \\cdot (-1) - 6 \\cdot 0) - 2 \\cdot (2 \\cdot (-1) - 6 \\cdot 1) + 3 \\cdot (2 \\cdot 0 - 4 \\cdot 1)$$',
    )
    const displayMath = wrapper.find('.katex-display').element as HTMLElement
    const katexNode = displayMath.querySelector('.katex') as HTMLElement

    Object.defineProperty(displayMath, 'clientWidth', {configurable: true, value: 240})
    Object.defineProperty(katexNode, 'scrollWidth', {configurable: true, value: 960})
    fitRichMathToContainer(wrapper.element as HTMLElement)

    expect(displayMath.classList.contains('rich-math-scaled')).toBe(true)
    expect(displayMath.style.getPropertyValue('--rich-math-scale')).toBe('0.2500')
  })

  it('renders multiline display math as separate visual rows', async () => {
    const wrapper = await mountRenderedMessage(
      '$$\n' +
        '\\nabla \\cdot \\mathbf{E}=\\frac{\\rho}{\\varepsilon_0} （高斯定律：电场与电荷关系）\n' +
        '\\nabla \\cdot \\mathbf{B}=0 （磁场无源：不存在磁单极）\n' +
        '\\nabla \\times \\mathbf{E}=-\\frac{\\partial \\mathbf{B}}{\\partial t} （法拉第电磁感应定律）\n' +
        '\\nabla \\times \\mathbf{B}=\\mu_0\\mathbf{J}+\\mu_0\\varepsilon_0\\frac{\\partial \\mathbf{E}}{\\partial t} （安培-麦克斯韦定律）\n' +
        '$$',
    )

    expect(wrapper.findAll('.split-display-math-row').length).toBeGreaterThanOrEqual(4)
    expect(wrapper.text()).toContain('高斯定律')
    expect(wrapper.text()).toContain('安培')
  })

  it('renders compact numbered display formulas as separate visual rows', async () => {
    const wrapper = await mountRenderedMessage(
      '$$ (1) \\nabla \\cdot \\mathbf{E}=\\frac{\\rho}{\\varepsilon_0} \\quad ' +
        '(2) \\nabla \\cdot \\mathbf{B}=0 \\quad ' +
        '(3) \\nabla \\times \\mathbf{E}=-\\frac{\\partial \\mathbf{B}}{\\partial t} \\quad ' +
        '(4) \\nabla \\times \\mathbf{B}=\\mu_0\\mathbf{J}+\\mu_0\\varepsilon_0\\frac{\\partial \\mathbf{E}}{\\partial t} $$',
    )

    expect(wrapper.findAll('.split-display-math-row')).toHaveLength(4)
    expect(wrapper.text()).toContain('(1)')
    expect(wrapper.text()).toContain('(4)')
  })

  it('renders bare aligned environments as display math', async () => {
    const wrapper = await mountRenderedMessage(
      '\\begin{aligned}\n' +
        '(1)\\quad & \\nabla \\cdot \\mathbf{E}=\\frac{\\rho}{\\varepsilon_0} \\\\\n' +
        '(2)\\quad & \\nabla \\cdot \\mathbf{B}=0 \\\\\n' +
        '(3)\\quad & \\nabla \\times \\mathbf{E}=-\\frac{\\partial \\mathbf{B}}{\\partial t} \\\\\n' +
        '(4)\\quad & \\nabla \\times \\mathbf{B}=\\mu_0\\mathbf{J}+\\mu_0\\varepsilon_0\\frac{\\partial \\mathbf{E}}{\\partial t}\n' +
        '\\end{aligned}',
    )

    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.html()).toContain('mtable')
    expect(wrapper.find('annotation[encoding="application/x-tex"]').text()).toContain('\\begin{aligned}')
  })

  it('keeps multiline aligned formulas with text labels valid', async () => {
    const wrapper = await mountRenderedMessage(
      '$$\n' +
        '\\begin{aligned}\n' +
        '&\\nabla \\cdot \\mathbf{E} = 0 &\\text{（高斯定律，真空无净电荷）} \\\\\n' +
        '&\\nabla \\cdot \\mathbf{B} = 0 &\\text{（高斯磁定律，磁单极子不存在）} \\\\\n' +
        '&\\nabla \\times \\mathbf{E} = -\\frac{\\partial \\mathbf{B}}{\\partial t} &\\text{（法拉第电磁感应定律）} \\\\\n' +
        '&\\nabla \\times \\mathbf{B} = \\mu_0 \\varepsilon_0 \\frac{\\partial \\mathbf{E}}{\\partial t} &\\text{（安培-麦克斯韦定律，含位移电流项）}\n' +
        '\\end{aligned}\n' +
        '$$',
    )

    expect(wrapper.find('.katex-error').exists()).toBe(false)
    expect(wrapper.html()).toContain('mtable')
    expect(wrapper.text()).toContain('高斯定律')
    expect(wrapper.text()).toContain('安培')
  })

  it('renders mhchem formulas', async () => {
    const wrapper = await mountRenderedMessage('$$\n\\ce{2H2 + O2 -> 2H2O}\n$$')

    expect(wrapper.find('.katex-display').exists()).toBe(true)
    expect(wrapper.text()).toContain('H')
  })

  it('normalizes partial derivatives glued to variables', async () => {
    const wrapper = await mountRenderedMessage('$\\nabla \\cdot \\mathbf{J} + \\frac{\\partial \\rho}{\\partialt} = 0$')

    expect(wrapper.find('.katex').exists()).toBe(true)
    expect(wrapper.text()).toContain('∂t')
    expect(wrapper.text()).not.toContain('\\partialt')
  })

  it('normalizes partial boundary notation glued to commands', async () => {
    const wrapper = await mountRenderedMessage('$\\oint_{\\partialV} \\mathbf{B} \\cdot d\\mathbf{A} = 0$')

    expect(wrapper.find('.katex').exists()).toBe(true)
    expect(wrapper.text()).toContain('∂V')
    expect(wrapper.text()).not.toContain('\\partialV')
  })

  it('keeps formulas inline when they are part of one sentence', async () => {
    const wrapper = await mountRenderedMessage('其中 $\\rho$ 是电荷体密度，$\\mathbf{J}$ 是电流密度。')

    expect(wrapper.find('.formula-row-list').exists()).toBe(false)
    expect(wrapper.findAll('.formula-row')).toHaveLength(0)
    expect(wrapper.findAll('.katex')).toHaveLength(2)
  })

  it('stacks multiple formulas in one paragraph into separate rows', async () => {
    const wrapper = await mountRenderedMessage(
      '$\\nabla \\cdot \\mathbf{E}=\\frac{\\rho}{\\varepsilon_0}$ （高斯定律） ' +
        '$\\nabla \\cdot \\mathbf{B}=0$ （磁场无源） ' +
        '$\\nabla \\times \\mathbf{E}=-\\frac{\\partial \\mathbf{B}}{\\partial t}$ （法拉第定律）',
    )

    expect(wrapper.find('.formula-row-list').exists()).toBe(true)
    expect(wrapper.findAll('.formula-row')).toHaveLength(3)
    expect(wrapper.findAll('.katex')).toHaveLength(3)
  })

  it('keeps equation snippets inline when joined by narrative words', async () => {
    const wrapper = await mountRenderedMessage('解为 $x=1$，且 $y=2$。')

    expect(wrapper.find('.formula-row-list').exists()).toBe(false)
    expect(wrapper.findAll('.formula-row')).toHaveLength(0)
    expect(wrapper.findAll('.katex')).toHaveLength(2)
  })

  it('splits single-line aligned formula lists into separate display rows', async () => {
    const wrapper = await mountRenderedMessage(
      '$$\\begin{aligned}' +
        '&\\nabla \\cdot \\mathbf{E}=0 &\\text{（高斯定律，真空无净电荷）}\\quad ' +
        '&\\nabla \\cdot \\mathbf{B}=0 &\\text{（高斯磁定律，磁单极子不存在）}\\quad ' +
        '&\\nabla \\times \\mathbf{E}=-\\frac{\\partial \\mathbf{B}}{\\partial t} &\\text{（法拉第定律）}' +
        '\\end{aligned}$$',
    )

    expect(wrapper.find('.katex-error').exists()).toBe(false)
    expect(wrapper.html()).toContain('mtable')
    expect(wrapper.text()).toContain('高斯定律')
    expect(wrapper.text()).toContain('法拉第')
  })

  it('sanitizes unsafe html', () => {
    const html = renderAiMarkdown('<img src=x onerror="alert(1)"><script>alert(1)</script>')

    expect(html).not.toContain('onerror')
    expect(html).not.toContain('<script')
  })

  it('keeps fenced latex examples as code while rendering formulas outside code', async () => {
    const wrapper = await mountRenderedMessage('```latex\n$$\nE=mc^2\n$$\n```\n\n效果：\n$$\nE=mc^2\n$$')

    expect(wrapper.find('code').text()).toContain('$$')
    expect(wrapper.find('code').find('.katex').exists()).toBe(false)
    expect(wrapper.find('.katex-display').exists()).toBe(true)
  })

  it('rerenders partial streaming content without throwing', async () => {
    await expect(mountRenderedMessage('推导：$a^2 +')).resolves.toBeTruthy()
    await expect(mountRenderedMessage('推导：$a^2 + b^2 = c^2$')).resolves.toBeTruthy()
  })

  it('updates rendered content when streamed content grows', async () => {
    const wrapper = mount(AiMarkdownMessage, {
      props: {
        content: '推导：$a^2 +',
      },
    })

    await wrapper.setProps({
      content: '推导：$a^2 + b^2 = c^2$',
    })

    expect(wrapper.find('.katex').exists()).toBe(true)
    expect(wrapper.findAll('.katex').length).toBe(1)
  })

  it('renders persisted question fields through the shared rich math component', async () => {
    const wrapper = mount(RichMathContent, {
      props: {
        content: '题干：$a^2+b^2=c^2$\n\n答案：$c=\\sqrt{a^2+b^2}$',
      },
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('.katex')).toHaveLength(2)
    expect(wrapper.text()).toContain('题干')
    expect(wrapper.text()).toContain('答案')
  })
})
