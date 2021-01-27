import os
import logging
import time
import json

from termcolor import colored
from pygments import highlight
from pygments.lexers import JsonLexer
from pygments.formatters import TerminalFormatter

OUTGOING = 'outgoing'
INCOMING = 'incoming'


class FuzzerLogger(object):
    def __init__(self, tokenized_count, log_path, log_filename):
        self.tokenized_count = tokenized_count
        self.log_path = log_path
        self.log_filename = log_filename
        self.counter = 0

        self.makedirs()

    def makedirs(self) -> None:
        output_path = '%s/%s/' % (self.log_path, self.tokenized_count)

        if not os.path.exists(output_path):
            os.makedirs(output_path)

    def get_filename(self) -> str:
        return '%s/%s/%s-%s.log' % (self.log_path,
                                    self.tokenized_count,
                                    self.log_filename,
                                    self.counter)

    def write(self, message) -> None:
        filename = self.get_filename()
        self.counter += 1
        open(filename, 'w').write(message)


def log(fuzzer, message, direction=None) -> None:
    fuzzer.log_file.write(json_indent(message))

    if direction in (OUTGOING, INCOMING):
        if fuzzer.latest_message_timestamp is None:
            relative_timestamp = 0.0
        else:
            relative_timestamp = time.time() - fuzzer.latest_message_timestamp
        fuzzer.latest_message_timestamp = time.time()

        message = json_highlight(message)

        if direction == OUTGOING:
            logging.debug('(%.4f)(%s)%s %s' % (relative_timestamp, fuzzer._id, colored('>>>', 'green'), message))
        elif direction == INCOMING:
            logging.debug('(%.4f)(%s)%s %s' % (relative_timestamp, fuzzer._id, colored('<<<', 'red'), message))
    else:
        logging.debug('(%s) %s' % (fuzzer._id, message))


def json_highlight(message):
    try:
        json_object = json.loads(message)
    except:
        return message
    else:
        json_str = json.dumps(json_object, indent=4, sort_keys=True)
        highlighted = highlight(json_str, JsonLexer(), TerminalFormatter())
        return highlighted + '\n\n'


def json_indent(message):
    try:
        json_object = json.loads(message)
    except:
        return message
    else:
        json_str = json.dumps(json_object, indent=4, sort_keys=True)
        return json_str + '\n\n'
