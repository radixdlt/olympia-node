import Vue from 'vue'
import Router from 'vue-router'
import Status from '@/components/Status'
import Profiler from '@/components/Profiler'
import Peers from '@/components/Peers'

Vue.use(Router)

export default new Router({
  routes: [
    { path: '/', redirect: '/status' },
    {
      path: '/status',
      name: 'Status',
      component: Status
    },
    {
      path: '/profiler',
      name: 'Profiler',
      component: Profiler
    },
    {
      path: '/peers',
      name: 'Peers',
      component: Peers
    }
  ]
})
