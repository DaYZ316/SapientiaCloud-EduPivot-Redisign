const GLOBAL_TOOLTIP_ID = 'global-tooltip'
const TOOLTIP_READY_CLASS = 'global-tooltip-ready'
const TOOLTIP_MARGIN = 12
const TOOLTIP_GAP = 8

type TooltipPlacement = 'top' | 'top-end' | 'right' | 'bottom' | 'left'

type ActiveTooltipState = {
    element: Element
    hadDataTooltip: boolean
    nativeTitle: string | null
    previousAriaDescribedBy: string | null
    autoDataTooltip: boolean
}

let tooltipElement: HTMLDivElement | null = null
let activeState: ActiveTooltipState | null = null
let activeAttributeObserver: MutationObserver | null = null
let isInstalled = false

export function installGlobalTooltip() {
    if (isInstalled || typeof document === 'undefined') return

    isInstalled = true
    document.documentElement.classList.add(TOOLTIP_READY_CLASS)
    tooltipElement = ensureTooltipElement()

    document.addEventListener('mouseover', handleMouseOver, true)
    document.addEventListener('mouseout', handleMouseOut, true)
    document.addEventListener('focusin', handleFocusIn, true)
    document.addEventListener('focusout', handleFocusOut, true)
    document.addEventListener('keydown', handleKeyDown, true)
    document.addEventListener('pointerdown', hideTooltip, true)
    window.addEventListener('resize', repositionActiveTooltip)
    window.addEventListener('scroll', repositionActiveTooltip, true)
}

function ensureTooltipElement() {
    const existingTooltip = document.getElementById(GLOBAL_TOOLTIP_ID)

    if (existingTooltip instanceof HTMLDivElement) {
        return existingTooltip
    }

    const tooltip = document.createElement('div')
    tooltip.id = GLOBAL_TOOLTIP_ID
    tooltip.className = 'global-tooltip'
    tooltip.setAttribute('role', 'tooltip')
    tooltip.hidden = true
    document.body.append(tooltip)

    return tooltip
}

function handleMouseOver(event: MouseEvent) {
    const target = findTooltipTarget(event.target)
    if (!target || activeState?.element === target) return

    showTooltip(target)
}

function handleMouseOut(event: MouseEvent) {
    if (!activeState) return

    const relatedTarget = event.relatedTarget
    if (relatedTarget instanceof Node && activeState.element.contains(relatedTarget)) return

    hideTooltip()
}

function handleFocusIn(event: FocusEvent) {
    const target = findTooltipTarget(event.target)
    if (!target) return

    showTooltip(target)
}

function handleFocusOut(event: FocusEvent) {
    if (!activeState) return

    const relatedTarget = event.relatedTarget
    if (relatedTarget instanceof Node && activeState.element.contains(relatedTarget)) return

    hideTooltip()
}

function handleKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
        hideTooltip()
    }
}

function findTooltipTarget(target: EventTarget | null) {
    if (!(target instanceof Element)) return null

    return target.closest('[data-tooltip], [title]')
}

function showTooltip(element: Element) {
    hideTooltip()

    const nativeTitle = element.getAttribute('title')
    const hadDataTooltip = element.hasAttribute('data-tooltip')

    activeState = {
        element,
        hadDataTooltip,
        nativeTitle,
        previousAriaDescribedBy: element.getAttribute('aria-describedby'),
        autoDataTooltip: Boolean(nativeTitle && !hadDataTooltip),
    }

    if (nativeTitle) {
        if (!hadDataTooltip) {
            element.setAttribute('data-tooltip', nativeTitle)
        }
        element.removeAttribute('title')
    }

    const tooltipText = getTooltipText()
    if (!tooltipText) {
        hideTooltip()
        return
    }

    setAriaDescribedBy(element)
    observeActiveElement(element)
    renderTooltip(tooltipText)
}

function hideTooltip() {
    if (!activeState) return

    const {element, nativeTitle, previousAriaDescribedBy, autoDataTooltip} = activeState

    activeAttributeObserver?.disconnect()
    activeAttributeObserver = null

    if (nativeTitle) {
        element.setAttribute('title', nativeTitle)
    }

    if (autoDataTooltip) {
        element.removeAttribute('data-tooltip')
    }

    if (previousAriaDescribedBy) {
        element.setAttribute('aria-describedby', previousAriaDescribedBy)
    } else {
        element.removeAttribute('aria-describedby')
    }

    activeState = null
    hideTooltipElement()
}

function observeActiveElement(element: Element) {
    activeAttributeObserver?.disconnect()
    activeAttributeObserver = new MutationObserver(() => {
        if (!activeState || activeState.element !== element) return

        const nativeTitle = element.getAttribute('title')
        if (nativeTitle) {
            activeState.nativeTitle = nativeTitle

            if (!activeState.hadDataTooltip) {
                activeState.autoDataTooltip = true
                element.setAttribute('data-tooltip', nativeTitle)
            }

            element.removeAttribute('title')
        }

        const tooltipText = getTooltipText()
        if (!tooltipText) {
            hideTooltip()
            return
        }

        renderTooltip(tooltipText)
    })
    activeAttributeObserver.observe(element, {
        attributeFilter: ['data-tooltip', 'data-tooltip-placement', 'title'],
        attributes: true,
    })
}

function getTooltipText() {
    if (!activeState) return ''

    return activeState.element.getAttribute('data-tooltip') || activeState.nativeTitle || ''
}

function renderTooltip(text: string) {
    const tooltip = tooltipElement
    if (!tooltip || !activeState) return

    tooltip.textContent = text
    tooltip.hidden = false
    tooltip.classList.add('is-visible')
    positionTooltip(activeState.element, tooltip)
}

function hideTooltipElement() {
    if (!tooltipElement) return

    tooltipElement.classList.remove('is-visible')
    tooltipElement.hidden = true
    tooltipElement.textContent = ''
}

function repositionActiveTooltip() {
    if (!activeState || !tooltipElement || tooltipElement.hidden) return

    positionTooltip(activeState.element, tooltipElement)
}

function positionTooltip(element: Element, tooltip: HTMLDivElement) {
    const targetRect = element.getBoundingClientRect()
    const tooltipRect = tooltip.getBoundingClientRect()
    const placement = getPlacement(element)
    const coordinates = getTooltipCoordinates(targetRect, tooltipRect, placement)

    tooltip.style.left = `${coordinates.left}px`
    tooltip.style.top = `${coordinates.top}px`
}

function getPlacement(element: Element): TooltipPlacement {
    const placement = element.getAttribute('data-tooltip-placement')

    if (
        placement === 'top-end' ||
        placement === 'right' ||
        placement === 'bottom' ||
        placement === 'left'
    ) {
        return placement
    }

    return 'top'
}

function getTooltipCoordinates(targetRect: DOMRect, tooltipRect: DOMRect, placement: TooltipPlacement) {
    const viewportWidth = window.innerWidth
    const viewportHeight = window.innerHeight
    let left = targetRect.left + (targetRect.width - tooltipRect.width) / 2
    let top = targetRect.top - tooltipRect.height - TOOLTIP_GAP

    if (placement === 'top-end') {
        left = targetRect.right - tooltipRect.width
    }

    if (placement === 'right') {
        left = targetRect.right + TOOLTIP_GAP
        top = targetRect.top + (targetRect.height - tooltipRect.height) / 2

        if (left + tooltipRect.width > viewportWidth - TOOLTIP_MARGIN) {
            left = targetRect.left - tooltipRect.width - TOOLTIP_GAP
        }
    }

    if (placement === 'bottom') {
        top = targetRect.bottom + TOOLTIP_GAP
    }

    if (placement === 'left') {
        left = targetRect.left - tooltipRect.width - TOOLTIP_GAP
        top = targetRect.top + (targetRect.height - tooltipRect.height) / 2

        if (left < TOOLTIP_MARGIN) {
            left = targetRect.right + TOOLTIP_GAP
        }
    }

    if ((placement === 'top' || placement === 'top-end') && top < TOOLTIP_MARGIN) {
        top = targetRect.bottom + TOOLTIP_GAP
    }

    left = clamp(left, TOOLTIP_MARGIN, viewportWidth - tooltipRect.width - TOOLTIP_MARGIN)
    top = clamp(top, TOOLTIP_MARGIN, viewportHeight - tooltipRect.height - TOOLTIP_MARGIN)

    return {left, top}
}

function clamp(value: number, min: number, max: number) {
    if (max < min) return min

    return Math.min(Math.max(value, min), max)
}

function setAriaDescribedBy(element: Element) {
    const describedBy = element.getAttribute('aria-describedby')
    const ids = describedBy?.split(/\s+/).filter(Boolean) ?? []

    if (!ids.includes(GLOBAL_TOOLTIP_ID)) {
        element.setAttribute('aria-describedby', [...ids, GLOBAL_TOOLTIP_ID].join(' '))
    }
}
