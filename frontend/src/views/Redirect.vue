<template>
  <div></div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  // 从 /redirect/xxx 跳转回 /xxx，实现页面刷新效果
  // 使用 fullPath 截取而非 params.path，保证 query 参数不丢失（如 /sale/orders/edit/1?copy=2）
  const target = route.fullPath.slice('/redirect'.length) || '/'
  router.replace(target)
})
</script>
