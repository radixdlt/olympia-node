Feature: Consensus testing

  Scenario: For a message sent to network, all nodes should have the commit of the message
    Given I have local network with 6 nodes and quorumsize of 6 nodes
    When I send a message to first node one
    Then corresponding atom of the message should be available on atom store of all nodes

  @sequence
  Scenario Outline: Sequence of message send should be in same sequence for all nodes
    Given I have local network with 6 nodes and quorumsize of 6 nodes
    When I send sequence of <numberOfMessages> message to first node one
    Then the AtomIDs of the messages should be of same sequence on all nodes
    Examples:
      | numberOfMessages |
      | 2                |
      | 10               |

  @ignore
  Scenario: Crash tolerant when nodes are same size as quorum
    Given I have local network with 6 nodes and quorumsize of 4 nodes
    And I send a message to first node one
    Then corresponding atom of the message should be available on atom store of all nodes
    And I bring down 2 nodes
    When I send a message to first node one
    Then corresponding atom of the message should be available on atom store of all nodes

  @ignores
  Scenario: Network failure when nodes are less than size as quorum
    Given I have local network with 6 nodes and quorumsize of 4 nodes
    And I send a message to first node one
    Then corresponding atom of the message should be available on atom store of all nodes
    And I bring down three nodes
    When I send a message to first node one
    Then corresponding atom of the message is not available on atom store of all nodes