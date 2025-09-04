# ESQL-check

This project contains docker images based on different languages that build a parser from the [ES:QL](https://www.elastic.co/docs/reference/query-languages/esql) [grammar](https://github.com/elastic/elasticsearch/tree/main/x-pack/plugin/esql/src/main/antlr) written in [ANTLR](https://www.antlr.org/)

## Usage

### Executable (linux)

```$ wget -qO esql-check https://github.com/mjmbischoff/esql-check/releases/download/9.1.2/esql-check-java
$ ./esql-check "from foo"
Input is valid :white_check_mark:
$ ./esql-check "select *"
Input is invalid :x:
Syntax error at line 1:0 - mismatched input 'select' expecting {'explain', 'row', 'from', 'ts', 'show'}.
```

### Docker image

```
$ docker run mjmbischoff/esql-check:9.1.2 "FROM foo UNKNOWN"
Input is invalid :x:
Syntax error at line 1:9 - extraneous input 'UNKNOWN' expecting <EOF>.
```

## Purpose
This project contains directories matching different languages. These contain docker images which generate a parser for 
[ES:QL](https://www.elastic.co/docs/reference/query-languages/esql). Finally, it creates an image with the entrypoint 
pointing at a script or executable that allows you to validate your [ES:QL](https://www.elastic.co/docs/reference/query-languages/esql) 
grammar. This can be useful for plugins, pipelines, etc. where there's a need to perform some validation outside of 
Elasticsearch. These dockerfiles also provide a live howto on how to get an ESQL parser in 
your project.


### dockerfile steps

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