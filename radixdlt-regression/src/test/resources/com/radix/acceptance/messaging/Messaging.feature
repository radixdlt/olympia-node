Feature: Token Transfer
  As an account owner,
  I want to be able to send messages along with token transfers
  So that I can exchange information across a Radix network

  Scenario: 1: Send and read message
    Given I have two accounts with funds at a suitable Radix network
    And I send the plaintext message "Hello World!" from the first account to the second
    Then my message is visible to everyone
