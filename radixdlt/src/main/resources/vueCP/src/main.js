// The Vue build version to load with the `import` command
// (runtime-only or standalone) has been set in webpack.base.conf with an alias.
import Vue from 'vue'
import App from './App'
import router from './router'
import BootstrapVue from 'bootstrap-vue'

import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap-vue/dist/bootstrap-vue.css'

import NavBar from '@/components/NavBar'
import Spinner from '@/components/Spinner'

Vue.config.productionTip = false
Vue.use(BootstrapVue)

// Declare global components.
Vue.component('NavBar', NavBar)
Vue.component('Spinner', Spinner)

/* eslint-disable no-new */
new Vue({
  el: '#app',
  router,
  components: { App },
  template: '<App/>'
})
