from fuzzer.main import PAYLOADS
from tokenizer import TOKEN
from utils.utils import get_max_threads


def generator_from_payload(tokenized_message: str) -> list:
    message_applied_with_tokens = []
    with open(PAYLOADS, 'r') as f:
        for payload in f:
            message_applied_with_tokens.append(replace_token_in_json(payload, tokenized_message))
    return message_applied_with_tokens


def generator_list_huge_atoms(huge_atom_message: str):
    for i in range(get_max_threads()):
        yield huge_atom_message


def replace_token_in_json(payload: str, tokenized_message: str) -> str:
    # Escape any quotes which are in the payload
    payload = payload.strip()
    payload = payload.replace('"', '\\"')

    # Do the replace
    modified_message = tokenized_message.replace(TOKEN, payload)
    return modified_message
