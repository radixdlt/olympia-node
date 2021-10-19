Feature: Token Transfer
  As an account owner,
  I want to be able to transfer tokens to other accounts,
  So that I can participate in the economy

  Scenario: 1: Send and receive tokens
    Given I have two accounts with funds at a suitable Radix network
    And I transfer 5 XRD from the first account to the second
    Then the second account can transfer 4 XRD back to the first

