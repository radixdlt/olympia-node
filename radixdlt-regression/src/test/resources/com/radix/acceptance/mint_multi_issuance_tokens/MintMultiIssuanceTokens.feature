# See: https://radixdlt.atlassian.net/browse/RLAU-94
@rlau-94
Feature: Mint Multi-Issuance Tokens
  As a Token Class Owner
  I want to mint new tokens
  So I can increase the supply of my tokens as needed

  #Scenario: 1: Mint Tokens Total Supply
    #Given a library client who owns an account and created token "JOSH" with 0 initial supply and is listening to the state of "JOSH"
    #When the client executes mint 100 "JOSH" tokens
    #Then the client should be notified that "JOSH" token has a total supply of 100

  #Scenario: 2: Mint Zero Tokens
    #Given a library client who owns an account and created a token with 0 initial subunit supply
    #When the client executes mint 0 tokens
    #Then the client should be notified that the action failed because cannot mint with 0 tokens

  #Scenario: 3: Mint Too Many Tokens
    #Given a library client who owns an account and created a token with 2^255 initial subunit supply and is listening to the state of the token
    #When the client executes mint 2^255 subunit tokens
    #Then the client should be notified that the action failed because it reached the max allowed number of tokens of 2^256 - 1

  #Scenario: 4: Mint Tokens on non-existing token
    #Given a library client who owns an account where token "JOSH" does not exist
    #When the client executes mint 100 "JOSH" tokens
    #Then the client should be notified that the action failed because "JOSH" does not exist

  #Scenario: 5: Mint Tokens on unowned account
    #Given a library client who does not own a token class "JOSH" on another account
    #When the client executes mint 100 "JOSH" tokens
    #Then the client should be notified that the action failed because the client does not have permission to mint those tokens
