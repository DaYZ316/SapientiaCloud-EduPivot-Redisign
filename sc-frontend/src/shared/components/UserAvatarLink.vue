<template>
  <component
      :is="linkable ? 'router-link' : 'div'"
      :class="[`user-avatar-link--${size}`]"
      class="user-avatar-link"
      v-bind="linkable ? { to: { name: 'user-profile', params: { userId } } } : {}"
      @click.stop
    >
    <div class="user-avatar-link__avatar">
      <img v-if="avatarUrl" :alt="displayName || 'User'" :src="avatarUrl"/>
      <span v-else>{{ avatarInitials }}</span>
    </div>
    <span v-if="showName" class="user-avatar-link__name">{{ displayName || 'User' }}</span>
  </component>
</template>

<script lang="ts" setup>
import {computed} from 'vue'

import {getAvatarInitials} from '@/shared/utils/avatar'

const props = withDefaults(defineProps<{
  userId: string
  displayName?: string | null
  avatarUrl?: string | null
  role?: number | null
  size?: 'tiny' | 'small' | 'medium' | 'large' | 'xl'
  showName?: boolean
  linkable?: boolean
}>(), {
  size: 'small',
  showName: true,
  linkable: true,
})

const avatarInitials = computed(() => getAvatarInitials(props.displayName))
</script>

<style scoped>
.user-avatar-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.user-avatar-link:hover {
  opacity: 0.8;
}

.user-avatar-link__avatar {
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: var(--color-primary);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-primary);
  font-family: var(--font-heading);
  font-weight: 700;
  flex-shrink: 0;
}

.user-avatar-link__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-avatar-link__avatar span {
  line-height: 1;
}

.user-avatar-link__name {
  font-family: var(--font-body);
  font-weight: 400;
  color: var(--color-on-surface);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Size: tiny (24px) �?inline forum post author chips */
.user-avatar-link--tiny .user-avatar-link__avatar {
  width: 24px;
  height: 24px;
  font-size: 10px;
}

.user-avatar-link--tiny .user-avatar-link__name {
  font-size: 12px;
}

/* Size: small (32px) �?forum reply headers, comment identity */
.user-avatar-link--small .user-avatar-link__avatar {
  width: 32px;
  height: 32px;
  font-size: 12px;
}

.user-avatar-link--small .user-avatar-link__name {
  font-size: 13px;
}

/* Size: medium (42px) �?member lists, admin tables */
.user-avatar-link--medium .user-avatar-link__avatar {
  width: 42px;
  height: 42px;
  font-size: 16px;
}

.user-avatar-link--medium .user-avatar-link__name {
  font-size: 14px;
}

/* Size: large (48px) �?course sidebar team, assistant stack */
.user-avatar-link--large .user-avatar-link__avatar {
  width: 48px;
  height: 48px;
  font-size: 18px;
}

.user-avatar-link--large .user-avatar-link__name {
  font-size: 15px;
}

/* Size: xl (64px) �?instructor biography */
.user-avatar-link--xl .user-avatar-link__avatar {
  width: 64px;
  height: 64px;
  font-size: 24px;
}

.user-avatar-link--xl .user-avatar-link__name {
  font-size: 16px;
}
</style>
