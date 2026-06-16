<template>
  <BaseConfirmDialog
    :visible="state.visible"
    :title="state.title"
    :message="state.message"
    :confirm-text="state.confirmText"
    :cancel-text="state.cancelText"
    :confirm-variant="state.confirmVariant"
    :close-label="state.cancelText"
    @confirm="confirm"
    @cancel="cancel"
  />
</template>

<script lang="ts" setup>
import BaseConfirmDialog from '@/shared/components/BaseConfirmDialog.vue'
import {useConfirmDialog} from '@/shared/composables/useConfirmDialog'

const {state, confirm, cancel} = useConfirmDialog()
</script>
