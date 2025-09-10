# ESQL-check

Project that documents creating an [ES:QL](https://www.elastic.co/docs/reference/query-languages) parser outside of Elasticsearch using dockerfiles. In addition it provides a lightweight executable to validate ESQL and provides artifacts that can be used as dependicies in other projects.

## ESQL checker

### Install

#### Executable (linux)

```
$ wget -qO esql-check https://github.com/mjmbischoff/esql-check/releases/download/9.1.2/esql-check-java
$ chmod +x esql-check
$ ./esql-check "from foo"
Input is valid :white_check_mark:
$ ./esql-check "select *"
Input is invalid :x:
Syntax error at line 1:0 - mismatched input 'select' expecting {'explain', 'row', 'from', 'ts', 'show'}.
```

#### Docker image

```
$ docker run mjmbischoff/esql-check:9.1.2 "FROM foo UNKNOWN"
Input is invalid :x:
Syntax error at line 1:9 - extraneous input 'UNKNOWN' expecting <EOF>.
```

### usage

| Flags / Args                    | Input Source           | Interpretation / Behavior                                                                                 |
|---------------------------------|------------------------|-----------------------------------------------------------------------------------------------------------|
| No flags, no args               | stdin                  | Read raw ESQL from stdin                                                                                  | 
| No flags, 1 positional arg      | Positional argument    | Treat as raw ESQL query string                                                                            |
| `--files` alone                 | Files matching glob(s) | Process each file as raw ESQL                                                                             |
| `--files` + 1 positional arg    | Ambiguous              | Error: cannot mix `--files` with a positional argument (also with `--elastic-dr` `--json` or `--toml`)                   |
| format flag + no args           | stdin                  | Read JSON/TOML content from stdin; extract the specified field and validate as ESQL                       | 
| format flag + 1 positional arg  | Positional argument    | Treat argument as JSON/TOML/Elastic Detection rule content; extract and validate as ESQL                  | 
| format flag + `--files`         | Files matching glob(s) | Process each file as JSON/TOML/Elastic Detection rule; extract the specified field from each and validate | 

#### TOML files --toml

Example: checking detection rules repo toml's
```
$ wget https://github.com/elastic/detection-rules/archive/refs/heads/main.zip
$ unzip main.zip
$ wget -qO esql-check https://github.com/mjmbischoff/esql-check/releases/download/9.1.3/esql-check-java
$ chmod +x esql-check
$ ./esql-check --toml rule.query --files "./detection-rules-main/rules/**/*.toml"
```
This will fail:
```
Checking file '/home/michael/test-esql/detection-rules-main/rules/cross-platform/credential_access_cookies_chromium_browsers_debugging.toml'...
Input is invalid ❌
Syntax error at line 1:0 - mismatched input 'process' expecting {'explain', 'row', 'from', 'ts', 'show'}.
for file '/home/michael/test-esql/detection-rules-main/rules/cross-platform/credential_access_cookies_chromium_browsers_debugging.toml'
```
Because not all rules are esql based.

#### Elastic detection rules --elastic-dr

Changing the command used in the TOML section to use the `--elastic-dr` flag, enables skipping detection rules that aren't esql based. If we run the following:
```
$./esql-check --elastic-dr --files "detection-rules-main/rules/**/*.toml"
```
We see it skipping files, while checking others. Currently(sep 2025) we get the following:
```
Checking file 'detection-rules-main/rules/integrations/okta/credential_access_okta_authentication_for_multiple_users_with_the_same_device_token_hash.toml'... line 4:24 token recognition error at: '"user\.'
Input is invalid ❌
Syntax error at line 4:31 - no viable alternative at input '(event.action rlike authentication'.
Check for file 'detection-rules-main/rules/integrations/okta/credential_access_okta_authentication_for_multiple_users_with_the_same_device_token_hash.toml' failed
```
inspecting the file it looks like an actual problem with an unescaped `\`. This is _indeed_ not valid esql.

## Published artifacts
if you just want to use the parser in your projects use:

### Java

! not pushing to maven central yet ! TODO
```
        <dependency>
            <groupId>org.antlr</groupId>
            <artifactId>antlr4</artifactId>
            <version>4.13.2</version>
        </dependency>
        <dependency>
            <groupId>dev.bischoff.michael.elastic.esql</groupId>
            <artifactId>parser</artifactId>
            <version>${ES_version}</version>
        </dependency>
```

### Python

Currently supplying a zip with the generated classes

TODO: Distribute as package

### Golang

Published under https://github.com/mjmbischoff/esql-go-parser
```
import (
    ...
    "github.com/antlr4-go/antlr/v4"
    "github.com/mjmbischoff/esql-go-parser/parsing"
)
```
Use explicit versions, eg:
```
go get github.com/mjmbischoff/esql-go-parser@v9.1.3
```

This project is currently supplying a zip with the generated classes that is used by that repo. It's public, so if you want to know how it's constructed, go check it out.

## Project 

### Purpose
This project aims to achieve a couple of goals:
- *provide a base for other programs* by removing the hassle of generating and compiling parsers generated by antlr, which should ease development as dependecies can be defined on the published artifacts. Java jars in maven central, python pip, golang etc.
- *document the steps to create a parser* if you want to do it yourself,  this project can serve as a howto
- *provide a checker* we need to test the above anyway so might as well build something somewhat usefull

This project contains directories matching different languages. These contain docker images which generate a parser for 
[ES:QL](https://www.elastic.co/docs/reference/query-languages/esql). Finally, it creates an image with the entrypoint 
pointing at a script or executable that allows you to validate your [ES:QL](https://www.elastic.co/docs/reference/query-languages/esql) 
grammar. This can be useful for plugins, pipelines, etc. where there's a need to perform some validation outside of 
Elasticsearch. These dockerfiles also provide a live howto on how to get an ESQL parser in 
your project.

### build

This project builds [ES:QL](https://www.elastic.co/docs/reference/query-languages) parsers based on [grammar](https://github.com/elastic/elasticsearch/tree/main/x-pack/plugin/esql/src/main/antlr) written in [ANTLR](https://www.antlr.org/). This is done using dockerfile's to ensure the build can be reproduced. We currently build parsers for Java Golang and Python. We then use these to implement a simple program that can validate ESQL queries. The intermediate result collected and published so it can be used in other projects. This means jars in maven central etc. Each language has his own subfolder. 

#### dockerfile steps

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
3. Export stage
    1. we copy the artifacts in so we can use this to extract the parser
2. Runtime stage - build minimum image
    1. copy over result of previous stage
    2. set entrypoint

#### Java

if you need the jar of the parser to do a local install, you can use the following command:
```
docker buildx build --build-arg GIT_REF=main --target export --output type=tar,dest=./java.tar ./java && mkdir -p java/target && tar -C java/target --strip-components=1 -xf java.tar export/generated-antlr-esql-parser.jar export/generated-antlr-esql-parser-sources.jar && rm java.tar
mvn install:install-file -Dfile=.java/target/generated-antlr-esql-parser.jar -DgroupId=dev.bischoff.michael.elastic.esql -DartifactId=parser -Dversion=main -Dpackaging=jar
```
