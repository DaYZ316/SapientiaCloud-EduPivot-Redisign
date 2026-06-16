<template>
  <BaseConfirmDialog
      :cancel-text="state.cancelText"
      :close-label="state.cancelText"
      :confirm-text="state.confirmText"
      :confirm-variant="state.confirmVariant"
      :message="state.message"
      :title="state.title"
      :visible="state.visible"
      @cancel="cancel"
      @confirm="confirm"
  />
</template>

<script lang="ts" setup>
import BaseConfirmDialog from '@/shared/components/BaseConfirmDialog.vue'
import {useConfirmDialog} from '@/shared/composables/useConfirmDialog'

const {state, confirm, cancel} = useConfirmDialog()
</script>
