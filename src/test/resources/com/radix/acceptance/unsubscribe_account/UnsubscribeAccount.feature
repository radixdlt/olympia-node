# See: https://radixdlt.atlassian.net/browse/RLAU-94
@rlau-59
Feature: Unsubscribe Account
  As an API client subscribed to an account listening for atoms
  I want to unsubscribe from the account and stop receiving notifications
  So I don't waste mine or nodes resources

  Scenario: 1: WebSocket Cancel Subscription
    Given a node connected websocket client who has an atom subscription to an empty account
    When the client sends a cancel subscription request followed by a message to this account
    Then the client should not receive any atom notification

  Scenario: 2: WebSocket Cancel One Subscription out of Two
    Given a node connected websocket client who has an atom subscription to two accounts, A and B
    When the client sends a cancel subscription request to account A followed by messages to both accounts
    Then the client should only receive a cancel subscription successful message with A and the message sent to B

  Scenario: 3: WebSocket Subscribe then Unsubscribe then Sunscribe
    Given a node connected websocket client who has an atom subscription to an account with two messages, Y and Z
    When the client sends a cancel subscription request followed by a subscribe message request
    Then the client should receive two messages Y and Z

  Scenario: 4: API Unsubscribe Two Accounts
    Given a library client who is solely subscribed to messages in two accounts
    When the client disposes both Observables
    Then the client can observe that all network connections are closed
