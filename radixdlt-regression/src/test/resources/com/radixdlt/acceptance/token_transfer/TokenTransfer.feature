Feature: Token Transfer
  As an account owner,
  I want to be able to transfer tokens to other accounts,
  So that I can participate in the network's economy

  Scenario: 1: Send and receive tokens
    Given I have two accounts with funds at a suitable Radix network
    And I transfer 5 XRD from the first account to the second
    Then the second account can transfer 4 XRD back to the first

  Scenario: 2: Transfer tokens to self
    Given I have an account with 10 XRD at a suitable Radix network
    When I transfer 5 XRD to myself
    Then I have the same amount of tokens, minus fees

  Scenario: 3: Transfer more than available
    Given I have an account with 10 XRD at a suitable Radix network
    Then I cannot transfer 15 XRD to another account



