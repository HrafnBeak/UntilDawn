package org.kennyerf.untildawn.client;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import org.kennyerf.untildawn.entity.WebShot;

/** A Webber's shot: the arrow model, in the cobweb texture. */
public class WebShotRenderer extends ArrowRenderer<WebShot, ArrowRenderState> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/block/cobweb.png");

    public WebShotRenderer(EntityRendererProvider.Context context) {
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
