package com.medicompare;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Optional version/date labels for the downloaded files, read from
 * data/dataset-info.properties. Kaggle CSV files carry no version or date,
 * so the app only shows one if you record it.
 *
 * Keys: catalog.version, usage.version
 */
public final class DatasetInfo {
    private final Properties props = new Properties();

    private DatasetInfo() {
    }

    public static DatasetInfo load(Path directory) {
        DatasetInfo info = new DatasetInfo();
        Path file = directory.resolve("dataset-info.properties");

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                info.props.load(reader);
            } catch (Exception exception) {
                System.out.println("Could not read " + file + ": "
                        + exception.getMessage());
            }
        }
        return info;
    }

    public String version(String role) {
        String value = props.getProperty(role + ".version", "").trim();
        return value.isEmpty()
                ? "dataset version/date not recorded (see README)"
                : value;
    }
}
