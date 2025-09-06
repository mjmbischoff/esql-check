package dev.bischoff.michael.elastic.esql.validator;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.antlr.v4.runtime.*;
import dev.bischoff.michael.elastic.esql.parser.*;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
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
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static com.fasterxml.jackson.core.JsonToken.FIELD_NAME;
import static dev.bischoff.michael.elastic.esql.validator.ESQLChecker.ExtractionResult.skip;
import static dev.bischoff.michael.elastic.esql.validator.ESQLChecker.ExtractionResult.esql;
import static java.nio.charset.StandardCharsets.UTF_8;

@Command(
    name = "esql-check",
    mixinStandardHelpOptions = true,
    version = "esql-check 0.1",
    header = "Program that validates Elasticsearch Query Language (ESQL) statements.\n",
    footer = {
        "Flags / Args behaviour:",
        "  No flags, no args",
        "    Input:    stdin",
        "    Behavior: Read raw ESQL from stdin",
        "",
        "  No flags, 1 positional arg",
        "    Input:    Positional argument",
        "    Behavior: Treat as raw ESQL query string",
        "",
        "  --files, no args",
        "    Input:    Files matching glob(s)",
        "    Behavior: Process each file as raw ESQL",
        "",
        "  --files combined 1 positional arg",
        "    Input:    Ambiguous",
        "    Behavior: Error: cannot mix --files with a positional",
        "              argument (also with --json or --toml)",
        "",
        "  format flag, no args",
        "    Input:    stdin",
        "    Behavior: Read JSON/TOML content from stdin; extract the",
        "              specified field and validate as ESQL",
        "",
        "  format flag, 1 positional arg",
        "    Input:    Positional argument",
        "    Behavior: Treat argument as JSON/TOML/Elastic Detection",
        "              rule; extract and validate as ESQL",
        "",
        "  format flag and --files",
        "    Input:    Files matching glob(s)",
        "    Behavior: Process each file as JSON/TOML; extract the",
        "              specified field from each and validate",
        ""
    }
)
public class ESQLChecker implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Optional raw ESQL or JSON/TOML/Elastic detection rule content")
    private String input;

    @Option(names = "--files", description = "Glob pattern(s) for ESQL files to validate")
    List<String> files = List.of();

    static class InputFormat {
        @Option(names = "--json", description = "Validate ESQL inside a JSON field with the given name")
        String jsonField;

        @Option(names = "--toml", description = "Validate ESQL inside a TOML field with the given name")
        String tomlField;

        @Option(names = "--elastic-dr", description = "Validate Elastic detection rule")
        boolean elasticDetectionRule;

        private ExtractionResult extract(InputStream in) throws Exception {
            if(elasticDetectionRule) {
                return Format.ElasticDetectionRule.extract(in);
            }
            if (jsonField != null) {
                return Format.JSON.extract(in, jsonField);
            }
            if (tomlField != null) {
                return Format.TOML.extract(in, tomlField);
            }
            return Format.ESQL.extract(in);
        }
    }

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    InputFormat inputFormat = new InputFormat();

    public static void main(String[] args) {
        var commandLine = new CommandLine(new ESQLChecker());
        commandLine.setExecutionExceptionHandler((e, commandLine1, parseResult) -> {
            switch (e) {
                case IOException ioe -> {
                    System.err.println("I/O error: " + ioe.getClass().getName() + "esql: '" + ioe.getMessage() + "'");
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
                        System.out.print("Checking file '"+path+"'... ");
                        var result = inputFormat.extract(in);
                        if(!result.shouldSkip) {
                            System.out.println("Skipping validation.");
                            continue;
                        }
                        if(failsCheck(result.esql)) {
                            System.err.println("Check for file '"+path+"' failed");
                            return 1;
                        }
                    }
                }
            }
        } else {
            try (InputStream in = input != null ? new ByteArrayInputStream(input.getBytes(UTF_8)) : System.in) {
                var result = inputFormat.extract(in);
                if(!result.shouldSkip) {
                    System.err.println("Skipping validation.");
                    return 0;
                }
                if(failsCheck(result.esql)) {
                    return 1;
                }
            }
        }
        return 0;
    }

    public List<Path> expandGlob(String pattern) throws IOException {
        Path baseDir = findExistingParent(pattern);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        List<Path> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(baseDir)) {
            stream.filter(matcher::matches)
                    .forEach(matches::add);
        }
        return matches;
    }

    // Determine the base directory to start walking
    private Path findExistingParent(String pattern) {
        Path path = Paths.get(pattern);
        Path parent = path.getParent();
        while (parent != null && !Files.exists(parent)) {
            parent = parent.getParent();
        }
        if (parent == null) {
            // If no existing parent (highly unusual), start from root or "."
            return path.isAbsolute() ? path.getRoot() : Paths.get(".");
        }
        return parent;
    }

    public boolean failsCheck(String text) {
        try {
            CharStream input = CharStreams.fromString(text);
            EsqlBaseParser parser = getEsqlParser(input);
            // Call top-level rule directly
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
            e.printStackTrace(System.err);
            return true;
        }
    }

    private static EsqlBaseParser getEsqlParser(CharStream input) {
        EsqlBaseLexer lexer = new EsqlBaseLexer(input);
        EsqlBaseParser parser = new EsqlBaseParser(new CommonTokenStream(lexer));

        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer,
                                    Object offendingSymbol,
                                    int line, int charPositionInLine,
                                    String msg, RecognitionException e) {
                throw new SyntaxException(line, charPositionInLine, msg);
            }
        });
        return parser;
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

    public record ExtractionResult(boolean shouldSkip, String esql) {
        public static ExtractionResult esql(String esql) {
            return new ExtractionResult(true, esql);
        }
        public static ExtractionResult skip() {
            return new ExtractionResult(false, null);
        }
    }
    public enum Format {
        JSON {
            @Override
            public ExtractionResult extract(InputStream input, String field) throws Exception {
                JsonFactory factory = new JsonFactory();
                try (JsonParser parser = factory.createParser(input)) {
                    while (!parser.isClosed()) {
                        JsonToken token = parser.nextToken();
                        if (FIELD_NAME.equals(token) && field.equals(parser.currentName())) {
                            parser.nextToken(); // move to value
                            return esql(parser.getValueAsString());
                        }
                    }
                }
                throw new IllegalArgumentException("Field '" + field + "' not found in JSON input");
            }
        },
        TOML {
            @Override
            public ExtractionResult extract(InputStream input, String field) throws Exception {
                TomlParseResult result = Toml.parse(input);
                if (result.hasErrors()) {
                    var joinedString = new StringJoiner(" ");
                    result.errors().forEach(error -> joinedString.add(error.toString()));
                    throw new RuntimeException("TOML input has errors: " + joinedString);
                }
                if (result.contains(field)) {
                    Object value = result.get(field);
                    if(value == null) {
                        throw new IllegalArgumentException("Field '" + field + "' is null in TOML input");
                    }
                    return esql(value.toString());
                }
                throw new IllegalArgumentException("Field '" + field + "' not found in TOML input");
            }
        },
        ESQL {
            @Override
            public ExtractionResult extract(InputStream input, String ignored) throws Exception {
                // expecting raw UTF-8 string.
                return esql(new String(input.readAllBytes(), UTF_8));
            }
        },
        ElasticDetectionRule {
            @Override
            public ExtractionResult extract(InputStream input, String ignored) throws Exception {
                TomlParseResult result = Toml.parse(input);
                if (result.hasErrors()) {
                    var joinedString = new StringJoiner(" ");
                    result.errors().forEach(error -> joinedString.add(error.toString()));
                    throw new RuntimeException("Elastic-detection-rule - TOML input has errors: " + joinedString);
                }
                // We only check esql based detection rules.
                if(!result.contains("rule.type")) {
                    return skip();
                }
                if(!Objects.equals(result.getString("rule.type"), "esql")) {
                    return skip();
                }
                if (result.contains("rule.query")) {
                    Object value = result.get("rule.query");
                    if(value == null) {
                        throw new RuntimeException("Field \"rule.query\" is null in TOML input");
                    }
                    return esql(value.toString());
                }
                throw new RuntimeException("Field \"rule.query\" not found in TOML input");
            }
        };

        public abstract ExtractionResult extract(InputStream input, String field) throws Exception;

        public ExtractionResult extract(InputStream input) throws Exception {
            return extract(input, null);
        }
    }
}
