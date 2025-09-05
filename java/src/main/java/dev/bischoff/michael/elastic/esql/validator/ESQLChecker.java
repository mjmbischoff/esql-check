package dev.bischoff.michael.elastic.esql.validator;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.IntervalSet;
import dev.bischoff.michael.elastic.esql.parser.*;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

@Command(
        name = "esql-check",
        mixinStandardHelpOptions = true,
        version = "esql-check 0.1",
        description = "Validates ElasticSearch ESQL queries."
)
public class ESQLChecker implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Optional raw ESQL or JSON/TOML content")
    private String input;

    @Option(names = "--files", description = "Glob pattern(s) for ESQL files to validate")
    List<String> files = List.of();

    static class InputFormat {
        @Option(names = "--json", description = "Validate ESQL inside a JSON field with the given name")
        String jsonField;

        @Option(names = "--toml", description = "Validate ESQL inside a TOML field with the given name")
        String tomlField;

        private String extract(InputStream in) throws Exception {
            if (jsonField != null) {
                return new JsonFieldExtractor().extract(in, jsonField);
            } else if (tomlField != null) {
                return new TomlFieldExtractor().extract(in, tomlField);
            } else {
                return new String(in.readAllBytes(), UTF_8);
            }
        }
    }

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    InputFormat inputFormat = new InputFormat();

    public static void main(String[] args) {
        var commandLine = new CommandLine(new ESQLChecker());
        commandLine.setExecutionExceptionHandler((e, commandLine1, parseResult) -> {
            switch (e) {
                case IOException ioe -> {
                    System.err.println("I/O error: " + ioe.getClass().getName() + "message: '" + ioe.getMessage() + "'");
                    return 10;
                }
                case IllegalArgumentException iae -> {
                    System.err.println("Input format error ❌: " + iae.getMessage());
                    return 11;
                }
                case RuntimeException re -> {
                    System.err.println("Runtime error ❌: " + re.getMessage());
                    e.printStackTrace(System.err);
                    return 90;
                }
                case Exception ex -> {
                    System.err.println("Unexpected error ❌");
                    e.printStackTrace(System.err);
                    return 99;
                }
            }
        });
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!files.isEmpty()) {
            // Ambiguous combination check
            if (input != null) {
                System.err.println("Error: cannot combine positional argument '"+input+"' with --files flag.");
                return 2;
            }
            // process files flags
            for(String filePattern : files) {
                for(Path path : expandGlob(filePattern)) {
                    try (InputStream in = Files.newInputStream(path)) {
                        String esql = inputFormat.extract(in);
                        if(failsCheck(esql)) {
                            System.err.println("for file '"+path+"'");
                            return 1;
                        }
                    }
                }
            }
        } else {
            try (InputStream in = input != null ? new ByteArrayInputStream(input.getBytes(UTF_8)) : System.in) {
                String esql = inputFormat.extract(in);
                if(failsCheck(esql)) {
                    return 1;
                }
            }
        }
        return 0;
    }

    public List<Path> expandGlob(String pattern) throws IOException {
        Path baseDir = Paths.get(".");
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        List<Path> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(baseDir)) {
            stream.filter(matcher::matches)
                    .forEach(matches::add);
        }
        return matches;
    }

    public boolean failsCheck(String text) {
        try {
            CharStream input = CharStreams.fromString(text);
            EsqlBaseLexer lexer = new EsqlBaseLexer(input);
            EsqlBaseParser parser = new EsqlBaseParser(new CommonTokenStream(lexer));
    
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
    
                    throw new SyntaxException(line, charPositionInLine, msg);
                }
            });
    
            // Call top-level rule directly (hardcoded)
            parser.singleStatement();
    
            System.out.println("Input is valid ✅");
            return false;
    
        } catch (SyntaxException e) {
            // Catch syntax errors from the error listener
            System.err.println("Input is invalid ❌");
            System.err.println(e.getMessage());
            return true;
        } catch (Exception e) {
            // Catch other unexpected exceptions
            System.err.println("Unexpected error ❌");
            e.printStackTrace();
            return true;
        }
    }

    public static class SyntaxException extends RuntimeException {
        private final int line;
        private final int charPositionInLine;
        private final String parserMessage;

        public SyntaxException(int line, int charPositionInLine, String message) {
            super(String.format("Syntax error at line %d:%d - %s.", line, charPositionInLine, message));
            this.line = line;
            this.charPositionInLine = charPositionInLine;
            this.parserMessage = message;
        }

        public int getLine() {
            return line;
        }

        public String getParserMessage() {
            return parserMessage;
        }

        public int getCharPositionInLine() {
            return charPositionInLine;
        }
    }
}
