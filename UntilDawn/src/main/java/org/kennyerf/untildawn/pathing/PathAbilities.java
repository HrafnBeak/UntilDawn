package org.kennyerf.untildawn.pathing;

/**
 * What a mob is allowed to do to the world while pathing.
 *
 * @param canBreak   may plan routes through solid blocks (Miner digs them, Bomber blasts them)
 * @param explosive  breaking means igniting (Bomber) rather than mining
 * @param canPlace   may plan bridges, steps and pillars (Builder)
 * @param parkour    sprint-jumps gaps (Sprinter); such mobs plan with Baritone when it can run, else plan their own jumps
 * @param climbWalls climbs straight up and down walls (the wave spiders); also plans with Baritone when it can run
 */
public record PathAbilities(boolean canBreak, boolean explosive, boolean canPlace, boolean parkour, boolean climbWalls) {
    public static final PathAbilities MINER = new PathAbilities(true, false, false, false, false);
    public static final PathAbilities BUILDER = new PathAbilities(false, false, true, false, false);
    public static final PathAbilities BOMBER = new PathAbilities(true, true, false, false, false);
    public static final PathAbilities WALKER = new PathAbilities(false, false, false, false, false);
    public static final PathAbilities SPRINTER = new PathAbilities(false, false, false, true, false);
    public static final PathAbilities SPIDER = new PathAbilities(false, false, false, false, true);

    /** Plans with Baritone when it can run: Baritone knows these moves better than the mod's own planner. */
    public boolean prefersBaritone() {
        return parkour || climbWalls;
    }
}
