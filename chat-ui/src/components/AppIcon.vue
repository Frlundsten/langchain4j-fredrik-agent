<script setup lang="ts">
// Small stroke icon set (24×24, currentColor), so the app has no icon dependency.
export type IconName =
  | 'menu'
  | 'plus'
  | 'trash'
  | 'sun'
  | 'moon'
  | 'send'
  | 'activity'
  | 'calculator'
  | 'clock'
  | 'wrench'
  | 'alert'
  | 'refresh'
  | 'chat'
  | 'spark'

defineProps<{ name: IconName; size?: number }>()

const paths: Record<IconName, string[]> = {
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h10'],
  plus: ['M12 5v14', 'M5 12h14'],
  trash: ['M4 7h16', 'M10 11v6', 'M14 11v6', 'M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12', 'M9 7V4h6v3'],
  sun: ['M12 3v2', 'M12 19v2', 'M5 5l1.4 1.4', 'M17.6 17.6 19 19', 'M3 12h2', 'M19 12h2', 'M5 19l1.4-1.4', 'M17.6 6.4 19 5', 'M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8'],
  moon: ['M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z'],
  send: ['M5 12h13', 'M13 6l6 6-6 6'],
  activity: ['M3 12h4l3-8 4 16 3-8h4'],
  calculator: ['M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z', 'M8 7h8', 'M8 12h.01', 'M12 12h.01', 'M16 12h.01', 'M8 16h.01', 'M12 16h.01', 'M16 16h.01'],
  clock: ['M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18', 'M12 7v5l3 2'],
  wrench: ['M14.7 6.3a4 4 0 0 0-5.4 5.2L4 16.8V20h3.2l5.3-5.3a4 4 0 0 0 5.2-5.4l-2.5 2.5-2.4-.6-.6-2.4z'],
  alert: ['M12 9v4', 'M12 17h.01', 'M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z'],
  refresh: ['M20 11a8 8 0 0 0-14.9-3.5', 'M4 4v4h4', 'M4 13a8 8 0 0 0 14.9 3.5', 'M20 20v-4h-4'],
  chat: ['M4 5h16v11H8l-4 4z'],
  spark: ['M12 3v4', 'M12 17v4', 'M3 12h4', 'M17 12h4', 'M6 6l2.5 2.5', 'M15.5 15.5 18 18', 'M6 18l2.5-2.5', 'M15.5 8.5 18 6'],
}
</script>

<template>
  <svg
    :width="size ?? 18"
    :height="size ?? 18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    class="shrink-0"
  >
    <path v-for="d in paths[name]" :key="d" :d="d" />
  </svg>
</template>
