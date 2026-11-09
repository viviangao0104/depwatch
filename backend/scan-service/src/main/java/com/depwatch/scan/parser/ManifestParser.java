package com.depwatch.scan.parser;

import com.depwatch.scan.graph.DependencyGraph;

import java.io.IOException;
import java.io.InputStream;

/**
 * 策略接口：每种 manifest 格式一个实现，Spring 自动收集成 List 注入 ManifestParserRegistry。
 * 加新格式（requirements.txt / go.sum / Cargo.lock）只需新增一个实现类。
 */
public interface ManifestParser {

    /** 这个解析器处理的类型名，与 project-service 的 ManifestType 对应。 */
    String type();

    boolean supports(String fileName);

    DependencyGraph parse(InputStream in) throws IOException, ManifestParseException;
}
