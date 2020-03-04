from tokenizer.quotes import tokenize_double_quotes
from tokenizer.particles import create_fuzzed_particle_group
TOKENIZER_METHODS = [
    #tokenize_method_name
   create_fuzzed_particle_group,
   tokenize_double_quotes,
]


def create_tokenized_messages(original_message, ignore_tokens,methods = None):
    tokenized_messages = []

    for tokenizer_method in TOKENIZER_METHODS:
        tokenized_messages.extend(tokenizer_method(original_message, ignore_tokens))

    return tokenized_messages