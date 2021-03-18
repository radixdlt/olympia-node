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

Scenario: 6. Atom creating mutable supply charged higher fee
  Given I have a connection to a Radix network,
  When I create an atom that creates a mutable supply token,
  And I submit that atom to the network with the computed minimum fee,
  Then I can see that the fee is at least 1 rad

Scenario: 7. Atom creating fixed supply charged higher fee
  Given I have a connection to a Radix network,
  When I create an atom that creates a fixed supply token,
  And I submit that atom to the network with the computed minimum fee,
  Then I can see that the fee is at least 1 rad

Scenario: 8. Atoms with handcrafted fee groups are accepted by the network
  Given I have a connection to a Radix network,
  When I submit an atom with a handcrafted fee group,
  Then I can see that atom being accepted by the network

Scenario: 9. Atoms with fee groups with output ttps are rejected by the network
  Given I have a connection to a Radix network,
  When I submit an atom with a fee group with two output TransferrableTokensParticles,
  Then I can see that atom being rejected by the network

Scenario: 10. Atoms with fee groups with input ttps smaller than output ttp are rejected by the network
  Given I have a connection to a Radix network,
  When I submit an atom with a fee group that has an input TransferrableTokensParticle with a smaller value than the output TransferrableTokensParticle,
  Then I can see that atom being rejected by the network

#Scenario: 11. Atom required fee can be calculated and atom with a smaller fee is rejected
  #Given I have a connection to a Radix network,
  #When I call a Radix API method to calculate the required fee for an atom that creates a fixed supply token,
  #And I submit this atom with a smaller fee than that returned by the service,
  #Then I can see that atom being rejected by the network

Scenario: 12. Atom required fee can be calculated and submitted atom is accepted
  Given I have a connection to a Radix network,
  When I call a Radix API method to calculate the required fee for an atom that creates a fixed supply token,
  And I submit this atom with a fee as returned by the service,
  Then I can see that atom being accepted by the network

#Scenario: 13. Particle fee can be calculated by comparing two atoms fees
  #Given I have a connection to a Radix network,
  #When I call a Radix API method to calculate the required fee for an atom that creates a fixed supply token,
  #And I add another particle that creates a fixed supply token to that atom and ask the service for required fee again,
  #Then I can calculate the fee for that extra particle
