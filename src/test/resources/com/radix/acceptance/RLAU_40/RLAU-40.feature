# Author: martin@radixdlt.com
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
    When property "name" = "RLAU-40 Test token"
    And property "symbol" = "RLAU"
    And property "description" = "RLAU-40 Test token"
    And property "totalSupply" = 1000000000
    And property "granularity" = 1
    And I submit a token-creation request
    Then I can observe the atom being accepted

  Scenario: 2: Creating a conflicting token
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When property "name" = "RLAU-40 Test token"
    And property "symbol" = "RLAU"
    And property "description" = "RLAU-40 Test token"
    And property "totalSupply" = 1000000000
    And property "granularity" = 1
    And I submit a token-creation request
    And property "name" = "RLAU-40 Test token"
    And property "symbol" = "RLAU"
    And property "description" = "RLAU-40 Test token"
    And property "totalSupply" = 1000000000
    And property "granularity" = 1
    And I submit a token-creation request
    Then I can observe atom 1 being accepted
    And I can observe atom 2 being rejected as a collision
    
  Scenario: 6: Token symbol too short
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When property "name" = "RLAU-40 Test token"
    And property "symbol" = "1"
    And property "description" = "RLAU-40 Test token"
    And property "totalSupply" = 1000000000
    And property "granularity" = 1
    And I submit a token-creation request
    Then I can observe the atom being rejected with a validation error

  Scenario: 7: Token symbol too long
    Given I have access to suitable development tools
    And I have included the radixdlt-java library
    And I have access to a suitable Radix network
    When property "name" = "RLAU-40 Test token"
    And property "symbol" = "123456"
    And property "description" = "RLAU-40 Test token"
    And property "totalSupply" = 1000000000
    And property "granularity" = 1
    And I submit a token-creation request
    Then I can observe the atom being rejected with a validation error
