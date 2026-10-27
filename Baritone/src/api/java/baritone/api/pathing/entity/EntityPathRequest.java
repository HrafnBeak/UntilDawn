/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.pathing.entity;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * A path to plan for an entity other than the player, with the inventory it plans with.
 * <p>
 * The inventory decides what the path may do, the way the player's hotbar does for Baritone: the best tool for each
 * block sets how long it takes to mine, blocks from {@link baritone.api.Settings#acceptableThrowawayItems} allow
 * bridging and pillaring, a water bucket allows long falls, and armour in the list counts as worn (Frost Walker,
 * Depth Strider). Every item counts as within reach, not just the first nine. Baritone's settings apply on top.
 *
 * @param level            the level to plan in; {@link IEntityPathPlanner#plan} must be called on its thread
 * @param start            where the entity's feet are
 * @param goal             where to go
 * @param items            what the entity carries (copied)
 * @param mainThread       runs work that must happen on the level's thread, such as working out block drops;
 *                         for a server level, the {@code MinecraftServer}
 * @param chunkRadius      the search may use loaded chunks up to this many chunks from {@code start}
 * @param allowBreak       may mine through blocks (with its best tool, or bare hands)
 * @param allowPlace       may place the blocks it carries
 * @param canSprint        moves at sprinting speed where a player would sprint (and can sprint-jump gaps)
 * @param allowParkour     may jump gaps ({@link baritone.api.Settings#allowParkour} for the player)
 * @param allowNeo         may jump around the end of a wall, curving out past it and back in (the entity has to steer
 *                         itself along the curve; players never plan these)
 * @param allowWallClimb   may climb straight up and down walls, and over edges onto them, as a spider does (the entity
 *                         has to press itself to the wall; players never plan these)
 * @param primaryTimeoutMs stop searching after this long once a path that gets somewhere has been found
 * @param failureTimeoutMs give up after this long when nothing has been found
 */
public record EntityPathRequest(Level level, BlockPos start, Goal goal, List<ItemStack> items, Executor mainThread,
                                int chunkRadius, boolean allowBreak, boolean allowPlace, boolean canSprint,
                                boolean allowParkour, boolean allowNeo, boolean allowWallClimb, long primaryTimeoutMs,
                                long failureTimeoutMs) {

    public EntityPathRequest {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(mainThread, "mainThread");
        items = items.stream().map(ItemStack::copy).toList();
        start = start.immutable();
    }

    public static Builder builder(Level level, BlockPos start, Goal goal, Executor mainThread) {
        return new Builder(level, start, goal, mainThread);
    }

    public static final class Builder {

        private final Level level;
        private final BlockPos start;
        private final Goal goal;
        private final Executor mainThread;
        private List<ItemStack> items = List.of();
        private int chunkRadius = 16;
        private boolean allowBreak = true;
        private boolean allowPlace = true;
        private boolean canSprint = true;
        private boolean allowParkour = BaritoneAPI.getSettings().allowParkour.value;
        private boolean allowNeo = false;
        private boolean allowWallClimb = false;
        private long primaryTimeoutMs = BaritoneAPI.getSettings().planAheadPrimaryTimeoutMS.value;
        private long failureTimeoutMs = BaritoneAPI.getSettings().planAheadFailureTimeoutMS.value;

        private Builder(Level level, BlockPos start, Goal goal, Executor mainThread) {
            this.level = level;
            this.start = start;
            this.goal = goal;
            this.mainThread = mainThread;
        }

        public Builder items(List<ItemStack> items) {
            this.items = items;
            return this;
        }

        public Builder chunkRadius(int chunkRadius) {
            this.chunkRadius = chunkRadius;
            return this;
        }

        public Builder allowBreak(boolean allowBreak) {
            this.allowBreak = allowBreak;
            return this;
        }

        public Builder allowPlace(boolean allowPlace) {
            this.allowPlace = allowPlace;
            return this;
        }

        public Builder canSprint(boolean canSprint) {
            this.canSprint = canSprint;
            return this;
        }

        public Builder allowParkour(boolean allowParkour) {
            this.allowParkour = allowParkour;
            return this;
        }

        public Builder allowNeo(boolean allowNeo) {
            this.allowNeo = allowNeo;
            return this;
        }

        public Builder allowWallClimb(boolean allowWallClimb) {
            this.allowWallClimb = allowWallClimb;
            return this;
        }

        public Builder timeouts(long primaryTimeoutMs, long failureTimeoutMs) {
            this.primaryTimeoutMs = primaryTimeoutMs;
            this.failureTimeoutMs = failureTimeoutMs;
            return this;
        }

        public EntityPathRequest build() {
            return new EntityPathRequest(level, start, goal, items, mainThread, chunkRadius, allowBreak, allowPlace,
                    canSprint, allowParkour, allowNeo, allowWallClimb, primaryTimeoutMs, failureTimeoutMs);
        }
    }
}
