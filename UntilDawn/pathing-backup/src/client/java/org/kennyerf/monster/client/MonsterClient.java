package org.kennyerf.monster.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import org.kennyerf.monster.registry.ModEntities;

public class MonsterClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // The custom mobs reuse the vanilla models and textures; their gear tells them apart.
        EntityRenderers.register(ModEntities.MINER, ZombieRenderer::new);
        EntityRenderers.register(ModEntities.BUILDER, ZombieRenderer::new);
        EntityRenderers.register(ModEntities.BOMBER, CreeperRenderer::new);
        EntityRenderers.register(ModEntities.ARCHER, SkeletonRenderer::new);
        EntityRenderers.register(ModEntities.LONGBOWMAN, SkeletonRenderer::new);
    }
}
