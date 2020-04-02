Feature: Consensus testing

  Scenario: For a message sent to network, all nodes should have the commit of the message
    Given I have local network with 6 nodes and quorumsize of 6 nodes
    When I send a message to first node one
    Then corresponding atom of the message should be available on atom store of all nodes



