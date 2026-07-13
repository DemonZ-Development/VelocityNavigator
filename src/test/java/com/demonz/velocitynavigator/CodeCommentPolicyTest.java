/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CodeCommentPolicyTest {

    @Test
    void javaSourcesContainNoNonLicenseComments() throws Exception {
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    int packageLine = source.indexOf("package ");
                    String body = packageLine < 0 ? source : source.substring(packageLine);
                    assertFalse(body.matches("(?s).*?(?m:^\\s*//|^\\s*/\\*|\\s//[^/]|\\s/\\*).*"), file.toString());
                }
            }
        }
    }
}
