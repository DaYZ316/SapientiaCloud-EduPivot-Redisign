<template>
  <div ref="root" :class="{ open: isOpen, disabled: disabled }" :style="selectStyle" class="base-select">
    <button
        :aria-expanded="isOpen"
        aria-haspopup="listbox"
        class="base-select-trigger"
        role="combobox"
        type="button"
        @click="toggleOpen"
        @keydown="handleTriggerKeydown"
    >
      <span>{{ selectedOption?.label || placeholder }}</span>
      <ChevronDown :size="16" class="base-select-icon" stroke-width="1.8"/>
    </button>

    <Transition name="base-select-menu">
      <div v-if="isOpen" ref="menu" :style="menuStyle" class="base-select-menu" role="listbox">
        <button
            v-for="option in options"
            :key="optionKey(option)"
            :aria-selected="isSelected(option.value)"
            :class="{ selected: isSelected(option.value) }"
            class="base-select-option"
            role="option"
            type="button"
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
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import {Check, ChevronDown} from 'lucide-vue-next'

type SelectValue = string | number | undefined

interface SelectOption {
  label: string
  value: SelectValue
}

const props = withDefaults(
    defineProps<{
      modelValue: SelectValue
      options: SelectOption[]
      placeholder?: string
      minWidth?: string
      disabled?: boolean
    }>(),
    {
      disabled: false,
    },
)

const emit = defineEmits<{
  'update:modelValue': [value: SelectValue]
  change: []
}>()

const root = ref<HTMLElement | null>(null)
const menu = ref<HTMLElement | null>(null)
const isOpen = ref(false)
const menuStyle = ref<Record<string, string>>({})

const selectedOption = computed(() => props.options.find((option) => option.value === props.modelValue))
const selectStyle = computed(() => (props.minWidth ? {minWidth: props.minWidth} : undefined))

function isSelected(value: SelectValue) {
  return value === props.modelValue
}

function optionKey(option: SelectOption) {
  return `${option.value ?? 'undefined'}-${option.label}`
}

function toggleOpen() {
  if (props.disabled) return
  if (isOpen.value) {
    closeMenu()
    return
  }
  openMenu()
}

function openMenu() {
  if (props.disabled) return
  isOpen.value = true
}

function closeMenu() {
  isOpen.value = false
}

function handleTriggerKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    openMenu()
    return
  }

  if (event.key === 'Enter') {
    event.preventDefault()
    toggleOpen()
    return
  }

  if (event.key === 'Escape') {
    event.stopPropagation()
    closeMenu()
  }
}

function selectOption(value: SelectValue) {
  if (value !== props.modelValue) {
    emit('update:modelValue', value)
    emit('change')
  }
  closeMenu()
}

function handleClickOutside(event: MouseEvent) {
  const target = event.target
  if (target instanceof Node && root.value && !root.value.contains(target) && !menu.value?.contains(target)) {
    closeMenu()
  }
}

function updateMenuPosition() {
  const rootElement = root.value
  if (!rootElement) return

  const rect = rootElement.getBoundingClientRect()
  const viewportWidth = document.documentElement.clientWidth
  const viewportHeight = document.documentElement.clientHeight
  const viewportPadding = 16
  const menuOffset = 8
  const maxWidth = Math.max(0, viewportWidth - viewportPadding * 2)
  const minWidth = Math.min(rect.width, maxWidth)
  const menuWidth = Math.min(Math.max(menu.value?.offsetWidth ?? 0, minWidth), maxWidth)
  const left = Math.min(Math.max(rect.left, viewportPadding), viewportWidth - viewportPadding - menuWidth)
  const menuHeight = menu.value?.offsetHeight ?? 0
  const spaceBelow = viewportHeight - rect.bottom - menuOffset - viewportPadding
  const spaceAbove = rect.top - menuOffset - viewportPadding
  const openAbove = menuHeight > spaceBelow && spaceAbove > spaceBelow
  const availableHeight = Math.max(140, openAbove ? spaceAbove : spaceBelow)
  const top = openAbove
      ? Math.max(viewportPadding, rect.top - menuOffset - Math.min(menuHeight, availableHeight))
      : rect.bottom + menuOffset

  menuStyle.value = {
    position: 'fixed',
    top: `${top}px`,
    left: `${left}px`,
    right: 'auto',
    minWidth: `${minWidth}px`,
    maxWidth: `${maxWidth}px`,
    maxHeight: `${availableHeight}px`,
  }
}

function addPositionListeners() {
  window.addEventListener('resize', updateMenuPosition)
  window.addEventListener('scroll', updateMenuPosition, true)
}

function removePositionListeners() {
  window.removeEventListener('resize', updateMenuPosition)
  window.removeEventListener('scroll', updateMenuPosition, true)
}

watch(isOpen, async (open) => {
  if (!open) {
    removePositionListeners()
    return
  }

  await nextTick()
  if (!isOpen.value) return
  updateMenuPosition()
  addPositionListeners()
})

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  removePositionListeners()
})

defineExpose({isOpen})
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
  position: relative;
  width: 100%;
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 0 38px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: border-color 0.2s,
  box-shadow 0.2s,
  background 0.2s;
}

.base-select-trigger span {
  min-width: 0;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  position: absolute;
  right: 14px;
  flex-shrink: 0;
  color: var(--color-on-surface);
  transition: transform 0.2s;
}

.base-select.open .base-select-icon {
  transform: rotate(180deg);
}

.base-select.disabled .base-select-trigger {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
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
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 10px 34px;
  background: transparent;
  border: none;
  border-radius: 10px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-on-surface);
  text-align: center;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.2s,
  color 0.2s;
}

.base-select-option:hover,
.base-select-option.selected {
  background: var(--color-surface-canvas);
}

.base-select-option.selected {
  font-weight: 400;
}

.base-select-option svg {
  position: absolute;
  right: 12px;
  flex-shrink: 0;
}

.base-select-menu-enter-active,
.base-select-menu-leave-active {
  transition: opacity 0.16s,
  transform 0.16s;
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
