<template>
  <b-container>
    <h1>{{ title }}</h1>
    <Spinner v-if="loading"/>
    <b-row v-if="!loading">
      <b-col>
        <h2>Transactions</h2>
        <ul>
          <li><span class="metric">Total:</span> {{data.witness.stored}}</li>
          <li><span class="metric">Transactions / sec:</span> {{data.witness.processing}}</li>
        </ul>
        <h2>Ledger</h2>
        <ul>
          <!-- <li><span class="metric">Processed:</span> {{data.ledger.processed}}</li> -->
          <li><span class="metric">Stored:</span> {{data.ledger.stored}}</li>
          <!-- <li><span class="metric">Processing:</span> {{data.ledger.processing + ' / sec'}}</li> -->
          <li><span class="metric">Storing:</span> {{data.ledger.storing + ' / sec'}}</li>
          <li><span class="metric">Conflicts Detected:</span> {{data.ledger.faults.tears}}</li>
          <li><span class="metric">Conflicts Resolved:</span> {{data.ledger.faults.stitched}}</li>
        </ul>
        <h2>Messages</h2>
        <ul>
          <li><span class="metric">Processed:</span> {{data.messages.processed}}</li>
          <li><span class="metric">Processing:</span> {{data.messages.processing + ' / sec'}}</li>
        </ul>
        <h2>Events</h2>
        <ul>
          <li><span class="metric">Processed:</span> {{data.events.processed}}</li>
          <li><span class="metric">Processing:</span> {{data.events.processing + ' / sec'}}</li>
        </ul>
      </b-col>
      <b-col cols="12" md="6">
        <h2>System</h2>
        <ul>
          <li><span class="metric">HID:</span> {{data.hid.value}}</li>
          <li><span class="metric">Agent:</span> {{data.agent.name}}</li>
          <li><span class="metric">Version:</span> {{data.agent.version + ' / ' + data.agent.protocol}}</li>
          <li><span class="metric">Free Memory:</span> {{data.memory.free}}</li>
          <li><span class="metric">Total Memory:</span> {{data.memory.total}}</li>
          <li><span class="metric">Max Memory:</span> {{data.memory.max}}</li>
          <li><span class="metric">Partition Low:</span> {{data.shards.low}}</li>
          <li><span class="metric">Partition High:</span> {{data.shards.high}}</li>
          <li><span class="metric">Synced:</span> {{data.period}}</li>
          <li><span class="metric">Clock:</span> {{data.clock}}</li>
          <li><span class="metric">CPUs:</span> {{data.processors}}</li>
        </ul>
      </b-col>
    </b-row>
  </b-container>
</template>

<script>
import axios from 'axios'
import moment from 'moment'

const API = 'http://localhost:8080/rpc'

export default {
  name: 'Status',

  data () {
    return {
      loading: false,
      data: null,
      post: null,
      error: null,
      title: 'Status',
      moment: moment
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

      setInterval(() => {
        axios.post(API, {
          method: 'System.getInstance',
          params: [],
          id: 0
        }).then(response => {
          this.loading = false
          // JSON responses are automatically parsed.
          this.data = response.data.result
          this.data.period = new Date(this.data.period * 10000).toLocaleDateString('en-GB', {
            hour: 'numeric',
            minute: 'numeric',
            second: 'numeric'
          })
        }).catch(e => {
          this.error = e.toString()
        })
      }, 1000)
    }
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
h1 {
  font-weight: normal;
  border-bottom: 1px solid lightgray;
  padding: 15px 0 15px 0;
}
h2 {
  font-weight: bold;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  margin: 5px 0 5px 0;
}
a {
  color: #42b983;
}
.metric {
  font-weight: 600;
}
</style>
