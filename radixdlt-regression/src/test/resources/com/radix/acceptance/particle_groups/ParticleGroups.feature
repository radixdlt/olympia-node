# See: https://radixdlt.atlassian.net/browse/RLAU-40
@rlau-40
Feature: Particle Groups
  As a library client,
  I want to read atomic transactions with multiple actions in it the same way it was written
  So that I can confidently know what the the intentions were of an atom
  We need a way to explicitly group particles in an atom specifically for things like fungible tokens which need to be implemented with multiple particles per atom.

  Scenario: 1: Conflicting mints across particle groups
    Given I have access to a suitable Radix network
    When I submit two fixed-supply token-creation requests with symbol "RLAU" and in separate particle groups
    Then I can observe atom 1 being rejected with a validation error

  Scenario: 2: Agreeable mints across particle groups
    Given I have access to a suitable Radix network
    When I submit two fixed-supply token-creation requests with symbol "RLAU1" and "RLAU2" and in separate particle groups
    Then I can observe atom 1 being accepted

  #Scenario: 3: Double-spend transfer across particle groups
    #Given I have access to a suitable Radix network
    #When I submit a fixed-supply token-creation request with name "RLAU Test", symbol "RLAU", totalSupply 100 scaled and granularity 1 scaled
    #And I can observe atom 1 being accepted
    #Then Two token transfer requests of 100 for "RLAU" in separate particle groups should fail

#  No longer relevant: There can be multiple token types per ParticleGroup as of commit 075d8d3e98667e5d5a02a724c8634fa30190e990 on RadixCore.
#  Scenario: 4: Different token types within particle group
#    Given I have access to a suitable Radix network
#    When I submit a fixed-supply token-creation request with name "RLAU 1", symbol "RLAU1", totalSupply 100 scaled and granularity 1 scaled
#    And I submit a fixed-supply token-creation request with name "RLAU 2", symbol "RLAU2", totalSupply 100 scaled and granularity 1 scaled
#    And I can observe atom 1 being accepted
#    And I can observe atom 2 being accepted
#    And I submit one token transfer requests of 100 for "RLAU1" and of 100 for "RLAU2" in one particle group
#    Then I can observe atom 3 being rejected with a validation error

  Scenario: 5: Empty particle group
    Given I have access to a suitable Radix network
    When I submit an arbitrary atom with an empty particle group
    Then I can observe atom 1 being rejected with a failure