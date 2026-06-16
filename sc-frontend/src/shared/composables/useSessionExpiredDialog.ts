import {readonly, ref} from 'vue'

const visible = ref(false)

export function showSessionExpiredDialog() {
    visible.value = true
}

export function closeSessionExpiredDialog() {
    visible.value = false
}

export function useSessionExpiredDialog() {
    return {
        visible: readonly(visible),
        show: showSessionExpiredDialog,
        close: closeSessionExpiredDialog,
    }
}
