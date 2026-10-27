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

import java.util.concurrent.CompletableFuture;

/**
 * Plans paths with Baritone's pathfinder for entities other than the player: mobs, NPCs, or a "what if" for a player
 * with a different inventory. Plans only; nothing is executed.
 * <p>
 * Get it from {@link baritone.api.IBaritoneProvider#getEntityPathPlanner()}. Baritone runs inside the client, so this
 * works in singleplayer and on LAN worlds hosted from the client (on the integrated server's thread), not on dedicated
 * servers.
 *
 * @see EntityPathRequest
 */
public interface IEntityPathPlanner {

    /**
     * Starts planning. Must be called on the level's thread, which is where the result is delivered; the search itself
     * runs on Baritone's pathing threads and reads the chunks loaded within {@link EntityPathRequest#chunkRadius()}.
     *
     * @return completes on {@link EntityPathRequest#mainThread()} with the path, or with a failed result if there is none
     */
    CompletableFuture<EntityPathResult> plan(EntityPathRequest request);
}
