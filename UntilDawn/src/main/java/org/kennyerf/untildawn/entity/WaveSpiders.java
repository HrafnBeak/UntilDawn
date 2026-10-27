package org.kennyerf.untildawn.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.spider.Spider;
import org.kennyerf.untildawn.entity.ai.HuntPlayersGoal;
import org.kennyerf.untildawn.entity.ai.SquadObjectiveGoal;
import org.kennyerf.untildawn.entity.ai.StationaryMeleeGoal;

import java.util.List;

/**
 * What the wave spiders (Webbers, Sneaking Spiders) share. They move like the other wave mobs, along planned paths with
 * their squad ({@link SquadObjectiveGoal}), but their paths climb straight up and down walls the way spiders do
 * ({@link org.kennyerf.untildawn.pathing.PathAbilities#SPIDER}). They keep the vanilla spider's leap at a nearby target,
 * and hunt players like every wave mob, in daylight as at night.
 */
final class WaveSpiders {
    private WaveSpiders() {
    }

    static AttributeSupplier.Builder createAttributes() {
        // Like the other wave mobs, it keeps track of a target up to 64 blocks off.
        return Spider.createAttributes().add(Attributes.FOLLOW_RANGE, 64.0);
    }

    /**
     * Swaps the vanilla spider's attack (which walks to the target on vanilla navigation, and gives up in daylight) for
     * {@link StationaryMeleeGoal}, its strolling for {@link SquadObjectiveGoal}, and its targeting, which only works in
     * the dark, for {@link HuntPlayersGoal}. The leap stays, ahead of the path, so a spider still pounces when close.
     */
    static <T extends Spider & WaveMob> void replaceVanillaGoals(T spider, GoalSelector goals, GoalSelector targets) {
        goals.removeAllGoals(goal -> goal instanceof MeleeAttackGoal || goal instanceof WaterAvoidingRandomStrollGoal);
        goals.addGoal(4, new SquadObjectiveGoal<>(spider));
        goals.addGoal(5, new StationaryMeleeGoal(spider));
        targets.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?>);
        targets.addGoal(2, new HuntPlayersGoal<>(spider));
    }

    /** One spider in a hundred spawns with a skeleton riding it; a wave spider rides alone. */
    static void dropRiders(Mob spider) {
        List<Entity> riders = List.copyOf(spider.getPassengers());
        spider.ejectPassengers();
        riders.forEach(Entity::discard);
    }
}
