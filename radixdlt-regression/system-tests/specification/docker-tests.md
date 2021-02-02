### Smoke Test

#### Description
This test establishes a steady state with some latency, and tests that the network can still function despite a (simulated) node crash.

#### Motivation

To ensure that the network can basically function under some latency and also survive a node crash.

This test is an amalgamation of some previous test cases which are now condensed into one. 

#### Environment

A network of 4 or 5 nodes, with some latency.

#### Test Steps
1) Set 100ms latency and 20% package loss
2) Checks:
    1) Responsiveness
    2) Proposals have direct parents (PHDP)
    3) No timeouts
    3) Safety
    4) Liveness
3) Stop one of the node containers
4) Checks:
    1) Responsiveness
    2) PHDP
    3) Safety
    4) Liveness
5) Start the container that was stopped earlier
6) The node that was restarted should resync with the network

#### Comments

This test discovered [an actual bug](https://radixdlt.atlassian.net/browse/RPNV1-859). 
 
Also, as a future addition, an Atom could be submitted and it's inclusion to the ledger asserted.

---

### Failed Liveness Test
```given_X_correct_bfts_in_latent_docker_network__when_all_nodes_are_out_synchrony__then_a_liveness_check_should_fail```

#### Description

Outgoing traffic is blocked for every node. The nodes should then fail a liveness test. When communication resumes, consensus should resume. 

#### Motivation

This was specified in the [Drop 2 testing document](https://radixdlt.atlassian.net/wiki/spaces/RPNV1/pages/939819009/Drop+2+Testing), specifically [this task](https://radixdlt.atlassian.net/browse/RPNV1-727).  

#### Environment

A network of 4 or 5 nodes. 

#### Test Steps
1) After the network has started, assert liveness
2) For each node, block port 30000 via iptables
3) A liveness test should fail
4) The ports are unblocked and liveness is asserted again

#### Comments

This is a somewhat destructive test and has desynced the network in the past.

