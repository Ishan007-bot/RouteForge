package com.routeforge.engine.osm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OsmPbfLoader}.
 * <p>
 * These tests run in CI on every push, so they MUST NOT depend on
 * any large file we'd have to download. We only cover error paths here.
 * <p>
 * A real end-to-end load is exercised manually by running the {@code main}
 * method against a downloaded {@code .osm.pbf} (see README).
 */
class OsmPbfLoaderTest {

    private final OsmPbfLoader loader = new OsmPbfLoader();

    @Test
    void load_rejectsNullPath() {
        assertThatThrownBy(() -> loader.load(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pbfFile");
    }

    @Test
    void load_rejectsNonexistentFile() {
        Path missing = Paths.get("definitely-does-not-exist-" + System.nanoTime() + ".pbf");
        assertThatThrownBy(() -> loader.load(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a regular file");
    }

    @Test
    void load_rejectsDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("osm-loader-test");
        try {
            assertThatThrownBy(() -> loader.load(tempDir))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a regular file");
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void load_rejectsMalformedPbf() throws IOException {
        // Write a fake "PBF" that is just random bytes — the reader must error.
        Path bogus = Files.createTempFile("bogus", ".pbf");
        try {
            Files.write(bogus, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
            assertThatThrownBy(() -> loader.load(bogus))
                    .isInstanceOf(IOException.class);
        } finally {
            Files.deleteIfExists(bogus);
        }
    }

    @Test
    void osmStats_totalSumsParts() {
        OsmStats s = new OsmStats(10, 20, 5);
        assertThat(s.total()).isEqualTo(35);
    }
}
