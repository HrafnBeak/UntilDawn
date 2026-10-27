package org.kennyerf.monster.pathing;

/**
 * What a mob is allowed to do to the world while pathing.
 *
 * @param canBreak  may plan routes through solid blocks (Miner digs them, Bomber blasts them)
 * @param explosive breaking means igniting (Bomber) rather than mining
 * @param canPlace  may plan bridges, steps and pillars (Builder)
 */
public record PathAbilities(boolean canBreak, boolean explosive, boolean canPlace) {
    public static final PathAbilities MINER = new PathAbilities(true, false, false);
    public static final PathAbilities BUILDER = new PathAbilities(false, false, true);
    public static final PathAbilities BOMBER = new PathAbilities(true, true, false);
    public static final PathAbilities WALKER = new PathAbilities(false, false, false);
}
