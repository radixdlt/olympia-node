@single
Feature: Fixed Supply Tokens
  As an account owner
  I want to create a fixed supply token with some key properties of an ERC-20 token,
  So that I can easily create a token coming from an ethereum background without applying too much extra learning

  Scenario: 1: Creating a fixed supply token
    Given I have an account with funds at a suitable Radix network
    When I create a fixed supply token with properties: 'testrri', 'symbol', 'name', 'desc', 'www.iconUrl.com', 'www.tokenUrl.com', with amount 150
    Then I can observe that the token has been created, with the correct values

  Scenario: 2: Creating a conflicting token
    Given I have an account with funds at a suitable Radix network
    When I create a fixed supply token with rri 'testrri2'
    And I create a fixed supply token with rri 'testrri2'
    Then I can observe that the last token creation failed

  Scenario: 3: Total supply
    Given I have an account with funds at a suitable Radix network

