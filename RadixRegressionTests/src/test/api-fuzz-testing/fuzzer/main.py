import logging
import ssl, os
from concurrent import futures
from fuzzer.websocket import FuzzerWebSocket
from tokenizer.main import create_tokenized_messages
from utils.utils import get_max_threads

PAYLOADS = 'data/payloads/payloads.txt'


def fuzz_websocket(ws_address: str, fuzzing_messages: list,
                   errors_to_ignore: list, tokenized_count: int, log_path: str,
                   http_proxy_host: str, http_proxy_port: str) -> None:
    ws = FuzzerWebSocket(ws_address,
                         fuzzing_messages,
                         errors_to_ignore,
                         tokenized_count,
                         log_path)
    if os.getenv('HTTP_PROXY'):
        ws.run_forever(sslopt={"cert_reqs": ssl.CERT_NONE},
                       http_proxy_host=http_proxy_host,
                       http_proxy_port=http_proxy_port)
    else:
        ws.run_forever(sslopt={"cert_reqs": ssl.CERT_NONE})


def apply_generator_and_execute(tokenized_messages: list, login_messages: list, message_generator: callable, executor,
                                exec_params: dict) -> None:
    for tokenized_count, tokenized_message in enumerate(tokenized_messages):
        for message in message_generator(tokenized_message):
            fuzzing_messages = login_messages[:]
            logging.debug(f'Generated fuzzed message: {message}')
            fuzzing_messages.append(message)
            executor.submit(fuzz_websocket, fuzzing_messages=fuzzing_messages, **exec_params)


def run_fuzz(login_messages: list, original_messages: list, tokens_to_ignore: list,
             tokeniser_methods: list, exec_params: dict, message_generator: callable) -> None:
    with futures.ThreadPoolExecutor(max_workers=get_max_threads()) as executor:
        for original_message in original_messages:
            logging.info(f'Fuzzing the message {original_message} ')
            tokenized_messages = create_tokenized_messages(original_message, tokens_to_ignore, tokeniser_methods)
            apply_generator_and_execute(tokenized_messages, login_messages, message_generator=message_generator,
                                        executor=executor,
                                        exec_params=exec_params)
