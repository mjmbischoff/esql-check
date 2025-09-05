package dev.bischoff.michael.elastic.esql.validator;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.InputStream;

public class TomlFieldExtractor implements FieldExtractor {

    @Override
    public String extract(InputStream input, String field) throws Exception {
        TomlParseResult result = Toml.parse(input);
        if (result.contains(field)) {
            Object value = result.get(field);
            return value != null ? value.toString() : null;
        }
        throw new IllegalArgumentException("Field '" + field + "' not found in TOML input");
    }
}
