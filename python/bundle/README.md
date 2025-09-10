# esql-parser

A Python package providing an ANTLR4-based parser and lexer for
**Elasticsearch ESQL (Event Query Language)**.

The grammar is derived from the official [Elasticsearch repository](https://github.com/elastic/elasticsearch)
and adapted for the Python ANTLR runtime.

## Installation

```bash
pip install esql-parser
```

## Usage
```
from antlr4 import InputStream, CommonTokenStream
from esql_parser.EsqlBaseLexer import EsqlBaseLexer
from esql_parser.EsqlBaseParser import EsqlBaseParser

# Example: parse a simple ESQL query
input_stream = InputStream("FROM logs | STATS count(*) BY host")
lexer = EsqlBaseLexer(input_stream)
token_stream = CommonTokenStream(lexer)
parser = EsqlBaseParser(token_stream)

tree = parser.singleStatement()
print(tree.toStringTree(recog=parser))
```

## Versioning

The version number of esql-parser matches the corresponding
Elasticsearch version from which the grammar was taken
(e.g. esql-parser==9.1.3 → Elasticsearch 9.1.3).