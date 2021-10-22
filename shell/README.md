# radix-shell

Run from the project root dir: `./shell/radix-shell.sh`.

In jshell, use `/open` to load the examples, f.e.:
`/open ./shell/examples/start_two_nodes.java`

Use jshell to interact with the nodes:
* `n1.peers()` - to show the peers connected to n1
* `n2.onMsg(PeerPongMessage.class, m -> System.out.println("Received Pong message"))` - print received Pong messages on n2
* `n2.sendMsg(n1.self(), new PeerPingMessage())` - to send a ping message from n2 to n1.

See `examples` directory for more.
