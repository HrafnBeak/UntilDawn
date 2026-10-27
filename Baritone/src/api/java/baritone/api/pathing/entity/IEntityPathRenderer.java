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

import java.util.List;

/**
 * Draws paths that aren't the player's (entities', predictions) with Baritone's path renderer, in the same pass and
 * style as the player's own path. {@link baritone.api.Settings#renderPath}, {@code renderGoal} and
 * {@code renderSelectionBoxes} apply to them too. They are drawn in whatever dimension the player is in.
 * <p>
 * Get it from {@link baritone.api.IBaritoneProvider#getEntityPathRenderer()}.
 */
public interface IEntityPathRenderer {

    /**
     * Replaces the paths drawn; an empty list draws none. Safe to call from any thread.
     */
    void setPaths(List<RenderedPath> paths);

    List<RenderedPath> getPaths();
}
