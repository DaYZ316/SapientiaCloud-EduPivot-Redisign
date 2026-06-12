<template>
  <div ref="root" class="base-select" :class="{ open: isOpen }" :style="selectStyle">
    <button
      type="button"
      class="base-select-trigger"
      role="combobox"
      aria-haspopup="listbox"
      :aria-expanded="isOpen"
      @click="toggleOpen"
      @keydown.down.prevent="openMenu"
      @keydown.enter.prevent="toggleOpen"
      @keydown.escape.stop="isOpen = false"
    >
      <span>{{ selectedOption?.label || placeholder }}</span>
      <ChevronDown class="base-select-icon" :size="16" stroke-width="1.8"/>
    </button>

    <Transition name="base-select-menu">
      <div v-if="isOpen" class="base-select-menu" role="listbox">
        <button
          v-for="option in options"
          :key="optionKey(option)"
          type="button"
          class="base-select-option"
          :class="{ selected: isSelected(option.value) }"
          role="option"
          :aria-selected="isSelected(option.value)"
          @click="selectOption(option.value)"
        >
          <span>{{ option.label }}</span>
          <Check v-if="isSelected(option.value)" :size="15" stroke-width="2"/>
        </button>
      </div>
    </Transition>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {Check, ChevronDown} from 'lucide-vue-next'

type SelectValue = string | number | undefined

interface SelectOption {
  label: string
  value: SelectValue
}

const props = defineProps<{
  modelValue: SelectValue
  options: SelectOption[]
  placeholder?: string
  minWidth?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: SelectValue]
  change: []
}>()

const root = ref<HTMLElement | null>(null)
const isOpen = ref(false)

const selectedOption = computed(() => props.options.find((option) => option.value === props.modelValue))
const selectStyle = computed(() => props.minWidth ? {minWidth: props.minWidth} : undefined)

function isSelected(value: SelectValue) {
  return value === props.modelValue
}

function optionKey(option: SelectOption) {
  return `${option.value ?? 'undefined'}-${option.label}`
}

function toggleOpen() {
  isOpen.value = !isOpen.value
}

function openMenu() {
  isOpen.value = true
}

function selectOption(value: SelectValue) {
  if (value !== props.modelValue) {
    emit('update:modelValue', value)
    emit('change')
  }
  isOpen.value = false
}

function handleClickOutside(event: MouseEvent) {
  const target = event.target
  if (target instanceof Node && root.value && !root.value.contains(target)) {
    isOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.base-select {
  position: relative;
  min-width: 148px;
}

.base-select.open {
  z-index: 120;
}

.base-select-trigger {
  width: 100%;
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 0 14px 0 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.base-select-trigger:hover {
  border-color: var(--color-outline-variant);
  background: var(--color-surface-container);
}

.base-select-trigger:focus-visible,
.base-select.open .base-select-trigger {
  outline: none;
  border-color: var(--color-on-surface);
  box-shadow: 0 0 0 3px rgba(26, 28, 28, 0.08);
}

.base-select-icon {
  flex-shrink: 0;
  color: var(--color-on-surface);
  transition: transform 0.2s;
}

.base-select.open .base-select-icon {
  transform: rotate(180deg);
}

.base-select-menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  z-index: 120;
  min-width: max-content;
  padding: 6px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
  box-shadow: 0 18px 40px rgba(26, 28, 28, 0.12);
}

.base-select-option {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 10px 12px;
  background: transparent;
  border: none;
  border-radius: 10px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-on-surface);
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.base-select-option:hover,
.base-select-option.selected {
  background: var(--color-surface-canvas);
}

.base-select-option.selected {
  font-weight: 600;
}

.base-select-option svg {
  flex-shrink: 0;
}

.base-select-menu-enter-active,
.base-select-menu-leave-active {
  transition: opacity 0.16s, transform 0.16s;
}

.base-select-menu-enter-from,
.base-select-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 768px) {
  .base-select {
    width: 100%;
  }
}
</style>
