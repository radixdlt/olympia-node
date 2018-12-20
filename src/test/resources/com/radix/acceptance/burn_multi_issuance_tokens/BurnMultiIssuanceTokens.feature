# See: https://radixdlt.atlassian.net/browse/RLAU-95
@rlau-95
Feature: Burn Multi-Issuance Tokens
  As a Token Class Creator
  I want to burn tokens in my account
  So that I can decrease the supply of tokens in circulation as needed

  Scenario: 1: Burn Tokens Total Supply
    Given a library client who owns an account and created token "JOSH" with 100 initial supply and is listening to the state of "JOSH"
    When the client executes 'BURN 50 "JOSH" tokens'
    Then the client should be notified that "JOSH" token has a total supply of 50

  Scenario: 2: Burn Tokens Action Notification
    Given a library client who owns an account and created token "JOSH" with 100 initial supply and is listening to the token class actions of this token
    When the client executes 'BURN 50 "JOSH" tokens'
    Then the client should be notified that a new action of 'BURN 50 "JOSH" tokens' has been executed

  Scenario: 3: Burn Too Many Tokens
    Given a library client who owns an account and created token "JOSH" with 100 initial supply and is listening to the state of "JOSH"
    When the client executes 'BURN 101 "JOSH" tokens'
    Then the client should be notified that the action failed because there's not that many tokens in supply

  Scenario: 4: Burn Tokens on non-existing token
    Given a library client who owns an account where token "JOSH" does not exist
    When the client executes 'BURN 1 "JOSH" tokens'
    Then the client should be notified that the action failed because "JOSH" does not exist

  Scenario: 5: Burn Tokens on unowned account
    Given a library client who does not own a token class "JOSH" on another account
    When the client executes 'BURN 1 "JOSH" tokens'
    Then the client should be notified that the action failed because the client does not have permission to burn those tokens
