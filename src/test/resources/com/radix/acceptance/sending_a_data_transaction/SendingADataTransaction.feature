# See: https://radixdlt.atlassian.net/browse/RLAU-162
@rlau-162
Feature: Sending A Data Transaction
  As a Radix library user,
  I want to enable sending a transaction that includes data to another account,
  So that I can communicate or share information.

  Scenario: 1: Message Rejected by Bad Signature
    Given I have access to a suitable Radix network
    When I submit a message with "1" to another client claiming to be another client
    Then I can observe the atom being rejected with a validation error

  Scenario: 2: Message Sent and Received
    Given I have access to a suitable Radix network
    And I submit a message with "2" to another client
    Then I can observe the atom being accepted
    And I can observe a message with "2"
    And another client can observe a message with "2"

  Scenario: 3: Message Sent to Self
    Given I have access to a suitable Radix network
    And I submit a message with "3" to myself
    Then I can observe a message with "3" from myself