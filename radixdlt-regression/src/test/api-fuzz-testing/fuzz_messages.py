# -*- coding: utf-8 -*-
import json
import logging, os

from fuzzer.main import run_fuzz
from fuzzer.message_generator import generator_list_huge_atoms, generator_from_payload
from tokenizer.particles import SAMPLE_ATOM
from utils.__init_ import recursive_items
from tokenizer.particles import particle_group_to_big_size
from tokenizer.quotes import tokenize_double_quotes
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
console.setLevel(logging.INFO)
formatter = logging.Formatter('[%(asctime)s][%(levelname)-8s] %(message)s')
console.setFormatter(formatter)
logging.getLogger('').addHandler(console)

#
#   User configured parameters

# The websocket address, including the protocol, for example:
#
#       ws://localhost
#       wss://localhost
#


ws_address = f"ws://{get_server_endpoint()}/rpc"

# The proxy server used to send the messages. This is very useful
# Set the os environment HTTP_PROXY to true if  proxy is used
# os.environ["HTTP_PROXY"] = "True"
http_proxy_host = 'localhost'
http_proxy_port = '8090'

# Log path, all messages are lo""gged in different files
log_path = 'output/'

# Websocket authentication message. The tool will send the authentication
# message (if included in messages below) and wait for `session_active_message`
# before sending `message`
auth_message = ''
session_active_message = ''

# The message to send to the websocket (after fuzzing)
message = open(SAMPLE_ATOM, 'r').read()

# When fuzzing `message` ignore these tokens. The tokens are part of the original
# message which shouldn't be replaced with the payloads. For example, if the
# message is:
#
#   {"foo": "bar"}
#
# And the `tokens_to_ignore` list contains "bar", then the fuzzer is not going to
# send payloads in "bar" but it will in "foo".
keys = recursive_items(json.loads(message))
tokens_to_ignore = [key for key, value in keys]

# The list containing messages to be sent to the websocket. In some cases
# You need to send two or more messages to set a specific remote state, and
# then you send the attack
init_messages = [auth_message]

# The messages to be fuzzed, these are sent in different websocket connections
# after sending the `init_messages`.
#
# Each message is fuzzed using `create_tokenized_messages`. This tokenizer
# function, together with `replace_token_in_json` needs to be customized
# if your websocket messages are NOT JSON.
original_messages = [message]

# When doing analysis of the websocket responses to try to identify exceptions
# and other errors, ignore these errors since they are common for the
# application under test
errors_to_ignore = []
params = {
    "ws_address": ws_address,
    'errors_to_ignore': errors_to_ignore,
    'log_path': log_path,
    'http_proxy_host': http_proxy_host,
    'http_proxy_port': http_proxy_port,
    'tokenized_count': 0
}
#
# run_fuzz(
#     login_messages=init_messages,
#     original_messages=original_messages,
#     tokens_to_ignore=tokens_to_ignore,
#     tokeniser_methods=[
#         particle_group_to_big_size

#     ],
#     exec_params=params,
#     message_generator=generator_list_huge_atoms
# )

print("Fuzzing big size messages test finished")
run_fuzz(
    login_messages=init_messages,
    original_messages=original_messages,
    tokens_to_ignore=tokens_to_ignore,
    tokeniser_methods=[
        tokenize_double_quotes
    ],
    exec_params=params,
    message_generator=generator_from_payload
)

print("Fuzzing tokenised submission of atom finished")
