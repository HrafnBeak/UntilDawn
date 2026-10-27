package org.kennyerf.monster.entity;

import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.entity.ai.MobPathController;
import org.kennyerf.monster.pathing.PathAbilities;

/**
 * This mod's mobs. Every WaveMob joins the shared wave team, cannot hurt other WaveMobs,
 * damages the Core while standing inside its area, and moves with the Baritone-style planner.
 */
public interface WaveMob {
    PathAbilities pathAbilities();

    MobPathController pathController();

    MonsterConfig.MobSettings settings();

    /** Within this distance (and with line of sight) the mob stops pathing and fights. */
    default double engageRange() {
        return 2.0;
    }

    /** Ranged mobs may engage from above or below; melee mobs must be level with the target. */
    default boolean isRanged() {
        return false;
    }
}
