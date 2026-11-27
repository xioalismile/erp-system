export default [
  {
    path: 'board/cockpit',
    name: 'CockpitLocal',
    component: () => import('@custom-board/Cockpit.vue'),
    meta: { title: '运营总览' }
  },
  {
    path: 'board/cockpit/screen',
    name: 'CockpitLocalScreen',
    component: () => import('@custom-board/Screen.vue'),
    meta: { title: '运营总览大屏' }
  }
]
