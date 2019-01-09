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

  Scenario: 3: WebSocket Subscribe then Unsubscribe then Sunscribe
    Given a node connected websocket client who has an atom subscription to an account with two messages, Y and Z
    When the client sends a cancel subscription request followed by a subscribe message request
    Then the client should receive two messages Y and Z

  Scenario: 4: API Unsubscribe Two Accounts
    Given a library client who is solely subscribed to messages in two accounts
    When the client disposes both Observables
    Then the client can observe that all network connections are closed
