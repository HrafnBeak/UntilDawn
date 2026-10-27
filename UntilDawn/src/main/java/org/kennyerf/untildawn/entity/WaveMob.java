package org.kennyerf.untildawn.entity;

import net.minecraft.world.entity.LivingEntity;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.pathing.PathAbilities;

/**
 * This mod's mobs. Every WaveMob joins the shared wave team, belongs to a squad (unless it works alone), cannot hurt
 * other WaveMobs, and damages the Core while standing inside its area. They move with the Baritone-style planner (the
 * spiders' paths climb walls).
 */
public interface WaveMob {
    UntilDawnConfig.MobSettings settings();

    /** False for loners (Assassins): never put in a squad, they hunt on their own. */
    default boolean joinsSquads() {
        return true;
    }

    PathAbilities pathAbilities();

    MobPathController pathController();

    /** Within this distance (and able to hit the target) the mob stops pathing and fights. */
    default double engageRange() {
        return 2.0;
    }

    /** Ranged mobs may engage from above or below; melee mobs must be level with the target. */
    default boolean isRanged() {
        return false;
    }

    /** Can attack the target from where it is without seeing it (a Longbow Man's plunging fire). */
    default boolean canFightUnseen(LivingEntity target) {
        return false;
    }
}
