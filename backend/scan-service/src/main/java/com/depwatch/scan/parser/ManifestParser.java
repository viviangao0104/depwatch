package com.depwatch.scan.parser;

import com.depwatch.scan.graph.DependencyGraph;

import java.io.IOException;
import java.io.InputStream;

/**
  * Strategy interface: one implementation per manifest format; Spring collects them into a List for ManifestParserRegistry.
  * Adding a format (requirements.txt / go.sum / Cargo.lock) is one new class.
 */
public interface ManifestParser {

    /** Type name handled by this parser; matches ManifestType in project-service. */
    String type();

    boolean supports(String fileName);

    DependencyGraph parse(InputStream in) throws IOException, ManifestParseException;
}
