import logging


class ResponseAnalyzer:
    def __init__(self):

        self.relevancy_lists = {
            "general": [
                'exception',
                'sql',
                'xml',
                'ldap',
                'root:x:',
                'root:!:',
                'daemon:',
                'bytes from 127.0.0.1',
                'trace',
                'groups=',
                '%p.%p.%p',
                'drwxrwxr',
                'backtrace',
                'memory map',
                'Protocol error occurred',
                'Size limit has exceeded',
                'An inappropriate matching occurred',
                'A constraint violation occurred',
                'The syntax is invalid',
                'Object does not exist',
                'The alias is invalid',
                'The distinguished name has an invalid syntax',
                'The server does not handle directory requests',
                'There was a naming violation',
                'There was an object class violation',
                'Results returned are too large',
                'Unknown error occurred',
                'Local error occurred',
                'The search filter is incorrect',
                'The search filter is invalid',
                'The search filter cannot be recognized',

            ],
            'ldap': [
                'supplied argument is not a valid ldap',
                'Invalid DN syntax',
                'No Such Object',
                'LDAPException',
                'com.sun.jndi.ldap',
                # http://www.tisc-insight.com/newsletters/58.html
                'IPWorksASP.LDAP',
            ],
            'Java': [
                'javax.naming.NameNotFoundException',
                'java.sql.SQLException',
                'Unexpected end of command in statement',
            ],
            'php': [
                'Bad search filter',
                'fread\\(\\):',
                'for inclusion \'\\(include_path=',
                'Failed opening required',
                '<b>Warning</b>:  file\\(',
                '<b>Warning</b>:  file_get_contents\\(',
                'open_basedir restriction in effect',
            ],
            'ASP': [
                'System.Data.OleDb.OleDbException',
                '[SQL Server]',
                '[Microsoft][ODBC SQL Server Driver]',
                '[SQLServer JDBC Driver]',
                '[SqlException',
                'System.Data.SqlClient.SqlException',
                'Unclosed quotation mark after the character string',
                "'80040e14'",
                'mssql_query()',
                'odbc_exec()',
                'Microsoft OLE DB Provider for ODBC Drivers',
                'Microsoft OLE DB Provider for SQL Server',
                'Incorrect syntax near',
                'Sintaxis incorrecta cerca de',
                'Syntax error in string in query expression',
                'ADODB.Field (0x800A0BCD)<br>',
                "ADODB.Recordset'",
                "Unclosed quotation mark before the character string",
                "'80040e07'",
                'Microsoft SQL Native Client error',
                'SQL Server Native Client',
                'Invalid SQL statement',
            ],
            'DB2': [
                'SQLCODE',
                'DB2 SQL error:',
                'SQLSTATE',
                '[CLI Driver]',
                '[DB2/6000]',
            ],
            'Sybase': [
                "Sybase message:",
                "Sybase Driver",
                "[SYBASE]",
            ],
            'Certs': [
                '-----BEGIN CERTIFICATE-----',
                '-----BEGIN RSA PRIVATE KEY-----'
            ]

        }

    @staticmethod
    def __search_for(response: str, in_list: list, named: str) -> bool:
        relevancy_list = in_list
        name_of_relevancy_list = named

        for index_of_relevant_string, relevant_string in enumerate(relevancy_list):
            if relevant_string in response:
                print(
                    f"Found relevant_string='{relevant_string}' in list='{name_of_relevancy_list}'")
                return True

        return False

    def log_response_if_relevant(self, response: str, errors_to_ignore: list = [],
                                 is_case_sensitive: bool = False) -> bool:
        if not is_case_sensitive:
            response = response.lower()

        for error_to_ignore in errors_to_ignore:
            if error_to_ignore in response:
                return False

        for list_name, relevancy_list in self.relevancy_lists.items():
            if self.__search_for(response, in_list=relevancy_list, named=list_name):
                return True

        return False
