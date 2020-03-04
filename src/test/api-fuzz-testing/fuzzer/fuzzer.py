import logging
import ssl
from multiprocessing import Queue
from concurrent import futures
from fuzzer.fuzzer_websocket import FuzzerWebSocket
from tokenizer.fuzzer_tokenizer import create_tokenized_messages
from tokenizer import TOKEN

PAYLOADS = 'data/payloads/payloads.txt'


class ThreadPoolExecutorWithQueueSizeLimit(futures.ThreadPoolExecutor):
    def __init__(self, maxsize=50, *args, **kwargs):
        super(ThreadPoolExecutorWithQueueSizeLimit, self).__init__(*args, **kwargs)
        self._work_queue = Queue(maxsize=maxsize)


def fuzz_websocket(ws_address, fuzzing_messages,
                   ignore_errors, tokenized_count, log_path,
                   http_proxy_host, http_proxy_port):
    ws = FuzzerWebSocket(ws_address,
                         fuzzing_messages,
                         ignore_errors,
                         tokenized_count,
                         log_path)
    ws.run_forever(sslopt={"cert_reqs": ssl.CERT_NONE},
                   http_proxy_host=http_proxy_host,
                   http_proxy_port=http_proxy_port)


def fuzz_multithreaded_websocket(ws_address, login_messages, original_messages, ignore_tokens, ignore_errors, output,
                                 http_proxy_host, http_proxy_port):
    payload_count = len(open(PAYLOADS).readlines())

    with futures.ThreadPoolExecutor(max_workers=20) as executor:
        for original_message in original_messages:

            logging.info(f'Fuzzing the message {original_messages} ')
            tokenized_messages = create_tokenized_messages(original_message, ignore_tokens)
            for tokenized_count, tokenized_message in enumerate(tokenized_messages):
                with open(PAYLOADS, 'r') as f:
                    for payload in f:
                        message_applied_with_tokens = replace_token_in_json(payload, tokenized_message)
                        logging.debug(f'Generated fuzzed message: {message_applied_with_tokens}')
                        fuzzing_messages = login_messages[:]
                        fuzzing_messages.append(message_applied_with_tokens)
                        executor.submit(fuzz_websocket, ws_address, fuzzing_messages, ignore_errors, 0, output,
                                        http_proxy_host, http_proxy_port)


def fuzz_multithreaded_atom(ws_address, login_messages, original_messages, ignore_tokens, ignore_errors, output,
                            http_proxy_host, http_proxy_port):
    payload_count = len(open(PAYLOADS).readlines())

    with futures.ThreadPoolExecutor(max_workers=10) as executor:
        for original_message in original_messages:

            logging.info(f'Fuzzing the message {original_messages} ')
            tokenized_messages = create_tokenized_messages(original_message, ignore_tokens)
            for tokenized_count, tokenized_message in enumerate(tokenized_messages):
                for i in range(20):
                    message_applied_with_tokens = tokenized_message
                    logging.debug(f'Generated fuzzed message: {message_applied_with_tokens}')
                    fuzzing_messages = login_messages[:]
                    fuzzing_messages.append(message_applied_with_tokens)
                    executor.submit(fuzz_websocket, ws_address, fuzzing_messages, ignore_errors, 0, output, http_proxy_host,
                                    http_proxy_port)


def replace_token_in_json(payload, tokenized_message):
    # Escape any quotes which are in the payload
    payload = payload.strip()
    payload = payload.replace('"', '\\"')

    # Do the replace
    modified_message = tokenized_message.replace(TOKEN, payload)
    return modified_message
