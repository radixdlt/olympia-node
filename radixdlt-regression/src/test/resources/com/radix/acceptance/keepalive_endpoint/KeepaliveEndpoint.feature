# See: https://radixdlt.atlassian.net/browse/RLAU-67
@rlau-67
Feature: Keep-alive Endpoint
  As a Library Developer
  I want to make sure that my web socket connection is maintained even when there is no data being passed back and forth
  So that I don’t have to manage reconnecting over and over again

  Scenario: 1: Ping
    Given that I have established a websocket connection to a node
    When I call the keep-alive endpoint
    Then I should receive a small reply confirming that the connection is still active