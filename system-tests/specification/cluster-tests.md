### Latency Test

```given_10_correct_bfts_in_latent_cluster_network__then_all_instances_should_get_same_commits_and_progress_should_be_made```

#### Description

Adds packet loss to one node and makes sure that consensus is still taking place. 

#### Motivation

To ensure the network can still function when latency and/or packet loss is introduced.    

#### Environment

An existing testnet.

#### Test Steps

1) Add 20% package loss to one of the nodes  
2) Checks:
    1) Responsiveness
    2) Proposals have direct parents (PHDP)
    3) No timeouts
    3) Safety
    4) Liveness

#### Comments

This test can be dangerous, because adding _both_ latency and packet loss to a node has caused it to permanently desync.  

---

### Failed Liveness Test
```given_10_correct_bfts_in_latent_cluster_network__when_all_nodes_are_out_synchrony__then_a_liveness_check_should_fail```

#### Description

Outgoing traffic is blocked for every node. The nodes should then fail a liveness test. When communication resumes, consensus should resume. 

#### Motivation

This was specified in the [Drop 2 testing document](https://radixdlt.atlassian.net/wiki/spaces/RPNV1/pages/939819009/Drop+2+Testing), specifically [this task](https://radixdlt.atlassian.net/browse/RPNV1-728).  

#### Environment

An existing testnet. 

#### Test Steps
1) Add 20% package loss to one of the nodes 
2) For each node, block port 30000 via iptables 
3) A liveness test should fail
4) The ports are unblocked and liveness is asserted again

#### Comments

This is a somewhat destructive test and has desynced the network in the past.

