package com.gradpilot.tracker;

import java.util.List;
import java.util.Map;

/**
 * A ready-made starting point for a tracker, e.g. "Job hunt".
 *
 * @param key          stable identifier used by the API
 * @param name         default tracker name
 * @param type         tracker type it creates
 * @param description  one line shown in the template picker
 * @param settings     default settings, e.g. pipeline stages
 * @param starterItems item titles created with the tracker
 */
public record TrackerTemplate(
        String key,
        String name,
        TrackerType type,
        String description,
        Map<String, Object> settings,
        List<String> starterItems) {
}
