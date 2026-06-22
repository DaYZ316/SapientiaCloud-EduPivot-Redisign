import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'

import AiMarkdownMessage from '@/features/ai/components/AiMarkdownMessage.vue'
import RichMathContent from '@/shared/components/RichMathContent.vue'
import {renderAiMarkdown} from '@/features/ai/components/aiMarkdownRenderer'

async function mountRenderedMessage(content: string) {
  const wrapper = mount(AiMarkdownMessage, {
    props: {
      content,
    },
  })
  await wrapper.vm.$nextTick()
  return wrapper
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
