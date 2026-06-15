<template>
  <component
    :is="linkable ? 'router-link' : 'div'"
    v-bind="linkable ? { to: { name: 'user-profile', params: { userId } } } : {}"
    class="user-avatar-link"
    :class="[`user-avatar-link--${size}`]"
    @click.stop
  >
    <div class="user-avatar-link__avatar">
      <img v-if="avatarUrl" :src="avatarUrl" :alt="displayName || 'User'"/>
      <img v-else :src="defaultAvatarSrc" :alt="displayName || 'User'"/>
    </div>
    <span v-if="showName" class="user-avatar-link__name">{{ displayName || 'User' }}</span>
  </component>
</template>

<script lang="ts" setup>
import {computed} from 'vue'

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

const defaultAvatarSrc = computed(() => {
  switch (props.role) {
    case 0:
      return '/assets/avatar-admin-default.png'
    case 2:
      return '/assets/avatar-teacher-default.png'
    default:
      return '/assets/avatar-student-default.png'
  }
})
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
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  flex-shrink: 0;
}

.user-avatar-link__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
}

.user-avatar-link--tiny .user-avatar-link__name {
  font-size: 12px;
}

/* Size: small (32px) �?forum reply headers, comment identity */
.user-avatar-link--small .user-avatar-link__avatar {
  width: 32px;
  height: 32px;
}

.user-avatar-link--small .user-avatar-link__name {
  font-size: 13px;
}

/* Size: medium (42px) �?member lists, admin tables */
.user-avatar-link--medium .user-avatar-link__avatar {
  width: 42px;
  height: 42px;
}

.user-avatar-link--medium .user-avatar-link__name {
  font-size: 14px;
}

/* Size: large (48px) �?course sidebar team, assistant stack */
.user-avatar-link--large .user-avatar-link__avatar {
  width: 48px;
  height: 48px;
}

.user-avatar-link--large .user-avatar-link__name {
  font-size: 15px;
}

/* Size: xl (64px) �?instructor biography */
.user-avatar-link--xl .user-avatar-link__avatar {
  width: 64px;
  height: 64px;
}

.user-avatar-link--xl .user-avatar-link__name {
  font-size: 16px;
}
</style>
