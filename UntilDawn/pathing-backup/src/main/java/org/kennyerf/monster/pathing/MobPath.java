package org.kennyerf.monster.pathing;

import java.util.List;

/**
 * Result of a search. {@code steps.get(0)} is the start position.
 *
 * @param reachesGoal false for a partial path towards the closest point found within the node budget
 */
public record MobPath(List<PathStep> steps, boolean reachesGoal, double cost, int nodesExplored, long micros, String goalDescription) {
    public int breakCount() {
        return steps.stream().mapToInt(s -> s.breaks().size()).sum();
    }

    public int placeCount() {
        return steps.stream().mapToInt(s -> s.places().size()).sum();
    }

    public PathStep last() {
        return steps.getLast();
    }
}
