package org.kennyerf.untildawn.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.kennyerf.untildawn.UntilDawn;
import org.kennyerf.untildawn.entity.ArcherSkeleton;
import org.kennyerf.untildawn.entity.Assassin;
import org.kennyerf.untildawn.entity.SneakingSpider;
import org.kennyerf.untildawn.entity.WebShot;
import org.kennyerf.untildawn.entity.Webber;
import org.kennyerf.untildawn.entity.BomberCreeper;
import org.kennyerf.untildawn.entity.BuilderZombie;
import org.kennyerf.untildawn.entity.LongbowSkeleton;
import org.kennyerf.untildawn.entity.MinerZombie;
import org.kennyerf.untildawn.entity.SprinterZombie;
import org.kennyerf.untildawn.entity.Swarmer;
import org.kennyerf.untildawn.entity.Warrior;
import org.kennyerf.untildawn.entity.WaveZombie;

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

    public static final EntityType<SprinterZombie> SPRINTER = register("sprinter",
            EntityType.Builder.<SprinterZombie>of(SprinterZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<Swarmer> SWARMER = register("swarmer",
            EntityType.Builder.<Swarmer>of(Swarmer::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<Warrior> WARRIOR = register("warrior",
            EntityType.Builder.<Warrior>of(Warrior::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<Assassin> ASSASSIN = register("assassin",
            EntityType.Builder.of(Assassin::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<Webber> WEBBER = register("webber",
            EntityType.Builder.of(Webber::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.9F).eyeHeight(0.65F).passengerAttachments(0.765F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<SneakingSpider> SNEAKING_SPIDER = register("sneaking_spider",
            EntityType.Builder.of(SneakingSpider::new, MobCategory.MONSTER)
                    .sized(0.7F, 0.5F).eyeHeight(0.45F).clientTrackingRange(8).notInPeaceful());

    public static final EntityType<WebShot> WEB_SHOT = register("web_shot",
            EntityType.Builder.<WebShot>of(WebShot::new, MobCategory.MISC)
                    .noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));

    private ModEntities() {
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, UntilDawn.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(MINER, WaveZombie.createAttributes());
        FabricDefaultAttributeRegistry.register(BUILDER, WaveZombie.createAttributes());
        FabricDefaultAttributeRegistry.register(BOMBER, BomberCreeper.createAttributes());
        FabricDefaultAttributeRegistry.register(ARCHER, ArcherSkeleton.createAttributes());
        FabricDefaultAttributeRegistry.register(LONGBOWMAN, ArcherSkeleton.createAttributes());
        FabricDefaultAttributeRegistry.register(SPRINTER, SprinterZombie.createAttributes());
        FabricDefaultAttributeRegistry.register(SWARMER, Swarmer.createAttributes());
        FabricDefaultAttributeRegistry.register(WARRIOR, Warrior.createAttributes());
        FabricDefaultAttributeRegistry.register(ASSASSIN, Assassin.createAttributes());
        FabricDefaultAttributeRegistry.register(WEBBER, Webber.createAttributes());
        FabricDefaultAttributeRegistry.register(SNEAKING_SPIDER, SneakingSpider.createAttributes());
    }
}
