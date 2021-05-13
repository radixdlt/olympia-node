Feature: Token Transfer
  As a radix user,
  I want to be able to transfer tokens to other accounts,
  So that I can participate in the economy

  Scenario: 1: Send and receive tokens
    Given I have an account with funds at a suitable Radix network
    And I can transfer 10 XRD to another account
    And that account can transfer 5 XRD back to me
