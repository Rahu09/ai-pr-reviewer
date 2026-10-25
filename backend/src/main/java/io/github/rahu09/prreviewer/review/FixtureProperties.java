package io.github.rahu09.prreviewer.review;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where fixture folders live. Relative paths resolve against the working directory ({@code backend/}). */
@ConfigurationProperties("reviewer.fixtures")
public record FixtureProperties(@DefaultValue("../evaluation/datasets") String dir) {
}
