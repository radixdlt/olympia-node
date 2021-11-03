Feature: Multi-action Transactions
  As an account owner,
  I want to be bale to perform multiple operations in one logical transaction
  So that I can use the radix network more efficiently

  Scenario: 1: A multi action transaction
    Given I have an account with 120 XRD at a suitable Radix network
    And I submit a transaction with three actions: two transfers and one staking
    Then I can observe the actions taking effect

  Scenario: 2: A multi action transaction exceeding the total balance
    Given I have an account with 10 XRD at a suitable Radix network
    Then I cannot submit a transaction with two actions: transfer 6 XRD to account2 and transfer 5 XRD to account3

