package com.gradpilot.tracker;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** The templates offered when someone taps + New tracker. */
@Component
public class TemplateCatalog {

    public static final List<String> JOB_STAGES =
            List.of("To apply", "Applied", "Assessment", "Interview", "Offer", "Rejected");

    private final List<TrackerTemplate> templates = List.of(
            new TrackerTemplate("work-shifts", "Work shifts", TrackerType.SCHEDULE,
                    "Your roster, with a reminder when it is time to leave",
                    Map.of(Tracker.SETTING_TRAVEL_MINUTES, 45),
                    List.of()),
            new TrackerTemplate("job-hunt", "Job hunt", TrackerType.PIPELINE,
                    "Every application from To apply to Offer",
                    Map.of(Tracker.SETTING_STAGES, JOB_STAGES),
                    List.of()),
            new TrackerTemplate("driving-licence", "Driving licence", TrackerType.CHECKLIST,
                    "The steps from learner to licence",
                    Map.of(),
                    List.of("Pass the Hazard Perception Test",
                            "Call a driving school",
                            "Book practice lessons",
                            "Book the driving test")),
            new TrackerTemplate("practice-log", "Practice log", TrackerType.LOG,
                    "Log lesson hours, study time or anything you count",
                    Map.of("unit", "minutes"),
                    List.of()),
            new TrackerTemplate("study-plan", "Study plan", TrackerType.PROGRESS,
                    "Courses and topics, with where you are up to",
                    Map.of(),
                    List.of()),
            new TrackerTemplate("research-project", "Research project", TrackerType.CHECKLIST,
                    "What to prepare before your next meeting",
                    Map.of(),
                    List.of("Prepare results to show",
                            "List questions for supervisors",
                            "Plan the next writing step")),
            new TrackerTemplate("big-date", "Big date", TrackerType.COUNTDOWN,
                    "Count down to a deadline, test or arrival",
                    Map.of(),
                    List.of()));

    public List<TrackerTemplate> all() {
        return templates;
    }

    public Optional<TrackerTemplate> find(String key) {
        return templates.stream().filter(t -> t.key().equals(key)).findFirst();
    }
}
