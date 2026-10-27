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

import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.BlockPos;

import java.awt.*;
import java.util.List;

/**
 * A path for {@link IEntityPathRenderer} to draw the way Baritone draws the player's.
 *
 * @param positions  feet positions in order
 * @param startIndex the first position to draw (the step being walked), like the player's path from its current one
 * @param toBreak    blocks to break along the way, boxed in {@link baritone.api.Settings#colorBlocksToBreak}
 * @param toPlace    blocks to place along the way, boxed in {@link baritone.api.Settings#colorBlocksToPlace}
 * @param goal       drawn like the player's goal, or null for none
 * @param pathColor  the line's colour
 * @param goalColor  the goal's colour
 */
public record RenderedPath(List<BetterBlockPos> positions, int startIndex, List<BlockPos> toBreak, List<BlockPos> toPlace,
                           Goal goal, Color pathColor, Color goalColor) {

    public RenderedPath {
        positions = List.copyOf(positions);
        toBreak = List.copyOf(toBreak);
        toPlace = List.copyOf(toPlace);
    }
}
