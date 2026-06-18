<template>
  <div aria-live="polite" class="course-entry-transition" role="status">
    <canvas ref="canvasRef" aria-hidden="true" class="shader-canvas"></canvas>
    <div class="loading-content">
      <div class="percentage">{{ Math.round(clampedProgress) }}%</div>
      <div class="progress-wrap">
        <div class="progress-track">
          <div class="progress-fill" :style="{width: `${clampedProgress}%`}"></div>
        </div>
        <div class="loading-label">{{ label }}</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'

const props = withDefaults(defineProps<{
  label?: string
  progress?: number
}>(), {
  label: 'LOADING CLASSROOM',
  progress: 0,
})

const clampedProgress = computed(() => {
  if (!Number.isFinite(props.progress)) {
    return 0
  }
  return Math.min(Math.max(props.progress, 0), 100)
})
const canvasRef = ref<HTMLCanvasElement | null>(null)
let animationFrame = 0
let resizeObserver: ResizeObserver | null = null
let removeMouseListener: (() => void) | null = null

const lightFragmentShaderSource = `
precision highp float;

varying vec2 v_texCoord;
uniform float u_time;
uniform vec2 u_resolution;
uniform vec2 u_mouse;

vec3 permute(vec3 x) { return mod(((x*34.0)+1.0)*x, 289.0); }
float snoise(vec2 v){
  const vec4 C = vec4(0.211324865405187, 0.366025403784439,
           -0.577350269189626, 0.024390243902439);
  vec2 i  = floor(v + dot(v, C.yy) );
  vec2 x0 = v -   i + dot(i, C.xx);
  vec2 i1;
  i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
  vec4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  vec3 p = permute( permute( i.y + vec3(0.0, i1.y, 1.0 ))
  + i.x + vec3(0.0, i1.x, 1.0 ));
  vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy),
    dot(x12.zw,x12.zw)), 0.0);
  m = m*m ;
  m = m*m ;
  vec3 x = 2.0 * fract(p * C.www) - 1.0;
  vec3 h = abs(x) - 0.5;
  vec3 ox = floor(x + 0.5);
  vec3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );
  vec3 g;
  g.x  = a0.x  * x0.x  + h.x  * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}

void main() {
    vec2 uv = v_texCoord;
    vec2 p = (uv * 2.0 - 1.0) * (u_resolution.xy / min(u_resolution.x, u_resolution.y));
    float paper = snoise(uv * 100.0) * 0.02;
    paper += snoise(uv * 200.0) * 0.01;
    float n1 = snoise(p * 0.8 + u_time * 0.2);
    float n2 = snoise(p * 1.5 - u_time * 0.1);
    float flow = smoothstep(-0.5, 0.5, n1 + n2 * 0.5);
    float dist = length(uv - 0.5);
    float vignette = smoothstep(0.8, 0.2, dist);
    vec3 colorPaper = vec3(0.976, 0.976, 0.976);
    vec3 colorInk = vec3(0.855, 0.855, 0.855);
    vec3 finalColor = mix(colorInk, colorPaper, flow + paper);
    finalColor *= vignette;
    finalColor *= vec3(1.0, 0.99, 0.96);
    gl_FragColor = vec4(finalColor, 1.0);
}`

const darkFragmentShaderSource = `
precision highp float;

uniform float u_time;
uniform vec2 u_resolution;
varying vec2 v_texCoord;

vec3 permute(vec3 x) { return mod(((x*34.0)+1.0)*x, 289.0); }
float snoise(vec2 v){
  const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
  vec2 i  = floor(v + dot(v, C.yy) );
  vec2 x0 = v -   i + dot(i, C.xx);
  vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
  vec4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  vec3 p = permute( permute( i.y + vec3(0.0, i1.y, 1.0 )) + i.x + vec3(0.0, i1.x, 1.0 ));
  vec3 m = max(0.5 - vec4(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw), 0.0), 0.0).xyz;
  m = m*m ; m = m*m ;
  vec3 x = 2.0 * fract(p * C.www) - 1.0;
  vec3 h = abs(x) - 0.5;
  vec3 ox = floor(x + 0.5);
  vec3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );
  vec3 g;
  g.x  = a0.x  * x0.x  + h.x  * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}

void main() {
    vec2 uv = v_texCoord;
    vec2 centered_uv = (uv - 0.5) * vec2(u_resolution.x/u_resolution.y, 1.0);
    float n = snoise(centered_uv * 3.0 + u_time * 0.2);
    float angle = n * 3.14159;
    vec2 flow = vec2(cos(angle), sin(angle));
    float detail = snoise(centered_uv * 10.0 + flow + u_time * 0.5);
    float mask = length(centered_uv);
    float vignette = smoothstep(0.5, 0.2, mask);
    float color_val = mix(0.05, 0.4, n * 0.5 + 0.5);
    color_val += detail * 0.05;
    color_val *= vignette;
    float final = smoothstep(0.1, 0.8, color_val);
    gl_FragColor = vec4(vec3(final), 1.0);
}`

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return

  const syncSize = () => {
    const width = canvas.clientWidth || 1280
    const height = canvas.clientHeight || 720
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width
      canvas.height = height
    }
  }

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(syncSize)
    resizeObserver.observe(canvas)
  }
  syncSize()

  const gl = (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')) as WebGLRenderingContext | null
  if (!gl) return
  const webgl = gl

  const vertexShaderSource = `
attribute vec2 a_position;
varying vec2 v_texCoord;
void main() {
  v_texCoord = a_position * 0.5 + 0.5;
  gl_Position = vec4(a_position, 0.0, 1.0);
}`

  const fragmentShaderSource = document.documentElement.dataset.theme === 'light'
      ? lightFragmentShaderSource
      : darkFragmentShaderSource

  function createShader(type: number, source: string) {
    const shader = webgl.createShader(type)
    if (!shader) return null
    webgl.shaderSource(shader, source)
    webgl.compileShader(shader)
    return shader
  }

  const vertexShader = createShader(gl.VERTEX_SHADER, vertexShaderSource)
  const fragmentShader = createShader(gl.FRAGMENT_SHADER, fragmentShaderSource)
  const program = gl.createProgram()
  if (!vertexShader || !fragmentShader || !program) return

  gl.attachShader(program, vertexShader)
  gl.attachShader(program, fragmentShader)
  gl.linkProgram(program)
  gl.useProgram(program)

  const buffer = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW)

  const position = gl.getAttribLocation(program, 'a_position')
  gl.enableVertexAttribArray(position)
  gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0)

  const timeUniform = gl.getUniformLocation(program, 'u_time')
  const resolutionUniform = gl.getUniformLocation(program, 'u_resolution')
  const mouseUniform = gl.getUniformLocation(program, 'u_mouse')
  const mouse = {x: canvas.width / 2, y: canvas.height / 2}

  const handleMouseMove = (event: MouseEvent) => {
    const rect = canvas.getBoundingClientRect()
    if (!rect.width || !rect.height) return
    const normalizedX = (event.clientX - rect.left) / rect.width
    const normalizedY = 1 - (event.clientY - rect.top) / rect.height
    mouse.x = normalizedX * canvas.width
    mouse.y = normalizedY * canvas.height
  }

  window.addEventListener('mousemove', handleMouseMove)
  removeMouseListener = () => window.removeEventListener('mousemove', handleMouseMove)

  const render = (time: number) => {
    if (typeof ResizeObserver === 'undefined') syncSize()
    gl.viewport(0, 0, canvas.width, canvas.height)
    if (timeUniform) gl.uniform1f(timeUniform, time * 0.001)
    if (resolutionUniform) gl.uniform2f(resolutionUniform, canvas.width, canvas.height)
    if (mouseUniform) gl.uniform2f(mouseUniform, mouse.x, mouse.y)
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
    animationFrame = requestAnimationFrame(render)
  }

  animationFrame = requestAnimationFrame(render)
})

onBeforeUnmount(() => {
  if (animationFrame) {
    cancelAnimationFrame(animationFrame)
  }
  resizeObserver?.disconnect()
  removeMouseListener?.()
})
</script>

<style scoped>
.course-entry-transition {
  position: fixed;
  inset: 0;
  z-index: 5000;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: #000000;
  color: #ffffff;
}

.shader-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 1;
}

.loading-content {
  position: relative;
  z-index: 1;
  display: grid;
  width: min(100% - 48px, 384px);
  justify-items: center;
}

.percentage {
  margin-bottom: 32px;
  color: #ffffff;
  font-family: var(--font-heading);
  font-size: clamp(56px, 12vw, 72px);
  font-weight: 700;
  line-height: 1.1;
}

.progress-wrap {
  display: grid;
  width: 100%;
  gap: 16px;
  justify-items: center;
}

.progress-track {
  position: relative;
  width: 100%;
  height: 1px;
  overflow: hidden;
  background: rgb(255 255 255 / 20%);
}

.progress-fill {
  position: absolute;
  inset: 0 auto 0 0;
  width: 0;
  background: #ffffff;
  transition: width 160ms ease-out;
}

.loading-label {
  color: rgb(255 255 255 / 70%);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: 0;
  text-align: center;
}

:global(:root[data-theme='light']) .course-entry-transition {
  color: #000000;
}

:global(:root[data-theme='light'] .course-entry-transition .percentage) {
  color: #000000;
}

:global(:root[data-theme='light'] .course-entry-transition .progress-track) {
  background: #e2e2e2;
}

:global(:root[data-theme='light'] .course-entry-transition .progress-fill) {
  background: #000000;
}

:global(:root[data-theme='light'] .course-entry-transition .loading-label) {
  color: #000000;
}

@media (prefers-reduced-motion: reduce) {
  .progress-fill {
    transition-duration: 0ms;
  }
}
</style>
