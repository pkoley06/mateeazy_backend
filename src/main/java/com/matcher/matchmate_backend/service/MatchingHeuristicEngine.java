package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.lifestyle.CleanlinessLevel;
import com.matcher.matchmate_backend.model.enums.lifestyle.DietaryPreference;
import com.matcher.matchmate_backend.model.enums.lifestyle.GuestTolerance;
import com.matcher.matchmate_backend.model.enums.lifestyle.SleepSchedule;
import com.matcher.matchmate_backend.model.enums.lifestyle.SmokingHabit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Milestone 3 Step 3.1: deterministic lifestyle-compatibility scoring between a
 * searching user (seeker) and a discovery-feed candidate. Spec: "MatchMate Technical
 * Architecture.md" §4.4. Pure/stateless — no repository access, so it's trivial to unit test.
 */
@Service
public class MatchingHeuristicEngine {

    private static final double WEIGHT_CLEANLINESS = 2.0;
    private static final double WEIGHT_SLEEP = 1.5;
    private static final double WEIGHT_GUEST = 1.2;
    private static final double WEIGHT_DIET = 1.0;
    private static final double MAX_DISTANCE =
            WEIGHT_CLEANLINESS + WEIGHT_SLEEP + WEIGHT_GUEST + WEIGHT_DIET; // 5.7

    public record MatchResult(Integer matchPercentage, List<String> compatibilityHighlights) {
        public static MatchResult incomplete() {
            return new MatchResult(null, List.of());
        }

        public static MatchResult disqualified() {
            return new MatchResult(0, List.of());
        }

        public boolean isDisqualified() {
            return matchPercentage != null && matchPercentage == 0;
        }
    }

    /** Evaluates candidate B's compatibility for seeker A. Never mutates either user. */
    public MatchResult evaluate(AppUser seeker, AppUser candidate) {
        if (!hasCompletedLifestyle(seeker) || !hasCompletedLifestyle(candidate)) {
            return MatchResult.incomplete();
        }

        if ((seeker.getSmokingHabit() == SmokingHabit.NON_SMOKER && candidate.getSmokingHabit() == SmokingHabit.REGULAR_SMOKER)
                || (candidate.getSmokingHabit() == SmokingHabit.NON_SMOKER && seeker.getSmokingHabit() == SmokingHabit.REGULAR_SMOKER)) {
            return MatchResult.disqualified();
        }

        double distance = 0;
        distance += WEIGHT_CLEANLINESS
                * Math.abs(cleanlinessScore(seeker.getCleanlinessLevel()) - cleanlinessScore(candidate.getCleanlinessLevel()));
        distance += WEIGHT_SLEEP
                * Math.abs(sleepScore(seeker.getSleepSchedule()) - sleepScore(candidate.getSleepSchedule()));
        distance += WEIGHT_GUEST
                * Math.abs(guestScore(seeker.getGuestTolerance()) - guestScore(candidate.getGuestTolerance()));
        distance += WEIGHT_DIET
                * Math.abs(dietScore(seeker.getDietaryPreference()) - dietScore(candidate.getDietaryPreference()));

        int percentage = (int) Math.max(0, Math.round(100 - (distance / MAX_DISTANCE * 100)));
        return new MatchResult(percentage, buildHighlights(seeker, candidate));
    }

    public boolean hasCompletedLifestyle(AppUser user) {
        return user.getSmokingHabit() != null
                && user.getDietaryPreference() != null
                && user.getSleepSchedule() != null
                && user.getCleanlinessLevel() != null
                && user.getGuestTolerance() != null;
    }

    public double cleanlinessScore(CleanlinessLevel level) {
        if (level == null) return 0.5;
        return switch (level) {
            case VERY_CLEAN -> 1.0;
            case MODERATE -> 0.5;
            case RELAXED -> 0.0;
        };
    }

    public double sleepScore(SleepSchedule schedule) {
        if (schedule == null) return 0.5;
        return switch (schedule) {
            case EARLY_BIRD -> 1.0;
            case FLEXIBLE -> 0.5;
            case NIGHT_OWL -> 0.0;
        };
    }

    public double guestScore(GuestTolerance tolerance) {
        if (tolerance == null) return 0.5;
        return switch (tolerance) {
            case FREQUENT -> 1.0;
            case OCCASIONAL -> 0.5;
            case NEVER -> 0.0;
        };
    }

    public double dietScore(DietaryPreference preference) {
        if (preference == null) return 0.4;
        return switch (preference) {
            case VEGAN -> 1.0;
            case VEGETARIAN -> 0.8;
            case EGGETARIAN -> 0.6;
            case NO_PREFERENCE -> 0.4;
            case NON_VEGETARIAN -> 0.0;
        };
    }

    private List<String> buildHighlights(AppUser seeker, AppUser candidate) {
        List<String> highlights = new ArrayList<>();

        if (seeker.getSmokingHabit() == SmokingHabit.NON_SMOKER
                && candidate.getSmokingHabit() == SmokingHabit.NON_SMOKER) {
            highlights.add("Both Non-Smokers");
        }
        if (seeker.getSleepSchedule() == candidate.getSleepSchedule()) {
            highlights.add(switch (seeker.getSleepSchedule()) {
                case NIGHT_OWL -> "Both Night Owls";
                case EARLY_BIRD -> "Both Early Birds";
                case FLEXIBLE -> "Similar Sleep Rhythm";
            });
        }
        if (seeker.getCleanlinessLevel() == candidate.getCleanlinessLevel()) {
            highlights.add(switch (seeker.getCleanlinessLevel()) {
                case VERY_CLEAN -> "Meticulous Cleaners";
                case MODERATE -> "Similarly Tidy";
                case RELAXED -> "Both Easygoing About Mess";
            });
        }
        if (seeker.getGuestTolerance() == candidate.getGuestTolerance()) {
            highlights.add(switch (seeker.getGuestTolerance()) {
                case NEVER -> "Both Prefer a Quiet Home";
                case OCCASIONAL -> "Similar Guest Habits";
                case FREQUENT -> "Both Love Hosting";
            });
        }
        if (seeker.getDietaryPreference() == candidate.getDietaryPreference()) {
            highlights.add(switch (seeker.getDietaryPreference()) {
                case VEGAN -> "Both Vegan";
                case VEGETARIAN -> "Both Vegetarian";
                case EGGETARIAN -> "Both Eggetarian";
                case NON_VEGETARIAN -> "Both Non-Vegetarian";
                case NO_PREFERENCE -> "Easygoing About Food";
            });
        }
        return highlights;
    }
}
