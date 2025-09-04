# ESQL-check

This project contains docker images based on different languages that build a parser from the [ES:QL grammar](https://github.com/elastic/elasticsearch/tree/main/x-pack/plugin/esql/src/main/antlr) written in [ANTLR](https://www.antlr.org/)

## purpose

These generate a parser to validate your ES:QL grammar in different languages. This can be useful for plugins, pipelines, etc. where there's a need to perform some validation outside of Elasticsearch.


## dockerfile steps

1. Build stage - compile parser from upstream
    1. get an image together for building
    2. download antlr
    3. pull down ES:QL grammar by sparse git checkout of https://github.com/elastic/elasticsearch/tree/main/x-pack/plugin/esql/src/main/antlr
    4. Copy g4 files to flatten directory structure
    5. Strip `@header{ ... }`, `{this.[anything]}?` and `superClass=...` lines
        - reason for `@header{ ... }` is that it is in a java style comment block `/* ... */` which is taken verbatim and thus breaks things when we try to create the parser in languages that don't support comment blocks.
        - `{this.[anything]}?` is inline scriptlet in java which is also taken along and thus would also break things. These are used as feature flags, thus you get all features.
        - `superClass=...` resets the superclass specified for antlr generation. This also caused issues in other languages
    6. compile the grammar into lexer and parser code for the target language
    7. compile if needed in the language
2. Runtime stage - build minimum image
    1. copy over result of previous stage
    2. set entrypoint