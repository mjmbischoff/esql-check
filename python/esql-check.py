import sys
import inspect
from antlr4 import InputStream, CommonTokenStream
from antlr4.error.ErrorListener import ErrorListener

# Import generated classes
from grammar.EsqlBaseLexer import EsqlBaseLexer
from grammar.EsqlBaseParser import EsqlBaseParser


class BailErrorListener(ErrorListener):
    def syntaxError(self, recognizer, offendingSymbol, line, column, msg, e):
        raise SyntaxError(f"Syntax error at line {line}, column {column}: {msg}")

def validate_input(text: str):
    """Return True if input is valid, False otherwise."""
    input_stream = InputStream(text)
    lexer = EsqlBaseLexer(input_stream)
    stream = CommonTokenStream(lexer)
    parser = EsqlBaseParser(stream)
    parser.removeErrorListeners()
    parser.addErrorListener(BailErrorListener())


    parse_method = parser.singleStatement    

    try:
        parse_method()
        return True
    except SyntaxError as e:
        print(e)
        return False

def main():
    if len(sys.argv) == 2:
        arg = sys.argv[1]
        try:
            # Try reading as a file first
            with open(arg, 'r') as f:
                data = f.read()
        except FileNotFoundError:
            # Treat as direct input string
            data = arg
    else:
        # Read from stdin
        print("Enter input to validate (Ctrl-D to end):")
        data = sys.stdin.read()

    if validate_input(data):
        print("Input is valid ✅")
        sys.exit(0)
    else:
        print("Input is invalid ❌")
        sys.exit(1)

if __name__ == "__main__":
    main()
