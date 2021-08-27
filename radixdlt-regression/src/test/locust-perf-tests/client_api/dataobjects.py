class RequestData():
    @staticmethod
    def get_transaction_history():
        return {
            "jsonrpc": "2.0",
            "id": "0",
            "method": "account.get_transaction_history",
            "params":
                {"address": "brx1qspm44wuhymwwse4jg8y4y8nkj657e3g4659hw53cgg59mkp74w85hqyapytf",
                 "size": 50}
        }

    @staticmethod
    def get_native_token():
        return {
            "jsonrpc": "2.0",
            "method": "tokens.get_native_token",
            "params": {},
            "id": 1
        }
    @staticmethod
    def get_xrd_token_info():
        return {
            "jsonrpc": "2.0",
            "method": "tokens.get_info",
            "params": {
                "rri": "xrd_tr31qye87xgq"
            },
            "id": 1
        }
    @staticmethod
    def get_account_balance():
        return {
            "jsonrpc": "2.0",
            "method": "account.get_balances",
            "params": {
                "address": "tdx31qspmhnpg6ls2pxdk595aey4gwslp8qqeyhfaa9cjryxx77tnlup637cmdq9cw"
            },
            "id": 1
        }
    @staticmethod
    def get_transaction_history_r():
        return {
            "jsonrpc": "2.0",
            "method": "account.get_transaction_history",
            "params": {
                "address": "tdx31qspmhnpg6ls2pxdk595aey4gwslp8qqeyhfaa9cjryxx77tnlup637cmdq9cw",
                "size": 250
            },

            "id": 1
        }
    @staticmethod
    def get_lookup_validator():
        return {
            "jsonrpc": "2.0",
            "method": "validators.lookup_validator",
            "params": {
                "validatorAddress": "tv31qfecr56jelx7pt462e2hpylrfjklgha8k5a850aha0p4p5pl4fmzxywlptr"
            },
            "id": 1
        }

    @staticmethod
    def get_archive_network_id():
        return { "jsonrpc": "2.0", "method": "network.get_id", "params": [], "id": 1}

    @staticmethod
    def build_transaction():
        return {
            "method": "construction.build_transaction",
            "params": {
                "actions": [

                ],
                "feePayer": ""
            },
            "id": 0
        }

    @staticmethod
    def finalise_transaction():
        return {
            "method": "construction.finalize_transaction",
            "params": [

            ],
            "id": 0
        }

    @staticmethod
    def submit_transaction():
        return {
            "method": "construction.submit_transaction",
            "params": [

            ],
            "id": 0
        }
