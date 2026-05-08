<template>
  <NConfigProvider :theme-overrides="themeOverrides">
    <NMessageProvider>
      <NDialogProvider>
        <RouterView/>
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>

<script lang="ts" setup>
import type {GlobalThemeOverrides} from 'naive-ui'
import {NConfigProvider, NDialogProvider, NMessageProvider} from 'naive-ui'
import {RouterView} from 'vue-router'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#1565c0',
    primaryColorHover: '#0f7ad7',
    primaryColorPressed: '#0b4f94',
    borderRadius: '8px',
    fontFamily:
        'Inter, "Segoe UI", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif',
  },
  Button: {
    heightLarge: '44px',
  },
  Card: {
    borderRadius: '8px',
  },
}
</script>
