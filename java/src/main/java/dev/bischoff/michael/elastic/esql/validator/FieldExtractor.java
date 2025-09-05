package dev.bischoff.michael.elastic.esql.validator;

import java.io.InputStream;

public interface FieldExtractor {
    String extract(InputStream input, String field) throws Exception;
}