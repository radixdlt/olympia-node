Feature: Mutable Supply Tokens
  As an account owner
  I want to create a mutable supply token in my account with some key properties of an ERC-20 token
  So that I can easily create a token coming from an ethereum background without applying too much extra learning

  Scenario: 1: Creating a mutable supply token
    Given I have an account with funds at a suitable Radix network
    When I create a mutable supply token with properties: 'msymbol', 'mtoken-name', 'acceptance test token', 'http://icon.com', 'http://token.com'
    Then I can observe that the token has been created, with the correct values

  Scenario: 2: Minting and transferring
    Given I have an account with funds at a suitable Radix network
    When I create a mutable supply token
    Then I can mint 10000 of this token
    And I can send 10 of my new tokens to another account

  Scenario: 3: Burning
    Given I have an account with funds at a suitable Radix network
    When I create a mutable supply token
    Then I can mint 100 of this token
    And I can burn 100 of this token
    And the total supply should be 0