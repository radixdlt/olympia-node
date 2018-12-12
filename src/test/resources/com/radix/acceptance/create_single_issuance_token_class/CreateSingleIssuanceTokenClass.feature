# See: https://radixdlt.atlassian.net/browse/RLAU-40
@rlau-40
Feature: Create Single Issuance Token Class
  As an account owner
  I want to create a fixed supply token with some key properties of an ERC-20 token,
  So that I can easily create a token coming from an ethereum background without applying too much extra learning

  Scenario: 1: Creating a unique token
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When I submit a fixed-supply token-creation request with name "RLAU Test", symbol "RLAU", totalSupply 1000000000 and granularity 1
    Then I can observe the atom being accepted

  Scenario: 2: Creating a conflicting token
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When I submit a fixed-supply token-creation request with symbol "RLAU"
    And I submit a fixed-supply token-creation request with symbol "RLAU"
    Then I can observe atom 1 being accepted
    And I can observe atom 2 being rejected as a collision

  Scenario: 3: Creating a token with disallowed granularity
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When I submit a fixed-supply token-creation request with granularity 0
    Then I can observe the atom being rejected with a validation error

  Scenario: 4: Transacting within granularity
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When I submit a fixed-supply token-creation request with granularity 2
    And I observe the atom being accepted
    And I submit a token transfer request of 100 for "RLAU" to an arbitrary account
    Then I can observe atom 2 being accepted

  Scenario: 5: Transacting outside granularity
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When I submit a fixed-supply token-creation request with granularity 2
    And I observe the atom being accepted
    And I submit a token transfer request of 1 for "RLAU" to an arbitrary account
    Then I can observe atom 2 being rejected with a validation error

  Scenario: 6: Token symbol too short
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When I submit a fixed-supply token-creation request with symbol "1"
    Then I can observe the atom being rejected with a validation error

  Scenario: 7: Token symbol too long
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When I submit a fixed-supply token-creation request with symbol "123456"
    Then I can observe the atom being rejected with a validation error
