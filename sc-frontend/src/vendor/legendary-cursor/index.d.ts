declare const LegendaryCursor: {
    init(args?: {
        lineExpFactor?: number
        speedExpFactor?: number
        lineSize?: number
        opacityDecrement?: number
        sparklesCount?: number
        maxOpacity?: number
        pixelRatio?: number
        autoPilot?: boolean
        autoPilotCenter?: { x: number; y: number }
        autoPilotRadius?: number
        autoPilotSpeed?: number
        zIndex?: number
        texture1?: string
        texture2?: string
        texture3?: string
    }): void
    setAutoPilotCenter(center: { x: number; y: number }): void
    pause(): void
    destroy(): void
}

export default LegendaryCursor
