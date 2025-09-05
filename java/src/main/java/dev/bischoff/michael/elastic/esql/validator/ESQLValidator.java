package dev.bischoff.michael.elastic.esql.validator;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.misc.IntervalSet;
import dev.bischoff.michael.elastic.esql.parser.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ESQLValidator {

    public static void main(String[] args) throws IOException {
        String inputText;
        if (args.length == 1) {
            try {
                inputText = new String(Files.readAllBytes(Paths.get(args[0])));
            } catch (IOException e) {
                inputText = args[0];
            }
        } else {
            System.out.println("Enter input to validate (Ctrl-D to end):");
            inputText = new String(System.in.readAllBytes());
        }
        System.exit(validateInput(inputText) ? 0 : 1);
    }
    public static boolean validateInput(String text) {
        try {
            CharStream input = CharStreams.fromString(text);
            EsqlBaseLexer lexer = new EsqlBaseLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            EsqlBaseParser parser = new EsqlBaseParser(tokens);
    
            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer,
                                        Object offendingSymbol,
                                        int line, int charPositionInLine,
                                        String msg, RecognitionException e) {
    
                    String expectedTokens = "";
    
                    if (recognizer instanceof Parser) {
                        Parser p = (Parser) recognizer;
                        IntervalSet expectedTokenSet = p.getExpectedTokens();
                        expectedTokens = expectedTokenSet.toString(p.getVocabulary());
                    }
    
                    throw new RuntimeException(
                        String.format(
                            "Syntax error at line %d:%d - %s.",
                            line, charPositionInLine,
                            msg
                        )
                    );
                }
            });
    
            // Call top-level rule directly (hardcoded)
            parser.singleStatement();
    
            System.out.println("Input is valid ✅");
            return true;
    
        } catch (RuntimeException e) {
            // Catch syntax errors from the error listener
            System.err.println("Input is invalid ❌");
            System.err.println(e.getMessage());
            return false;
        } catch (Exception e) {
            // Catch other unexpected exceptions
            System.err.println("Unexpected error ❌");
            e.printStackTrace();
            return false;
        }
    }    
}
