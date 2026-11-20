package com.vaadin.testing.unified.spi;

import java.nio.file.Path;
import java.util.List;

/**
 * What a Testing Tool wrote for a snapshot or a failure: its files, and where the screenshot among them shows
 * the Step Target (null when nothing is highlighted: no screenshot, or no Step Target).
 */
public record Evidence(List<Path> files, Highlight highlight) {

    public Evidence {
        files = List.copyOf(files);
    }

    public static Evidence of(List<Path> files) {
        return new Evidence(files, null);
    }
}
