<template>
  <b-navbar class="navbar" toggleable="md">
    <b-navbar-toggle target="nav_collapse"></b-navbar-toggle>

    <!-- <b-navbar-brand href="#">
      <img src="../assets/logo.png" alt="logo" height="30">
    </b-navbar-brand> -->

    <b-collapse is-nav id="nav_collapse">

      <!-- Right aligned nav items -->
      <b-navbar-nav class="ml-auto">
        <b-nav-item>
          <router-link to="/status" tag="span">Status</router-link>
        </b-nav-item>
        <b-nav-item>
          <router-link to="/profiler" tag="span">Profiler</router-link>
        </b-nav-item>
        <b-nav-item>
          <router-link to="/peers" tag="span">Peers</router-link>
        </b-nav-item>
        <!-- <b-nav-item-dropdown text="Lang" right>
          <b-dropdown-item href="#">EN</b-dropdown-item>
          <b-dropdown-item href="#">ES</b-dropdown-item>
          <b-dropdown-item href="#">RU</b-dropdown-item>
          <b-dropdown-item href="#">FA</b-dropdown-item>
        </b-nav-item-dropdown> -->
      </b-navbar-nav>

    </b-collapse>
  </b-navbar>
</template>

<script>
export default {
  name: 'NavBar'
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style lang="scss" scoped>
.navbar {
  background-color: #35B0E5;
}
.nav-item {
  span {
    font-weight: 900;
    color: darken(white, 10);
  }
  span.router-link-active {
    color: white;
  }
}
</style>
