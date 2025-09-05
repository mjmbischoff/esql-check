package dev.bischoff.michael.elastic.esql.validator;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.InputStream;
import java.util.StringJoiner;

public class TomlFieldExtractor implements FieldExtractor {

    @Override
    public String extract(InputStream input, String field) throws Exception {
        TomlParseResult result = Toml.parse(input);
        if(result.hasErrors()) {
            var joinedString = new StringJoiner(" ");
            result.errors().forEach(error -> joinedString.add(error.toString()));
            throw new RuntimeException("TOML input has errors: " + joinedString);
        }
        if (result.contains(field)) {
            Object value = result.get(field);
            return value != null ? value.toString() : null;
        }
        throw new IllegalArgumentException("Field '" + field + "' not found in TOML input");
    }
}
