# See: https://radixdlt.atlassian.net/browse/RPNV1-356
@rpnv1-356
Feature: Multiple Transactions
  As a library client,
  I want to read atomic transactions with multiple actions in it the same way it was written
  So that I can confidently know what the the intentions were of an atom
  We need a way to explicitly group particles in an atom specifically for things like fungible tokens which need to be implemented with multiple particles per atom.

  Scenario: 1: Multiple transfers from same address
    Given I have access to a suitable Radix network
    When I submit atom which contains two transfers with the same token type from 1 source address to 2 destination addresses
    Then I can observe the atom being accepted

  Scenario: 2: Multiple transfers to same address
    Given I have access to a suitable Radix network
    When I submit atom which contains two transfers with the same token type from 2 source addresses to 1 destination address
    Then I can observe the atom being accepted

  Scenario: 3: Multiple transfers between different addresses
    Given I have access to a suitable Radix network
    When I submit atom which contains two transfers with the same token type from 2 source addresses to 2 destination addresses
    Then I can observe the atom being accepted

  Scenario: 4: Multiple transfers of different tokens
    Given I have access to a suitable Radix network
    When I submit atom which contains two transfers with different token types RPNV1A and RPNV1B
    Then I can observe the atom being accepted

  Scenario: 5: The transfer is atomic
    Given I have access to a suitable Radix network
    When I submit atom which contains two transfers with the same token type from 2 source addresses to 2 destination addresses where one transfer exceeds amount of available funds
    Then I can observe the atom being rejected with a validation error
