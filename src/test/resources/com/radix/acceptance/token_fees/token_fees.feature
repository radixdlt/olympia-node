# See: https://radixdlt.atlassian.net/browse/RPNV1-185
@rpnv1-185
Feature: Token fees required for submitted atoms
  As a token buyer,
  I would like valid transactions to require a radix token fee,
  So that the network is not overrun with spam

  For the purpose of these acceptance criteria we will work with the following fee table:
  Minimum fee: 40 millirads (0.040 rads)
  1 millirad per byte after the first 3072 bytes
  1,000 millirad for creating a fixed or mutable supply token

Scenario: 1. Atom without fee is rejected
  Given I have a connection to a Radix network,
  When I submit an atom without any fees,
  Then I can see that atom being rejected by the network

Scenario: 2. Atom with insufficient fee is rejected
  Given I have a connection to a Radix network,
  When I submit an atom with a fee that is too small,
  Then I can see that atom being rejected by the network

Scenario: 3. Atom with minimum fee is accepted
  Given I have a connection to a Radix network,
  When I submit an atom with a fee that equals the minimum fee,
  Then I can see that atom being accepted by the network

Scenario: 4. Atom with more than minimum fee is accepted
  Given I have a connection to a Radix network,
  When I submit an atom with a fee that exceeds the minimum fee,
  Then I can see that atom being accepted by the network

Scenario: 5. Minimum fee charged for small atom
  Given I have a connection to a Radix network,
  When I create an atom with a size smaller than 3072 bytes,
  And I submit that atom to the network with the computed minimum fee,
  Then I can see that the fee is 40 millirads

Scenario: 6. Greater than minimum fee for larger atom 
  Given I have a connection to a Radix network,
  When I create an atom with a size larger than 3072 bytes,
  And I submit that atom to the network with the computed minimum fee,
  Then I can see that the fee is greater than 40 millirads

Scenario: 7. Atom creating mutable supply charged higher fee
  Given I have a connection to a Radix network,
  When I create an atom that creates a mutable supply token,
  And I submit that atom to the network with the computed minimum fee,
  Then I can see that the fee is at least 1 rad

Scenario: 8. Atom creating fixed supply charged higher fee
  Given I have a connection to a Radix network,
  When I create an atom that creates a fixed supply token,
  And I submit that atom to the network with the computed minimum fee,
  Then I can see that the fee is at least 1 rad
