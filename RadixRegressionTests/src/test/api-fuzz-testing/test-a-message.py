# -*- coding: utf-8 -*-
import logging, os

from fuzzer.main import fuzz_websocket
from utils.utils import get_server_endpoint
#
#   Configure logging
#
logging.basicConfig(level=logging.DEBUG,
                    format='[%(asctime)s][%(levelname)-8s] %(message)s',
                    datefmt='%m-%d %H:%M',
                    filename='output.log',
                    filemode='w')

console = logging.StreamHandler()
console.setLevel(logging.DEBUG)
formatter = logging.Formatter('[%(asctime)s][%(levelname)-8s] %(message)s')
console.setFormatter(formatter)
logging.getLogger('').addHandler(console)

#
#   User configured parameters
#

# The websocket address, including the protocol, for example:
#
#       ws://localhost
#       wss://localhost
#
ws_address = f"ws://{get_server_endpoint()}/rpc"

# The proxy server used to send the messages. This is very useful
# for debugging the tools
# Set the os environment HTTP_PROXY to true if  proxy is used
# os.environ["HTTP_PROXY"] = "True"
http_proxy_host = 'localhost'
http_proxy_port = '8090'

# Log path, all messages are logged in different files
log_path = 'output/'

# Websocket authentication message. The tool will send the authentication
# message (if included in messages below) and wait for `session_active_message`
# before sending `message`
auth_message = ''
session_active_message = ''

# The message to send to the websocket
message = '{"id":"6e377df2-0db1-4cf6-83b5-921359a8150e","method":"Atoms.submitAtom","params":{"metaData":{"powNonce":":str:65865","timestamp":":str:1581592870305"},"serializer":"radix.atom","version":100,"particleGroups":[{"serializer":"radix.particle_group","particles":[{"spin":-1,"serializer":"radix.spun_particle","particle":{"destinations":[":uid:a23d5114ed1175b08cd330f31e1ec08d"],"rri":":rri:/JHCXM7sGgVM5unZafsPQyRMF1FQ7Pt1HMs62MrT4GGPFraUc7Cq/123+DEF","serializer":"radix.particles.rri","version":100,"nonce":0},"version":100},{"spin":1,"serializer":"radix.spun_particle","particle":{"symbol":":str:123+DEF","address":":adr:JHCXM7sGgVM5unZafsPQyRMF1FQ7Pt1HMs62MrT4GGPFraUc7Cq","granularity":":u20:1000000000000000000","permissions":{"burn":":str:token_owner_only","mint":":str:token_owner_only"},"destinations":[":uid:a23d5114ed1175b08cd330f31e1ec08d"],"name":":str:RLAU-40 Test token","serializer":"radix.particles.token_definition","description":":str:RLAU-40 Test token","version":100},"version":100},{"spin":1,"serializer":"radix.spun_particle","particle":{"amount":":u20:115792089237316195423570985008687907853269984665640564039457584007913129639935","granularity":":u20:1000000000000000000","permissions":{"burn":":str:token_owner_only","mint":":str:token_owner_only"},"destinations":[":uid:a23d5114ed1175b08cd330f31e1ec08d"],"serializer":"radix.particles.unallocated_tokens","version":100,"nonce":1581592870304,"tokenDefinitionReference":":rri:/JHCXM7sGgVM5unZafsPQyRMF1FQ7Pt1HMs62MrT4GGPFraUc7Cq/123+DEF"},"version":100}],"version":100},{"serializer":"radix.particle_group","particles":[{"spin":-1,"serializer":"radix.spun_particle","particle":{"amount":":u20:115792089237316195423570985008687907853269984665640564039457584007913129639935","granularity":":u20:1000000000000000000","permissions":{"burn":":str:token_owner_only","mint":":str:token_owner_only"},"destinations":[":uid:a23d5114ed1175b08cd330f31e1ec08d"],"serializer":"radix.particles.unallocated_tokens","version":100,"nonce":1581592870304,"tokenDefinitionReference":":rri:/JHCXM7sGgVM5unZafsPQyRMF1FQ7Pt1HMs62MrT4GGPFraUc7Cq/123+DEF"},"version":100},{"spin":1,"serializer":"radix.spun_particle","particle":{"amount":":u20:1000000000000000000000000000","address":":adr:JHCXM7sGgVM5unZafsPQyRMF1FQ7Pt1HMs62MrT4GGPFraUc7Cq","granularity":":u20:1000000000000000000","permissions":{"burn":":str:token_owner_only","mint":":str:token_owner_only"},"destinations":[":uid:a23d5114ed1175b08cd330f31e1ec08d"],"serializer":"radix.particles.transferrable_tokens","version":100,"nonce":43054399378263,"tokenDefinitionReference":":rri:/JHCXM7sGgVM5unZafsPQyRMF1FQ7Pt1HMs62MrT4GGPFraUc7Cq/123+DEF","planck":26419881},"version":100},{"spin":1,"serializer":"radix.spun_particle","particle":{"amount":":u20:115792089237316195423570985008687907853269984665639564039457584007913129639935","granularity":":u20:1000000000000000000","permissions":{"burn":":str:token_owner_only","mint":":str:token_owner_only"},"destinations":[":uid:a23d5114ed1175b08cd330f31e1ec08d"],"serializer":"radix.particles.unallocated_tokens","version":100,"nonce":1581592870304,"tokenDefinitionReference":":rri:/JHCXM7sGgVM5unZafsPQyRMF1FQ7Pt1HMs62MrT4GGPFraUc7Cq/123+DEF"},"version":100}],"version":100}],"signatures":{"a23d5114ed1175b08cd330f31e1ec08d":{"r":":byt:Lx9DFIvx83gPlN5ek/uMnSR99xz7u7066FejAOjyU8o\u003d","s":":byt:LbHuqFUYOfpu2nqC41aVA7uiWMWzL8HivmCj5flTv4w\u003d","serializer":"crypto.ecdsa_signature","version":100}}}}'

# The list containing all messages to be sent to the websocket. In some cases
# You need to send two or more messages to set a specific remote state, and
# then you send the attack
messages_to_send = [
    message,
]

# When doing analysis of the websocket responses to try to identify exceptions
# and other errors, ignore these errors since they are common for the
# application under test
errors_to_ignore = []

#
#   Do not touch these lines
#
fuzz_websocket(ws_address=ws_address,
               fuzzing_messages=messages_to_send,
               errors_to_ignore=errors_to_ignore,
               tokenized_count=0,
               log_path=log_path,
               http_proxy_host=http_proxy_host,
               http_proxy_port=http_proxy_port)
