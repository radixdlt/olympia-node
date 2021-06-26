def post_headers():
    headers = {
        'content-length': '168',
        'x-radixdlt-correlation-id': 'c85be8a4-25f2-49a8-9a6b-81267b1f59f1',
        'user-agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 11_4_0) AppleWebKit/537.36 (KHTML, like Gecko) radix-olympia-desktop-wallet/0.7.6 Chrome/89.0.4389.90 Electron/12.0.2 Safari/537.36',
        'x-radixdlt-method': 'account.get_transaction_history',
        'content-type': 'application/json',
        'accept': '*/*',
        'origin': 'app://.',
        'sec-fetch-site': 'cross-site',
        'sec-fetch-mode': 'cors',
        'sec-fetch-dest': 'empty',
        'accept-encoding': 'gzip, deflate, br',
        'accept-language': 'en-GB',
    }
    return headers


def logOnError(response):
    req = response.request
    """
    Ignore this function for now, but potentially could be used for debugging script as locust script cannot be debugged in IDE
    """
    print('\n{}\n{}\n{}\n\n{}'.format(
        '-----------Request-----------',
        req.method + ' ' + req.url,
        '\n'.join('{}: {}'.format(k, v) for k, v in req.headers.items()),
        req.body,
    ))

    print('\n{}\n{}\n{}\n\n{}'.format(
        '-----------Response-----------',
        'Response code:' + str(response.status_code),
        '\n'.join('{}: {}'.format(k, v) for k, v in response.headers.items()),
        response.text,
    ))
