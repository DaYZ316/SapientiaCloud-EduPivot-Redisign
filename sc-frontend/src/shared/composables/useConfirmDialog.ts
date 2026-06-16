import {computed, readonly, ref} from 'vue'

import {i18n} from '@/app/i18n'

export interface ConfirmDialogOptions {
    title?: string
    message: string
    confirmText?: string
    cancelText?: string
    confirmVariant?: 'primary' | 'danger'
}

export interface ConfirmDialogState {
    visible: boolean
    title: string
    message: string
    confirmText: string
    cancelText: string
    confirmVariant: 'primary' | 'danger'
}

type ConfirmResolver = (confirmed: boolean) => void

const defaultState: ConfirmDialogState = {
    visible: false,
    title: '',
    message: '',
    confirmText: '',
    cancelText: '',
    confirmVariant: 'primary',
}

const state = ref<ConfirmDialogState>({...defaultState})
let resolver: ConfirmResolver | null = null

const normalizedState = computed<ConfirmDialogState>(() => ({
    ...state.value,
    title: state.value.title || i18n.global.t('common.confirmDialog.title'),
    confirmText: state.value.confirmText || i18n.global.t('common.confirmDialog.confirm'),
    cancelText: state.value.cancelText || i18n.global.t('common.confirmDialog.cancel'),
}))

function close(confirmed: boolean) {
    resolver?.(confirmed)
    resolver = null
    state.value = {...defaultState}
}

export function confirmDialog(options: ConfirmDialogOptions) {
    resolver?.(false)

    state.value = {
        visible: true,
        title: options.title ?? '',
        message: options.message,
        confirmText: options.confirmText ?? '',
        cancelText: options.cancelText ?? '',
        confirmVariant: options.confirmVariant ?? 'primary',
    }

    return new Promise<boolean>((resolve) => {
        resolver = resolve
    })
}

export function useConfirmDialog() {
    return {
        state: readonly(normalizedState),
        confirm: () => close(true),
        cancel: () => close(false),
    }
}
