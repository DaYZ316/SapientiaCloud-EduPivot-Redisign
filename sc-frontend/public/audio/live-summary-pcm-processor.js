const TARGET_SAMPLE_RATE = 16000
const MIN_OUTPUT_SAMPLES = 1024

class LiveSummaryPcmProcessor extends AudioWorkletProcessor {
  constructor() {
    super()
    this.cursor = 0
    this.pendingSamples = []
    this.ratio = sampleRate / TARGET_SAMPLE_RATE
    this.port.onmessage = (event) => {
      if (event.data?.type === 'flush') {
        this.flush()
      }
    }
  }

  process(inputs) {
    const input = inputs[0]?.[0]
    if (!input || input.length === 0) {
      return true
    }

    while (this.cursor < input.length) {
      this.pendingSamples.push(input[Math.floor(this.cursor)] || 0)
      this.cursor += this.ratio
    }
    this.cursor -= input.length

    if (this.pendingSamples.length >= MIN_OUTPUT_SAMPLES) {
      this.flush()
    }

    return true
  }

  flush() {
    if (this.pendingSamples.length === 0) {
      return
    }

    const buffer = new ArrayBuffer(this.pendingSamples.length * 2)
    const view = new DataView(buffer)
    for (let i = 0; i < this.pendingSamples.length; i += 1) {
      const sample = Math.max(-1, Math.min(1, this.pendingSamples[i] || 0))
      view.setInt16(i * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
    }
    this.pendingSamples = []
    this.port.postMessage(buffer, [buffer])
  }
}

registerProcessor('live-summary-pcm-processor', LiveSummaryPcmProcessor)
