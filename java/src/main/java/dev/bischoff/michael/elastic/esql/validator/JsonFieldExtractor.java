package dev.bischoff.michael.elastic.esql.validator;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.InputStream;

public class JsonFieldExtractor implements FieldExtractor {

    @Override
    public String extract(InputStream input, String field) throws Exception {
        JsonFactory factory = new JsonFactory();
        try (JsonParser parser = factory.createParser(input)) {
            while (!parser.isClosed()) {
                JsonToken token = parser.nextToken();
                if (JsonToken.FIELD_NAME.equals(token) && field.equals(parser.currentName())) {
                    parser.nextToken(); // move to value
                    return parser.getValueAsString();
                }
            }
        }
        throw new IllegalArgumentException("Field '" + field + "' not found in JSON input");
    }
}
