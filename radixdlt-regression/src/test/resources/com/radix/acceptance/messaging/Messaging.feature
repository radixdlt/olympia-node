Feature: Token Transfer
  As an account owner,
  I want to be able to send messages along with token transfers
  So that I can exchange information across a Radix network

  Scenario: 1: Send messages
    Given I have two accounts with funds at a suitable Radix network
    And I send the plaintext message "Hello World!" from the first account to the second
    Then my message can be read

  Scenario: 2: Messages have an upper size boundary
    Given I have an account with funds at a suitable Radix network
    And I send a plaintext message with more than 255 characters
    Then the transaction will not be submitted
