import re

from tokenizer import TOKEN

QUOTES_RE = re.compile('"(.*?)"')


def tokenize_double_quotes(ws_message: str, tokens_to_ignore: list) -> list:
    tokenized = []

    for match in QUOTES_RE.finditer(ws_message):
        start, end = match.span()

        should_tokenize = True

        for ignore_token in tokens_to_ignore:
            if ignore_token == ws_message[start + 1:end - 1]:
                should_tokenize = False
                break

        if not should_tokenize:
            continue

        tokenized.append(ws_message[:start + 1] + TOKEN + ws_message[end - 1:])

    return tokenized
