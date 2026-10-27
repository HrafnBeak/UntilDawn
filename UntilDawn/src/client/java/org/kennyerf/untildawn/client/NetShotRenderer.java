package org.kennyerf.untildawn.client;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import org.kennyerf.untildawn.entity.NetShot;

/** A Net Shot: the arrow model, in the string texture (a placeholder). */
public class NetShotRenderer extends ArrowRenderer<NetShot, ArrowRenderState> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/item/string.png");

    public NetShotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected Identifier getTextureLocation(ArrowRenderState state) {
        return TEXTURE;
    }

    @Override
    public ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }
}
