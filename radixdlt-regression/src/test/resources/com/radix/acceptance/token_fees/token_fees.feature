Feature: Token fees required for submitted transactions
  As a radix user,
  I would like valid transactions to require a radix token fee,
  So that the network is maintained

  Scenario: 1. Fixed Fees
    Given I have an account with funds at a suitable Radix network
    When I submit 10 transactions
    Then I can observe that I have paid 1XRD in fees