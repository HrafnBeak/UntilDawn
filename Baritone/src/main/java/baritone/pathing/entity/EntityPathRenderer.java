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

package baritone.pathing.entity;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.event.events.RenderEvent;
import baritone.api.pathing.entity.IEntityPathRenderer;
import baritone.api.pathing.entity.RenderedPath;
import baritone.api.utils.IPlayerContext;
import baritone.utils.PathRenderer;

import java.util.List;

/**
 * Draws other paths alongside the player's, from {@link PathRenderer#render}.
 */
public enum EntityPathRenderer implements IEntityPathRenderer {
    INSTANCE;

    private volatile List<RenderedPath> paths = List.of();

    @Override
    public void setPaths(List<RenderedPath> paths) {
        this.paths = List.copyOf(paths);
    }

    @Override
    public List<RenderedPath> getPaths() {
        return paths;
    }

    public void render(RenderEvent event, IPlayerContext ctx) {
        Settings settings = Baritone.settings();
        for (RenderedPath path : paths) {
            if (path.goal() != null && settings.renderGoal.value) {
                PathRenderer.drawGoal(event.getModelViewStack(), ctx, path.goal(), event.getPartialTicks(), path.goalColor());
            }
            if (!settings.renderPath.value) {
                continue;
            }
            if (settings.renderSelectionBoxes.value) {
                PathRenderer.drawManySelectionBoxes(event.getModelViewStack(), ctx.player(), path.toBreak(), settings.colorBlocksToBreak.value);
                PathRenderer.drawManySelectionBoxes(event.getModelViewStack(), ctx.player(), path.toPlace(), settings.colorBlocksToPlace.value);
            }
            if (path.positions().size() > 1) {
                PathRenderer.drawPath(event.getModelViewStack(), path.positions(), path.startIndex(), path.pathColor(), settings.fadePath.value, 10, 20);
            }
        }
    }
}
