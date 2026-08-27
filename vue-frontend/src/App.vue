<template>
  <!-- key로 전체 경로를 준다: /projects/:id, /courses/:id처럼 파라미터만 바뀌는
       라우트는 Vue Router가 기본적으로 컴포넌트를 재사용한다. onMounted에서만
       데이터를 불러오는 화면들은 그러면 파라미터가 바뀌어도 화면이 안 바뀌고,
       특히 AssetDetailView는 이미 표시(un-mask)한 Secret 평문이 다음 자산으로
       이동해도 클라이언트 상태에 그대로 남아있는 문제가 있었다(이슈 #18 "재마스킹
       시 민감 값을 클라이언트 상태에서 제거" 요구사항 위반). fullPath를 key로
       주면 파라미터가 바뀔 때마다 컴포넌트가 새로 마운트되어 항상 깨끗한
       상태에서 시작한다. -->
  <router-view :key="$route.fullPath" />
</template>

<script setup>
</script>
