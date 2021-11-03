Feature: Fees required for submitted transactions
  As a radix network,
  I would like transactions to require a radix token fee,
  So that the network can be maintained

  Scenario: 1. Token transfer with small message attached
    Given I have an account with funds at a suitable Radix network
    When I transfer 5 XRD, attaching a small message to the transaction
    Then I can observe that I have paid fees proportional to the message bytes

  Scenario: 2. Token transfer with large message message attached
    Given I have an account with funds at a suitable Radix network
    When I transfer 5 XRD, attaching a large message to the transaction
    Then I can observe that I have paid fees proportional to the message bytes

  Scenario: 3. Fixed token creation fees
    Given I have an account with 110 XRD at a suitable Radix network
    When I create a fixed supply token
    Then I can observe that I have paid 100 extra XRD in fees

  Scenario: 4. Mutable token creation fees
    Given I have an account with 110 XRD at a suitable Radix network
    When I create a mutable supply token
    Then I can observe that I have paid 100 extra XRD in fees