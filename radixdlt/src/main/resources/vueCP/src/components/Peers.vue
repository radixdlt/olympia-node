<template>
  <b-container class="profiler" v-if='data'>
    <h1>{{ title }}</h1>
    <b-table striped hover :items="items"></b-table>
  </b-container>
</template>

<script>
import axios from 'axios'

const API = 'http://localhost:8080/rpc'

const items = [
  { isActive: true, age: 40, first_name: 'Dickerson', last_name: 'Macdonald' },
  { isActive: false, age: 21, first_name: 'Larsen', last_name: 'Shaw' },
  { isActive: false, age: 89, first_name: 'Geneva', last_name: 'Wilson' },
  { isActive: true, age: 38, first_name: 'Jami', last_name: 'Carney' }
]

export default {
  name: 'Peers',

  data () {
    return {
      loading: false,
      data: null,
      post: null,
      error: null,
      title: 'Peers',
      items: items
    }
  },
  created () {
    // fetch the data when the view is created and the data is
    // already being observed
    this.fetchData()
  },
  watch: {
    // call again the method if the route changes
    '$route': 'fetchData'
  },
  methods: {
    fetchData () {
      this.loading = true
      this.data = this.error = this.post = null

      axios.post(API, {
        method: 'System.getProfiler',
        params: [],
        id: 0
      }).then(response => {
        this.loading = false
        // JSON responses are automatically parsed.
        this.data = response.data.result
      }).catch(e => {
        this.error = e.toString()
      })
    }
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
h1, h2 {
  font-weight: normal;
}
h1 {
  padding: 15px 0 15px 0;
  margin-bottom: 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
a {
  color: #42b983;
}
</style>
