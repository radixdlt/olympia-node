from datetime import datetime

import websocket
import threading
import logging
import time

from websocket._app import WebSocketApp
from websocket._exceptions import WebSocketConnectionClosedException

from analyser.response_analyzer import ResponseAnalyzer
from utils import utils
from fuzzer.logger import FuzzerLogger, log, json_highlight, json_indent, OUTGOING, INCOMING


class FuzzerWebSocket(WebSocketApp):
    def __init__(self, url: str, client_messages: list, errors_to_ignore: list, tokenized_count: int, log_path: str):
        params = {
            "on_message": self.on_message,
            'on_error': self.on_error,
            'on_close': self.on_close,
            'on_open': self.on_open
        }

        super(FuzzerWebSocket, self).__init__(url, **params)
        self._id = utils.getID()
        log_filename = self._id
        self.log_file = FuzzerLogger(tokenized_count, log_path, log_filename)
        self.client_messages = client_messages
        self.messages_awaiting_responses = 0
        self.inspect_response = False
        self.latest_message_timestamp = None
        self.latest_message_sent_timestamp = None
        self.errors_to_ignore = errors_to_ignore
        self.response_analyzer = ResponseAnalyzer()

    def on_message(self, ws, message) -> None:
        self.messages_awaiting_responses = max(self.messages_awaiting_responses - 1, 0)
        log(self, message, INCOMING);

        # if self.inspect_response:
        #     anaylse_response(message);

        if self.inspect_response:
            found_interesting = self.analyze_response(message)
            if found_interesting:
                msg = 'Potential issue found in connection with ID %s: %s'
                logging.warning(msg % (self._id, message))

    def analyze_response(self, response: str) -> bool:
        return self.response_analyzer.log_response_if_relevant(response, self.errors_to_ignore)

    def on_error(self, ws, error) -> None:
        log(self, '/error %s ' % error)

    def on_close(self, ws) -> None:
        log(self, 'closed connection')

    def on_open(self, ws) -> None:
        logging.debug("Successfully opened connection")
        self.send_message(self.client_messages[0])

        t = threading.Thread(target=self.send_login_and_payloads, )
        t.start()

    def send_login_and_payloads(self) -> None:
        self.wait_on_messages()

        for message in self.messages_to_fuzz():
            # Send the payload(s)
            self.send_message(message, inspect_response=True)

            # Wait for the payload response
            self.wait_on_messages()

            # All done, close the connection!
        logging.debug('All answers received! Closing connection.')

        try:
            self.close()
        except:
            pass

    def wait_on_messages(self) -> None:
        start_timestamp = time.time()
        while self.messages_awaiting_responses > 0:
            time.sleep(0.1)

            spent_time = time.time() - start_timestamp
            if spent_time > 5:
                logging.debug('Timed out waiting for answers')
                break

            waited_since_last_sent_message = time.time() - self.latest_message_sent_timestamp
            if waited_since_last_sent_message < 2.0:
                continue

            if self.messages_awaiting_responses == 0:
                break

    def send_message(self, message, inspect_response=False) -> None:
        log(self, message, direction=OUTGOING)
        self.messages_awaiting_responses += 1
        self.inspect_response = inspect_response
        self.latest_message_sent_timestamp = time.time()

        try:
            self.send(message)
        except WebSocketConnectionClosedException:
            msg = '(%s) Connection was closed when trying to send message'
            logging.error(msg % self._id)

    def messages_to_fuzz(self):
        for message in self.client_messages[1:]:
            yield message
