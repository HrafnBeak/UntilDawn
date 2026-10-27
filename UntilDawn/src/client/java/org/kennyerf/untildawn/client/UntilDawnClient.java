package org.kennyerf.untildawn.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.CaveSpiderRenderer;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.StrayRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import org.kennyerf.untildawn.registry.ModEntities;
import org.kennyerf.untildawn.workbench.WorkbenchPayloads;

public class UntilDawnClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // The custom mobs reuse the vanilla models and textures; their gear tells them apart.
        EntityRenderers.register(ModEntities.MINER, ZombieRenderer::new);
        EntityRenderers.register(ModEntities.BUILDER, ZombieRenderer::new);
        EntityRenderers.register(ModEntities.BOMBER, CreeperRenderer::new);
        EntityRenderers.register(ModEntities.ARCHER, SkeletonRenderer::new);
        EntityRenderers.register(ModEntities.LONGBOWMAN, SkeletonRenderer::new);
        EntityRenderers.register(ModEntities.SPRINTER, ZombieRenderer::new);
        EntityRenderers.register(ModEntities.SWARMER, ZombieRenderer::new);
        EntityRenderers.register(ModEntities.WARRIOR, HuskRenderer::new);
        EntityRenderers.register(ModEntities.ASSASSIN, StrayRenderer::new);
        EntityRenderers.register(ModEntities.WEBBER, SpiderRenderer::new);
        EntityRenderers.register(ModEntities.SNEAKING_SPIDER, CaveSpiderRenderer::new);
        EntityRenderers.register(ModEntities.WEB_SHOT, WebShotRenderer::new);

        // A Workbench's state opens its screen, or refreshes it if it is already open.
        ClientPlayNetworking.registerGlobalReceiver(WorkbenchPayloads.State.TYPE, (state, context) -> {
            if (context.client().gui.screen() instanceof WorkbenchScreen screen && screen.pos().equals(state.pos())) {
                screen.update(state);
            } else if (state.open()) {
                context.client().gui.setScreen(new WorkbenchScreen(state));
            }
        });

        // /untildawn path show and predict are drawn by Baritone; without it the server knows not to send paths.
        if (FabricLoader.getInstance().isModLoaded("baritone")) {
            BaritonePathOverlay.init();
        }
    }
}
