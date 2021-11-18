var n1 = nodeBuilder().p2pServer(30304).build();
var n2 = nodeBuilder().p2pServer(30305).build();
n1.connectTo(n2.self());
