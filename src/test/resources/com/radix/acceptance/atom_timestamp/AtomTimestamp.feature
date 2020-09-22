# See: https://radixdlt.atlassian.net/browse/RPNV1-358
@rpnv1-358
Feature: Atom timestamps
    As either a Radix developer or an Exchange,
    I would like to be able to query for the ledger timestamp for a committed atom,
    So that I can audit and perform other activities that are time-sensitive.

  Scenario: 1: Atom timestamps for new atom for same address
    Given a library client with an active connection to a server
    When the client creates a token
    Then the client should be notified of the token creation atom with a timestamp

  Scenario: 2: Atom timestamps for old atom for same address
    Given a library client with an active connection to a server that has previously created a token
    When the client requests updates for that account
    Then the client should be notified of the token creation atom with a timestamp

  Scenario: 3: Atom timestamps for new atom for a different address
    Given identity 1 with an active connection to the network
    And identity 2 with an active connection to the network
    When identity 1 requests updates for identity 2
    And identity 2 creates a token
    Then identity 1 should be notified of the token creation atom with a timestamp
