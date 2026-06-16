<template>
  <div ref="root" class="base-date-picker">
    <button
      :id="id"
      class="base-date-picker-trigger"
      :class="{ placeholder: !modelValue }"
      type="button"
      @click="openPicker"
      @keydown.escape="closePicker"
    >
      <span>{{ selectedDateDisplay || placeholderText }}</span>
      <CalendarDays :size="18" stroke-width="1.8" />
    </button>

    <div v-if="pickerOpen" ref="popover" class="base-date-picker-popover" :style="popoverStyle">
      <div class="base-date-picker-toolbar">
        <div class="base-date-picker-selects">
          <button
            class="base-date-picker-control base-date-picker-year-trigger"
            type="button"
            :aria-label="t('settings.selectYear')"
            :aria-expanded="activePanel === 'year'"
            @click="togglePanel('year')"
          >
            <span>{{ pickerYear }}</span>
            <ChevronDown :size="14" stroke-width="1.8" />
          </button>
          <button
            class="base-date-picker-control"
            type="button"
            :aria-label="t('settings.selectMonth')"
            :aria-expanded="activePanel === 'month'"
            @click="togglePanel('month')"
          >
            <span>{{ currentMonthLabel }}</span>
            <ChevronDown :size="14" stroke-width="1.8" />
          </button>
        </div>
        <div v-if="activePanel === 'calendar'" class="base-date-picker-nav">
          <button type="button" :aria-label="t('settings.previousMonth')" @click="moveMonth(-1)">
            <ChevronLeft :size="16" stroke-width="1.8" />
          </button>
          <button type="button" :aria-label="t('settings.nextMonth')" @click="moveMonth(1)">
            <ChevronRight :size="16" stroke-width="1.8" />
          </button>
        </div>
      </div>

      <div v-if="activePanel === 'year'" class="base-date-picker-year-panel">
        <div class="base-date-picker-year-toolbar">
          <button
            type="button"
            :aria-label="t('settings.previousYearGroup')"
            :disabled="!canMoveYearPageOlder"
            @click="moveYearPage(-1)"
          >
            <ChevronLeft :size="15" stroke-width="1.8" />
          </button>
          <span>{{ yearPageRange }}</span>
          <button
            type="button"
            :aria-label="t('settings.nextYearGroup')"
            :disabled="!canMoveYearPageNewer"
            @click="moveYearPage(1)"
          >
            <ChevronRight :size="15" stroke-width="1.8" />
          </button>
        </div>

        <div class="base-date-picker-year-grid">
          <button
            v-for="year in visibleYears"
            :key="year"
            class="base-date-picker-year"
            :class="{ selected: year === pickerYear }"
            type="button"
            @click="selectYear(year)"
          >
            {{ year }}
          </button>
        </div>
      </div>

      <div v-else-if="activePanel === 'month'" class="base-date-picker-month-grid">
        <button
          v-for="month in monthOptions"
          :key="month.value"
          class="base-date-picker-month"
          :class="{ selected: month.value === pickerMonthIndex }"
          type="button"
          @click="selectMonth(month.value)"
        >
          {{ month.label }}
        </button>
      </div>

      <template v-else>
        <div class="base-date-picker-weekdays">
          <span v-for="weekday in weekdayLabels" :key="weekday">{{ weekday }}</span>
        </div>

        <div class="base-date-picker-grid">
          <button
            v-for="day in calendarDays"
            :key="day.value"
            class="base-date-picker-day"
            :class="{ outside: !day.inCurrentMonth, selected: day.selected, today: day.today }"
            type="button"
            @click="selectDate(day.value)"
          >
            {{ day.label }}
          </button>
        </div>
      </template>

      <div v-if="showTime" class="base-date-picker-time">
        <Clock3 :size="16" stroke-width="1.8" />
        <input type="time" :step="timeStep" :value="selectedTime" :aria-label="timeLabel" @input="updateTime" />
      </div>

      <div class="base-date-picker-footer">
        <button type="button" @click="clearDate">{{ t('settings.clear') }}</button>
        <button type="button" @click="selectToday">{{ t('settings.today') }}</button>
        <button
          v-if="showTime"
          class="base-date-picker-confirm"
          type="button"
          :aria-label="confirmLabel"
          @click="closePicker"
        >
          <Check :size="15" stroke-width="2" />
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarDays, Check, ChevronDown, ChevronLeft, ChevronRight, Clock3 } from 'lucide-vue-next'

type DatePickerPanel = 'calendar' | 'year' | 'month'

const props = withDefaults(
  defineProps<{
    id?: string
    modelValue?: string | null
    placeholder?: string
    showTime?: boolean
    timeStep?: number
    defaultTime?: string
    timeLabel?: string
    confirmLabel?: string
  }>(),
  {
    modelValue: '',
    placeholder: '',
    showTime: false,
    timeStep: 300,
    defaultTime: '09:00',
    timeLabel: 'Time',
    confirmLabel: 'Close date picker',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const { t, locale } = useI18n()

const root = ref<HTMLElement | null>(null)
const popover = ref<HTMLElement | null>(null)
const pickerOpen = ref(false)
const popoverStyle = ref<Record<string, string>>({})
const activePanel = ref<DatePickerPanel>('calendar')
const pickerMonth = ref(startOfMonth(parseDateValue(props.modelValue) ?? new Date()))
const yearPageStart = ref(getYearPageStart(pickerMonth.value.getFullYear()))

const placeholderText = computed(() => props.placeholder || t('settings.datePlaceholder'))

const selectedDateDisplay = computed(() => {
  const date = parseDateValue(props.modelValue)
  if (!date) {
    return ''
  }

  const dateText = new Intl.DateTimeFormat(String(locale.value), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)

  const timeText = props.showTime ? parseTimeValue(props.modelValue) : ''

  return timeText ? `${dateText} ${timeText}` : dateText
})

const selectedTime = computed(() => parseTimeValue(props.modelValue) || normalizeTimeValue(props.defaultTime))
const selectedDateValue = computed(() => {
  const date = parseDateValue(props.modelValue)
  return date ? toDateValue(date) : ''
})

const visibleYears = computed(() =>
  Array.from({ length: 12 }, (_, index) => yearPageStart.value - index).filter((year) => year >= 1900),
)

const yearPageRange = computed(() => {
  const years = visibleYears.value

  return years.length ? `${years[years.length - 1]} - ${years[0]}` : ''
})

const canMoveYearPageOlder = computed(() => {
  const maxYear = getMaxYear()
  const minPageStart = 1900 + ((maxYear - 1900) % 12)

  return yearPageStart.value > minPageStart
})

const canMoveYearPageNewer = computed(() => yearPageStart.value < getMaxYear())

const monthOptions = computed(() => {
  const formatter = new Intl.DateTimeFormat(String(locale.value), { month: 'long' })

  return Array.from({ length: 12 }, (_, month) => ({
    value: month,
    label: formatter.format(new Date(2026, month, 1)),
  }))
})

const currentMonthLabel = computed(
  () => monthOptions.value.find((month) => month.value === pickerMonth.value.getMonth())?.label ?? '',
)

const pickerYear = computed(() => pickerMonth.value.getFullYear())
const pickerMonthIndex = computed(() => pickerMonth.value.getMonth())

const weekdayLabels = computed(() => {
  const formatter = new Intl.DateTimeFormat(String(locale.value), { weekday: 'short' })
  const sunday = new Date(2026, 5, 7)

  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(sunday)
    date.setDate(sunday.getDate() + index)
    return formatter.format(date)
  })
})

const calendarDays = computed(() => {
  const month = pickerMonth.value
  const firstDay = new Date(month.getFullYear(), month.getMonth(), 1)
  const gridStart = new Date(firstDay)
  gridStart.setDate(firstDay.getDate() - firstDay.getDay())
  const todayValue = toDateValue(new Date())

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart)
    date.setDate(gridStart.getDate() + index)
    const value = toDateValue(date)

    return {
      value,
      label: date.getDate(),
      inCurrentMonth: date.getMonth() === month.getMonth(),
      selected: selectedDateValue.value === value,
      today: todayValue === value,
    }
  })
})

watch(
  () => props.modelValue,
  () => {
    if (!pickerOpen.value) {
      syncPickerMonth()
    }
  },
)

function parseDateValue(value: string | null | undefined) {
  if (!value) {
    return null
  }

  const match = /^(\d{4})-(\d{2})-(\d{2})(?:T\d{2}:\d{2})?$/.exec(value)
  if (!match) {
    return null
  }

  const year = Number(match[1])
  const month = Number(match[2]) - 1
  const day = Number(match[3])
  const date = new Date(year, month, day)

  return date.getFullYear() === year && date.getMonth() === month && date.getDate() === day ? date : null
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function getMaxYear() {
  return Math.max(new Date().getFullYear(), pickerMonth.value.getFullYear())
}

function getYearPageStart(year: number) {
  const maxYear = getMaxYear()
  const safeYear = Math.min(Math.max(year, 1900), maxYear)

  return maxYear - Math.floor((maxYear - safeYear) / 12) * 12
}

function toDateValue(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function parseTimeValue(value: string | null | undefined) {
  if (!value) {
    return ''
  }

  const match = /T(\d{2}):(\d{2})/.exec(value)
  return match ? normalizeTimeValue(`${match[1]}:${match[2]}`) : ''
}

function normalizeTimeValue(value: string) {
  const match = /^(\d{2}):(\d{2})$/.exec(value)
  if (!match) {
    return '09:00'
  }

  const hour = Number(match[1])
  const minute = Number(match[2])
  if (hour > 23 || minute > 59) {
    return '09:00'
  }

  return `${match[1]}:${match[2]}`
}

function withTime(dateValue: string, timeValue = selectedTime.value) {
  return `${dateValue}T${timeValue}`
}

function syncPickerMonth() {
  pickerMonth.value = startOfMonth(parseDateValue(props.modelValue) ?? new Date())
  yearPageStart.value = getYearPageStart(pickerMonth.value.getFullYear())
  activePanel.value = 'calendar'
}

function openPicker() {
  syncPickerMonth()
  pickerOpen.value = true
}

function closePicker() {
  activePanel.value = 'calendar'
  pickerOpen.value = false
}

function togglePanel(panel: DatePickerPanel) {
  activePanel.value = activePanel.value === panel ? 'calendar' : panel
  if (activePanel.value === 'year') {
    yearPageStart.value = getYearPageStart(pickerMonth.value.getFullYear())
  }
}

function moveMonth(offset: number) {
  pickerMonth.value = new Date(pickerMonth.value.getFullYear(), pickerMonth.value.getMonth() + offset, 1)
  activePanel.value = 'calendar'
}

function moveYearPage(offset: number) {
  const maxYear = getMaxYear()
  const minPageStart = 1900 + ((maxYear - 1900) % 12)
  const nextPageStart = yearPageStart.value + offset * 12

  yearPageStart.value = Math.min(Math.max(nextPageStart, minPageStart), maxYear)
}

function selectYear(year: number) {
  pickerMonth.value = new Date(year, pickerMonth.value.getMonth(), 1)
}

function selectMonth(month: number) {
  pickerMonth.value = new Date(pickerMonth.value.getFullYear(), month, 1)
}

function selectDate(value: string) {
  emit('update:modelValue', props.showTime ? withTime(value) : value)
  if (!props.showTime) {
    closePicker()
  }
}

function clearDate() {
  emit('update:modelValue', '')
  closePicker()
}

function selectToday() {
  const today = new Date()
  const todayValue = toDateValue(today)
  emit('update:modelValue', props.showTime ? withTime(todayValue) : todayValue)
  pickerMonth.value = startOfMonth(today)
  closePicker()
}

function updateTime(event: Event) {
  const input = event.target as HTMLInputElement
  const dateValue = props.modelValue
    ? toDateValue(parseDateValue(props.modelValue) ?? new Date())
    : toDateValue(new Date())

  emit('update:modelValue', withTime(dateValue, normalizeTimeValue(input.value)))
}

function handleOutsideClick(event: MouseEvent) {
  const target = event.target
  if (target instanceof Node && root.value && !root.value.contains(target)) {
    closePicker()
  }
}

onMounted(() => {
  document.addEventListener('click', handleOutsideClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleOutsideClick)
  removePositionListeners()
})

function updatePopoverPosition() {
  const rootElement = root.value
  if (!rootElement) return

  const rect = rootElement.getBoundingClientRect()
  const viewportWidth = document.documentElement.clientWidth
  const viewportHeight = document.documentElement.clientHeight
  const viewportPadding = 16
  const popoverOffset = 8
  const popoverWidth = Math.min(318, viewportWidth - viewportPadding * 2)
  const left = Math.min(Math.max(rect.left, viewportPadding), viewportWidth - viewportPadding - popoverWidth)
  const popoverHeight = popover.value?.offsetHeight ?? 0
  const spaceBelow = viewportHeight - rect.bottom - popoverOffset - viewportPadding
  const spaceAbove = rect.top - popoverOffset - viewportPadding
  const openAbove = popoverHeight > spaceBelow && spaceAbove > spaceBelow
  const availableHeight = Math.max(220, openAbove ? spaceAbove : spaceBelow)
  const top = openAbove
    ? Math.max(viewportPadding, rect.top - popoverOffset - Math.min(popoverHeight, availableHeight))
    : rect.bottom + popoverOffset

  popoverStyle.value = {
    position: 'fixed',
    top: `${top}px`,
    left: `${left}px`,
    width: `${popoverWidth}px`,
    maxHeight: `${availableHeight}px`,
  }
}

function addPositionListeners() {
  window.addEventListener('resize', updatePopoverPosition)
  window.addEventListener('scroll', updatePopoverPosition, true)
}

function removePositionListeners() {
  window.removeEventListener('resize', updatePopoverPosition)
  window.removeEventListener('scroll', updatePopoverPosition, true)
}

watch(pickerOpen, async (open) => {
  if (!open) {
    removePositionListeners()
    return
  }

  await nextTick()
  if (!pickerOpen.value) return
  updatePopoverPosition()
  addPositionListeners()
})
</script>

<style scoped>
.base-date-picker {
  position: relative;
  width: 100%;
}

.base-date-picker-trigger {
  width: 100%;
  min-height: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--login-field-border);
  border-radius: 0;
  color: var(--login-text);
  font-family: var(--font-body);
  font-size: 16px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s;
}

.base-date-picker-trigger:hover {
  border-bottom-color: var(--login-text);
}

.base-date-picker-trigger:focus {
  outline: none;
  border-bottom-color: var(--login-text);
}

.base-date-picker-trigger.placeholder span {
  color: var(--login-muted);
}

.base-date-picker-trigger svg {
  flex-shrink: 0;
  color: var(--color-muted);
}

.base-date-picker-popover {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  z-index: 120;
  width: min(318px, calc(100vw - 48px));
  overflow-y: auto;
  padding: 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 22px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.22);
  color: var(--color-on-surface);
}

.base-date-picker-toolbar,
.base-date-picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.base-date-picker-selects {
  display: flex;
  flex: 1;
  min-width: 0;
  gap: 8px;
}

.base-date-picker-control {
  min-width: 0;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 10px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: 10px;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.base-date-picker-year-trigger {
  flex: 0 0 88px;
}

.base-date-picker-control:not(.base-date-picker-year-trigger) {
  flex: 1;
}

.base-date-picker-control svg {
  flex-shrink: 0;
  color: var(--color-muted);
}

.base-date-picker-control[aria-expanded='true'],
.base-date-picker-control:focus {
  outline: none;
  border-color: var(--color-on-surface);
}

.base-date-picker-nav {
  display: flex;
  gap: 6px;
}

.base-date-picker-nav button,
.base-date-picker-footer button,
.base-date-picker-year-toolbar button,
.base-date-picker-year,
.base-date-picker-month,
.base-date-picker-day {
  border: 0;
  background: transparent;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  cursor: pointer;
}

.base-date-picker-nav button {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: var(--color-muted);
  transition:
    background 0.2s,
    color 0.2s;
}

.base-date-picker-nav button:hover,
.base-date-picker-year-toolbar button:not(:disabled):hover,
.base-date-picker-year:hover,
.base-date-picker-month:hover,
.base-date-picker-day:hover,
.base-date-picker-footer button:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.base-date-picker-year-panel {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.base-date-picker-year-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.base-date-picker-year-toolbar span {
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  color: var(--color-muted);
}

.base-date-picker-year-toolbar button {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: var(--color-muted);
  transition:
    background 0.2s,
    color 0.2s,
    opacity 0.2s;
}

.base-date-picker-year-toolbar button:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.base-date-picker-year-grid,
.base-date-picker-month-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.base-date-picker-month-grid {
  margin-top: 14px;
}

.base-date-picker-year,
.base-date-picker-month {
  min-height: 36px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 800;
  transition:
    background 0.2s,
    color 0.2s;
}

.base-date-picker-year.selected,
.base-date-picker-month.selected,
.base-date-picker-day.selected {
  background: var(--color-primary);
  color: var(--color-on-primary);
  opacity: 1;
}

.base-date-picker-weekdays,
.base-date-picker-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
}

.base-date-picker-weekdays {
  margin-top: 14px;
  margin-bottom: 8px;
}

.base-date-picker-weekdays span {
  text-align: center;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  color: var(--color-muted);
}

.base-date-picker-day {
  min-height: 34px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 700;
  transition:
    background 0.2s,
    color 0.2s,
    opacity 0.2s;
}

.base-date-picker-day.outside {
  color: var(--color-muted);
  opacity: 0.55;
}

.base-date-picker-day.today:not(.selected) {
  box-shadow: inset 0 0 0 1px var(--color-outline-variant);
}

.base-date-picker-footer {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--color-outline-light);
}

.base-date-picker-time {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 0 12px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: 12px;
  color: var(--color-muted);
}

.base-date-picker-time svg {
  flex-shrink: 0;
}

.base-date-picker-time input {
  width: 100%;
  min-width: 0;
  background: transparent;
  border: 0;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 800;
  outline: none;
}

.base-date-picker-time input::-webkit-calendar-picker-indicator {
  cursor: pointer;
  filter: invert(1);
  opacity: 0.72;
}

.base-date-picker-footer button {
  min-height: 32px;
  padding: 0 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 800;
  color: var(--color-primary);
}

.base-date-picker-confirm {
  display: grid;
  place-items: center;
  margin-left: auto;
  color: var(--color-on-surface);
}
</style>
