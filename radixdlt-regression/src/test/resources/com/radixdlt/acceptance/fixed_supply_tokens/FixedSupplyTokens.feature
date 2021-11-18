Feature: Fixed Supply Tokens
  As an account owner
  I want to create a fixed supply token with some key properties of an ERC-20 token,
  So that I can easily create a token coming from an ethereum background without applying too much extra learning

  Scenario: 1: Creating and transferring a fixed supply token
    Given I have an account with funds at a suitable Radix network
    When I create a fixed supply token with properties: 'fsymbol', 'ftoken-name', 'acceptance test token', 'http://icon.com', 'http://token.com', 150 total supply
    Then I can observe that the token has been created, with the correct values
    And I can send 10 of my new tokens to another account

  Scenario: 2: Total supply
    Given I have an account with funds at a suitable Radix network
    When I create a fixed supply token with a total supply of 10000
    Then I can observe that the token has been created, with a total supply of 10000
    And I cannot transfer more than the total supply

