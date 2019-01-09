# See: https://radixdlt.atlassian.net/browse/RLAU-59
@rlau-59
Feature: Unsubscribe Account
  As an API client subscribed to an account listening for atoms
  I want to unsubscribe from the account and stop receiving notifications
  So I don't waste mine or nodes resources

  Scenario: 1: WebSocket Cancel Subscription
    Given a node connected websocket client who has an atom subscription to an empty account
    When the client sends a cancel subscription request to his account
    And the client sends a message to himself
    Then the client should not receive any new atom notifications in his account

  Scenario: 2: WebSocket Cancel One Subscription out of Two
    Given a node connected websocket client who has an atom subscription to an empty account
    And the websocket client has an atom subscription to another account
    When the client sends a cancel subscription request to his account
    And the client sends a message to the other account
    Then the client should not receive any new atom notifications in his account
    And the client should receive the sent atom in the other subscription

  Scenario: 3: WebSocket Subscribe then Unsubscribe then Subscribe
    Given a node connected websocket client who has an atom subscription to an empty account
    When the client sends a message to himself
    And the client sends another message to himself
    And the client sends a cancel subscription request to his account
    And the client sends a subscribe request to his account in another subscription
    Then the client should receive both atom messages in the other subscription
