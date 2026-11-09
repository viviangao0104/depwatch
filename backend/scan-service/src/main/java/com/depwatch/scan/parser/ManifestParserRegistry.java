package com.depwatch.scan.parser;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ManifestParserRegistry {

    private final List<ManifestParser> parsers;

    public ManifestParserRegistry(List<ManifestParser> parsers) {
        this.parsers = parsers;
    }

    public ManifestParser forFile(String fileName) {
        return parsers.stream()
                .filter(p -> p.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new ManifestParseException("No parser for file: " + fileName));
    }
}
