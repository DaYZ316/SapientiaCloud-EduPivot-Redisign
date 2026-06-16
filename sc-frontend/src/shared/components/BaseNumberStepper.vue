<template>
  <div class="base-number-stepper">
    <button
        :aria-label="decrementLabel"
        :disabled="disabled || isAtMin"
        class="base-number-stepper-button"
        type="button"
        @click="stepBy(-step)"
    >
      <span aria-hidden="true">−</span>
    </button>

    <input
        :disabled="disabled"
        :max="max"
        :min="min"
        :step="step"
        :value="modelValue"
        class="base-number-stepper-input"
        type="number"
        @blur="clampValue"
        @input="handleInput"
    />

    <button
        :aria-label="incrementLabel"
        :disabled="disabled || isAtMax"
        class="base-number-stepper-button"
        type="button"
        @click="stepBy(step)"
    >
      <span aria-hidden="true">+</span>
    </button>
  </div>
</template>

<script lang="ts" setup>
import {computed} from 'vue'

const props = withDefaults(defineProps<{
  modelValue: number
  min?: number
  max?: number
  step?: number
  disabled?: boolean
  decrementLabel?: string
  incrementLabel?: string
}>(), {
  min: 0,
  max: undefined,
  step: 1,
  disabled: false,
  decrementLabel: 'Decrease value',
  incrementLabel: 'Increase value',
})

const emit = defineEmits<{
  'update:modelValue': [value: number]
  change: [value: number]
}>()

const isAtMin = computed(() => props.modelValue <= props.min)
const isAtMax = computed(() => props.max != null && props.modelValue >= props.max)

function normalizeValue(value: number) {
  if (Number.isNaN(value)) {
    return props.min
  }

  const minBounded = Math.max(props.min, value)
  return props.max == null ? minBounded : Math.min(props.max, minBounded)
}

function updateValue(value: number) {
  const nextValue = normalizeValue(value)
  emit('update:modelValue', nextValue)
  emit('change', nextValue)
}

function stepBy(delta: number) {
  updateValue(props.modelValue + delta)
}

function handleInput(event: Event) {
  const input = event.target as HTMLInputElement
  updateValue(input.valueAsNumber)
}

function clampValue() {
  updateValue(props.modelValue)
}
</script>

<style scoped>
.base-number-stepper {
  width: 100%;
  min-height: 49px;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 28px;
  align-items: center;
  border-bottom: 1px solid var(--login-field-border);
  transition: border-color 0.2s;
}

.base-number-stepper:focus-within {
  border-bottom-color: var(--login-text);
}

.base-number-stepper-input {
  width: 100%;
  min-width: 0;
  padding: 12px 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  color: var(--login-text);
  font-family: var(--font-body);
  font-size: 16px;
  font-weight: 400;
  line-height: 1.5;
  outline: none;
  text-align: center;
}

.base-number-stepper-input::-webkit-inner-spin-button,
.base-number-stepper-input::-webkit-outer-spin-button {
  margin: 0;
  appearance: none;
}

.base-number-stepper-input[type='number'] {
  appearance: textfield;
}

.base-number-stepper-button {
  width: 28px;
  height: 49px;
  display: grid;
  place-items: center;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 20px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  transition: color 0.18s, transform 0.18s, opacity 0.18s;
}

.base-number-stepper-button:hover:not(:disabled) {
  color: var(--color-on-surface);
}

.base-number-stepper-button:active:not(:disabled) {
  transform: translateY(1px);
}

.base-number-stepper-button:focus-visible {
  outline: none;
  color: var(--color-on-surface);
  box-shadow: inset 0 -2px 0 var(--color-on-surface);
}

.base-number-stepper-button:disabled {
  cursor: not-allowed;
  opacity: 0.38;
}
</style>
