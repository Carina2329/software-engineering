<script setup lang="ts">
</script>

<template>
  <router-view />
</template>

<style>
:root {
  color: #15211f;
  background: #f4f7f5;
  font-family:
    "IBM Plex Sans", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  min-width: 320px;
  min-height: 100%;
  margin: 0;
}

body {
  min-height: 100vh;
  background:
    radial-gradient(circle at 10% 5%, rgba(61, 132, 113, 0.13), transparent 28rem),
    #f4f7f5;
}

button,
input {
  font: inherit;
}
</style>
