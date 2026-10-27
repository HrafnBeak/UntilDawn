package org.kennyerf.monster.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.kennyerf.monster.Monster;
import org.kennyerf.monster.entity.ArcherSkeleton;
import org.kennyerf.monster.entity.BomberCreeper;
import org.kennyerf.monster.entity.BuilderZombie;
import org.kennyerf.monster.entity.LongbowSkeleton;
import org.kennyerf.monster.entity.MinerZombie;

public final class ModEntities {
    public static final EntityType<MinerZombie> MINER = register("miner",
            EntityType.Builder.<MinerZombie>of(MinerZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<BuilderZombie> BUILDER = register("builder",
            EntityType.Builder.<BuilderZombie>of(BuilderZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<BomberCreeper> BOMBER = register("bomber",
            EntityType.Builder.of(BomberCreeper::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<ArcherSkeleton> ARCHER = register("archer",
            EntityType.Builder.of(ArcherSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<LongbowSkeleton> LONGBOWMAN = register("longbowman",
            EntityType.Builder.of(LongbowSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(10).notInPeaceful());

    private ModEntities() {
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Monster.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(MINER, MinerZombie.createAttributes());
        FabricDefaultAttributeRegistry.register(BUILDER, BuilderZombie.createAttributes());
        FabricDefaultAttributeRegistry.register(BOMBER, BomberCreeper.createAttributes());
        FabricDefaultAttributeRegistry.register(ARCHER, ArcherSkeleton.createAttributes());
        FabricDefaultAttributeRegistry.register(LONGBOWMAN, LongbowSkeleton.createAttributes());
    }
}
