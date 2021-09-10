@single
Feature: Transaction Lookup
  As an account owner,
  I want to get information about transactions in a radix network
  So that I can observe what is happening to the network and to my own transactions

  Scenario: 1: Get transaction status
    Given I have an account with funds at a suitable Radix network
    And I transfer 3 XRD anywhere
    Then I can check the status of my transaction
    And It should be quickly CONFIRMED

  Scenario: 2: Lookup transaction
    Given I have an account with funds at a suitable Radix network
    And I transfer 2 XRD anywhere
    Then I can lookup my transaction and observe it contains the expected information

  Scenario: 3: Transaction History
    Given I have an account with funds at a suitable Radix network
    And I perform 5 token transfers
    Then I can observe those 5 transactions in my transaction history