package org.kennyerf.untildawn.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.kennyerf.untildawn.UntilDawn;
import org.kennyerf.untildawn.baritone.BaritonePrediction;
import org.kennyerf.untildawn.command.ItemList;
import org.kennyerf.untildawn.registry.ModComponents;
import org.kennyerf.untildawn.workbench.TechNode;
import org.kennyerf.untildawn.workbench.TechTree;
import org.kennyerf.untildawn.workbench.WorkbenchBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import org.kennyerf.untildawn.entity.ArcherSkeleton;
import org.kennyerf.untildawn.entity.Assassin;
import org.kennyerf.untildawn.entity.SneakingSpider;
import org.kennyerf.untildawn.entity.Webber;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.registry.ModItems;
import org.kennyerf.untildawn.entity.BuilderZombie;
import org.kennyerf.untildawn.entity.LongbowSkeleton;
import org.kennyerf.untildawn.entity.MinerZombie;
import org.kennyerf.untildawn.entity.SprinterZombie;
import org.kennyerf.untildawn.entity.Swarmer;
import org.kennyerf.untildawn.entity.Warrior;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.pathing.AccessCorridors;
import org.kennyerf.untildawn.pathing.MobPath;
import org.kennyerf.untildawn.pathing.MobPathfinder;
import org.kennyerf.untildawn.pathing.MoveType;
import org.kennyerf.untildawn.pathing.PathAbilities;
import org.kennyerf.untildawn.pathing.PathBudget;
import org.kennyerf.untildawn.pathing.PathGoal;
import org.kennyerf.untildawn.pathing.PathStep;
import org.kennyerf.untildawn.pathing.SquadSupport;
import org.kennyerf.untildawn.registry.ModEntities;
import org.kennyerf.untildawn.squad.Squad;
import org.kennyerf.untildawn.squad.Squads;
import org.kennyerf.untildawn.squad.WorkRequest;
import org.kennyerf.untildawn.world.BlockDamage;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side scenarios in a 16x16 arena with a stone floor at y=0 (mobs stand at y=1). Run with
 * {@code gradlew runGameTest}. Baritone only plans inside the client, so what needs it is in
 * {@link UntilDawnClientGameTests}.
 */
public class UntilDawnGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("untildawn-test");
    private static final String ARENA = "untildawn-test:arena";
    private static final BlockPos START = new BlockPos(3, 1, 8);

    // ---------------------------------------------------------------- /untildawn path predict

    @GameTest(structure = ARENA)
    public void predictParsesItemLists(GameTestHelper helper) {
        List<ItemStack> items = parseItems(helper, "iron_pickaxe  cobblestone*200 diamond_boots[enchantments={frost_walker:2}]");
        helper.assertValueEqual(items.size(), 3, "items");
        helper.assertTrue(items.get(0).is(Items.IRON_PICKAXE) && items.get(0).getCount() == 1, "one pickaxe");
        helper.assertTrue(items.get(1).is(Items.COBBLESTONE) && items.get(1).getCount() == 200, "counts may exceed a stack");
        helper.assertTrue(items.get(2).is(Items.DIAMOND_BOOTS) && !items.get(2).getEnchantments().isEmpty(), "components are kept");
        helper.assertTrue(parseItems(helper, " ").isEmpty(), "blank means nothing");
        for (String bad : List.of("cobblestone*0", "cobblestone*x", "no_such_item", "iron_pickaxe,dirt")) {
            try {
                ItemList.parse(bad, helper.getLevel().registryAccess());
                helper.fail("should reject " + bad);
            } catch (CommandSyntaxException expected) {
                // rejected, as it should be
            }
        }
        helper.succeed();
    }

    /** Baritone plans inside the client; a server must say so instead of failing when asked. */
    @GameTest(structure = ARENA)
    public void predictNeedsClient(GameTestHelper helper) {
        helper.assertFalse(BaritonePrediction.isAvailable(), "Baritone predictions can't run on a dedicated server");
        helper.succeed();
    }

    // ---------------------------------------------------------------- workbench

    /** The mod's datapack tech tree loads, in order, with tags in its craft costs resolved. */
    @GameTest(structure = ARENA)
    public void techTreeLoads(GameTestHelper helper) {
        List<TechNode> nodes = TechTree.all();
        helper.assertValueEqual(nodes.size(), 3, "tech tree items");
        helper.assertTrue(nodes.get(0).createResult().is(Items.WOODEN_SWORD) && nodes.get(0).parent().isEmpty(), "the wooden sword is the top");
        helper.assertTrue(nodes.get(2).createResult().is(Items.IRON_SWORD) && nodes.get(2).researchCost() == 30, "the iron sword is last");
        TechNode.Cost planks = nodes.get(0).craftCost().getFirst();
        helper.assertTrue(planks.ingredient().test(new ItemStack(Items.BIRCH_PLANKS)) && planks.count() == 2, "any planks will do");
        helper.succeed();
    }

    /** Breaking a Workbench keeps its research on the dropped item, and placing that item again restores it. */
    @GameTest(structure = ARENA)
    public void workbenchKeepsResearchWhenBroken(GameTestHelper helper) {
        BlockPos pos = new BlockPos(8, 1, 8);
        helper.setBlock(pos, ModBlocks.WORKBENCH);
        Identifier wooden = UntilDawn.id("wooden_sword");
        helper.getBlockEntity(pos, WorkbenchBlockEntity.class).research(wooden);
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        ItemEntity drop = helper.getEntities(EntityTypes.ITEM).stream()
                .filter(item -> item.getItem().is(ModItems.WORKBENCH)).findFirst().orElse(null);
        helper.assertTrue(drop != null, "the bench should drop itself");
        helper.assertValueEqual(drop.getItem().get(ModComponents.RESEARCH), List.of(wooden), "research on the item");

        BlockPos again = new BlockPos(4, 1, 4);
        helper.setBlock(again, ModBlocks.WORKBENCH);
        WorkbenchBlockEntity placed = helper.getBlockEntity(again, WorkbenchBlockEntity.class);
        placed.applyComponentsFromItemStack(drop.getItem());
        helper.assertTrue(placed.isResearched(wooden), "placing the bench again restores its research");
        helper.succeed();
    }

    // ---------------------------------------------------------------- mobs

    /**
     * With the target straight overhead and no wall in reach to hang a ladder on, the Builder puts up a pillar of planks
     * and climbs a ladder up its side: a way up others can follow, where a tower would be one only it could use.
     */
    @GameTest(structure = ARENA, maxTicks = 800)
    public void builderLaddersUpAPillarInTheOpen(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 5, 8), Blocks.STONE);
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(3, 6, 8));
        cow.setNoAi(true);
        BuilderZombie builder = helper.spawn(ModEntities.BUILDER, START);
        builder.setTarget(cow);
        watchBuilder(helper, builder);
        passWhen(helper, "builderLaddersUpAPillarInTheOpen", () -> {
            helper.assertTrue(isHurt(cow), "the builder has not reached the cow yet: " + state(helper, builder));
            helper.assertTrue(count(helper, ModBlocks.UNSTABLE_LADDER) >= 3, "the builder should have climbed on ladders");
            helper.assertTrue(count(helper, ModBlocks.UNSTABLE_PLANKS) >= 2, "with no wall, the ladders hang on planks");
        });
    }

    /** Up against a wall, the Builder hangs ladders on it and climbs them to the target on top. */
    @GameTest(structure = ARENA, maxTicks = 800)
    public void builderLaddersUpAWall(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(6, 1, 5), new BlockPos(10, 4, 11))) {
            helper.setBlock(pos.immutable(), Blocks.STONE);
        }
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(8, 5, 8));
        cow.setNoAi(true);
        BuilderZombie builder = helper.spawn(ModEntities.BUILDER, START);
        builder.setTarget(cow);
        watchBuilder(helper, builder);
        passWhen(helper, "builderLaddersUpAWall", () -> {
            helper.assertTrue(isHurt(cow), "the builder has not climbed to the cow yet: " + state(helper, builder));
            helper.assertTrue(count(helper, ModBlocks.UNSTABLE_LADDER) > 0, "the builder should have climbed the wall on ladders");
        });
    }

    /**
     * Stranded on a platform six blocks up, too far to jump down, the Builder builds its way down (a ladder over the edge,
     * or a stair) and gets to the cow below without a fall.
     */
    @GameTest(structure = ARENA, maxTicks = 800)
    public void builderClimbsDownSafely(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 6, 7), new BlockPos(4, 6, 9))) {
            helper.setBlock(pos.immutable(), Blocks.STONE);
        }
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(12, 1, 8));
        cow.setNoAi(true);
        BuilderZombie builder = helper.spawn(ModEntities.BUILDER, new BlockPos(3, 7, 8));
        builder.setTarget(cow);
        watchBuilder(helper, builder);
        passWhen(helper, "builderClimbsDownSafely", () -> {
            helper.assertTrue(isHurt(cow), "the builder has not got down to the cow yet: " + state(helper, builder));
            helper.assertTrue(count(helper, ModBlocks.UNSTABLE_LADDER) + count(helper, ModBlocks.UNSTABLE_PLANKS) > 0, "it should have built its way down");
        });
    }

    /**
     * A platform up in the air at the far end of the arena: the Builder walks across the floor to it and builds up beside
     * it, rather than climbing where it starts and bridging across through the air.
     */
    @GameTest(structure = ARENA, maxTicks = 800)
    public void builderWalksBeforeItClimbs(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(11, 6, 6), new BlockPos(13, 6, 10))) {
            helper.setBlock(pos.immutable(), Blocks.STONE);
        }
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(12, 7, 8));
        cow.setNoAi(true);
        BuilderZombie builder = helper.spawn(ModEntities.BUILDER, new BlockPos(2, 1, 8));
        builder.setTarget(cow);
        watchBuilder(helper, builder);
        passWhen(helper, "builderWalksBeforeItClimbs", () -> {
            helper.assertTrue(isHurt(cow), "the builder has not reached the cow yet: " + state(helper, builder));
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(8, 9, 15)))) {
                BlockState state = helper.getLevel().getBlockState(pos);
                helper.assertFalse(state.is(ModBlocks.UNSTABLE_LADDER) || state.is(ModBlocks.UNSTABLE_PLANKS),
                        "the builder should build up beside the platform, not at " + pos.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
            }
        });
    }

    /** A Builder in the middle of a fight still leaves it to build what a squadmate asks for. */
    @GameTest(structure = ARENA, maxTicks = 400)
    public void builderLeavesAFightToHelp(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(11, 1, 8));
        cow.setNoAi(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        cow.setHealth(1000.0F);
        BuilderZombie builder = helper.spawn(ModEntities.BUILDER, new BlockPos(10, 1, 8));
        builder.setTarget(cow);
        Swarmer swarmer = helper.spawn(ModEntities.SWARMER, new BlockPos(3, 1, 8));
        swarmer.setNoAi(true);
        BlockPos gap = new BlockPos(4, 0, 8);
        helper.setBlock(gap, Blocks.AIR);
        Squad squad = Squads.form(helper.getLevel(), List.of(builder, swarmer));
        BlockPos from = helper.absolutePos(new BlockPos(3, 1, 8));
        BlockPos to = helper.absolutePos(new BlockPos(4, 1, 8));
        squad.request(WorkRequest.Kind.BUILD, List.of(
                new PathStep(from, MoveType.START, List.of(), List.of()),
                new PathStep(to, MoveType.TRAVERSE, List.of(), List.of(to.below()))), swarmer);
        passWhen(helper, "builderLeavesAFightToHelp", () ->
                helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(gap)).is(ModBlocks.UNSTABLE_PLANKS),
                        "the builder should have bridged the gap for the swarmer: " + state(helper, builder)));
    }

    // ---------------------------------------------------------------- Builder plans

    /** Straight up in the open, a Builder plans one straight pillar of planks with a ladder up its side, not a tower. */
    @GameTest(structure = ARENA)
    public void builderPlansALadderPillar(GameTestHelper helper) {
        MobPath path = plan(helper, PathAbilities.BUILDER, START, PathGoal.near(helper.absolutePos(new BlockPos(3, 7, 8)), 0, "up"));
        helper.assertTrue(path != null && path.reachesGoal(), "should reach the top: " + moves(helper, path));
        helper.assertFalse(path.steps().stream().anyMatch(step -> step.type() == MoveType.PILLAR), "no tower: " + moves(helper, path));
        Set<Long> pillar = new HashSet<>();
        int ladders = 0;
        for (PathStep step : path.steps()) {
            ladders += step.ladders().size();
            for (BlockPos spot : step.places()) {
                if (!step.ladders().contains(spot)) {
                    pillar.add(BlockPos.asLong(spot.getX(), 0, spot.getZ()));
                }
            }
        }
        helper.assertTrue(ladders >= 5, "a ladder all the way up: " + moves(helper, path));
        helper.assertValueEqual(pillar.size(), 1, "columns of planks the ladder hangs on");
        assertStairsStraight(helper, path);
        helper.succeed();
    }

    /** Down off a platform too high to drop from, a Builder plans a way down that never drops further than is safe. */
    @GameTest(structure = ARENA)
    public void builderPlansAWayDown(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 6, 7), new BlockPos(4, 6, 9))) {
            helper.setBlock(pos.immutable(), Blocks.STONE);
        }
        MobPath path = plan(helper, PathAbilities.BUILDER, new BlockPos(3, 7, 8), PathGoal.near(helper.absolutePos(new BlockPos(12, 1, 8)), 1, "down"));
        helper.assertTrue(path != null && path.reachesGoal(), "should get down: " + moves(helper, path));
        List<PathStep> steps = path.steps();
        for (int i = 1; i < steps.size(); i++) {
            int drop = steps.get(i - 1).pos().getY() - steps.get(i).pos().getY();
            helper.assertTrue(drop <= UntilDawnConfig.MAX_FALL.get(), "a drop of " + drop + ": " + moves(helper, path));
        }
        helper.assertTrue(path.placeCount() > 0, "it has to build the way down: " + moves(helper, path));
        assertStairsStraight(helper, path);
        helper.succeed();
    }

    /** A walker can't build, so the same drop leaves it no way down of its own. */
    @GameTest(structure = ARENA)
    public void walkersCantBuildDown(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 6, 7), new BlockPos(4, 6, 9))) {
            helper.setBlock(pos.immutable(), Blocks.STONE);
        }
        MobPath path = plan(helper, PathAbilities.WALKER, new BlockPos(3, 7, 8), PathGoal.near(helper.absolutePos(new BlockPos(12, 1, 8)), 1, "down"));
        helper.assertTrue(path == null || !path.reachesGoal(), "a walker should find no way down");
        helper.succeed();
    }

    /** A composition bigger than the squad limit spawns as several squads, with its workers dealt out between them. */
    @GameTest(structure = ARENA)
    public void bigCompositionsSplitWithWorkersSpread(GameTestHelper helper) {
        List<Mob> mobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            EntityType<? extends Mob> type = i < 2 ? ModEntities.BUILDER : i < 4 ? ModEntities.MINER : ModEntities.ARCHER;
            Mob mob = helper.spawn(type, new BlockPos(2 + i, 1, 8));
            mob.setNoAi(true);
            mobs.add(mob);
        }
        List<Squad> squads = Squads.formSpread(helper.getLevel(), mobs, 8);
        helper.assertValueEqual(squads.size(), 2, "squads");
        for (Squad squad : squads) {
            helper.assertTrue(squad.members().size() <= 8, "a squad is over the limit: " + squad);
            long builders = squad.members().stream().filter(mob -> mob instanceof BuilderZombie).count();
            long miners = squad.members().stream().filter(mob -> mob instanceof MinerZombie).count();
            helper.assertTrue(builders == 1 && miners == 1, "each squad should get one Builder and one Miner, not " + builders + " and " + miners);
        }
        helper.succeed();
    }

    /** A mob's arrow damages the first block it hits only: after that it is an ordinary arrow and loses its trail. */
    @GameTest(structure = ARENA)
    public void arrowsOnlyDamageTheFirstBlockTheyHit(GameTestHelper helper) {
        LongbowSkeleton longbow = helper.spawn(ModEntities.LONGBOWMAN, new BlockPos(3, 1, 8));
        longbow.setNoAi(true);
        BlockPos first = helper.absolutePos(new BlockPos(8, 0, 8));
        BlockPos second = helper.absolutePos(new BlockPos(9, 0, 8));
        Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.EVENT);
        arrow.setOwner(longbow);
        arrow.addTag(ArcherSkeleton.BLOCK_DAMAGE_TAG);
        arrow.addTag(ArcherSkeleton.TRAIL_TAG);
        BlockDamage.onArrowHit(arrow, first);
        BlockDamage.onArrowHit(arrow, second);
        helper.assertTrue(BlockDamage.damageAt(helper.getLevel(), first) > 0, "the first block hit should be damaged");
        helper.assertTrue(BlockDamage.damageAt(helper.getLevel(), second) == 0, "a second block hit should not be");
        helper.assertFalse(arrow.entityTags().contains(ArcherSkeleton.TRAIL_TAG), "the trail should end at the first block");
        helper.succeed();
    }

    /** Longbow Men wear leather from head to foot and shoot a Punch I bow. */
    @GameTest(structure = ARENA)
    public void longbowMenAreKittedOut(GameTestHelper helper) {
        LongbowSkeleton longbow = helper.spawn(ModEntities.LONGBOWMAN, new BlockPos(3, 1, 8));
        longbow.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(longbow.blockPosition()), EntitySpawnReason.EVENT, null);
        helper.assertTrue(longbow.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)
                && longbow.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)
                && longbow.getItemBySlot(EquipmentSlot.LEGS).is(Items.LEATHER_LEGGINGS)
                && longbow.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS), "a full suit of leather");
        ItemStack bow = longbow.getMainHandItem();
        var punch = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH);
        helper.assertTrue(bow.is(Items.BOW) && bow.getEnchantments().getLevel(punch) == 1, "a Punch I bow, not " + bow);
        helper.succeed();
    }

    /** Without Baritone (a dedicated server) a Sprinter plans with the mod's own planner and still gets there. */
    @GameTest(structure = ARENA, maxTicks = 400)
    public void sprinterWalksWithoutBaritone(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(12, 1, 8));
        cow.setNoAi(true);
        SprinterZombie sprinter = helper.spawn(ModEntities.SPRINTER, START);
        sprinter.setTarget(cow);
        helper.assertTrue(sprinter.pathAbilities().parkour(), "sprinters can parkour");
        passWhen(helper, "sprinterWalksWithoutBaritone", () ->
                helper.assertTrue(isHurt(cow), "the sprinter has not reached the cow yet: " + state(helper, sprinter)));
    }

    /**
     * Without Baritone a Sprinter still jumps gaps: two platforms 3 blocks apart, too high to drop off and walk round,
     * and one block up to a third so the jump also has to climb.
     */
    @GameTest(structure = ARENA, maxTicks = 400)
    public void sprinterJumpsGapWithoutBaritone(GameTestHelper helper) {
        for (int z = 7; z <= 9; z++) {
            for (int x = 1; x <= 4; x++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
            for (int x = 8; x <= 9; x++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
            for (int x = 12; x <= 14; x++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
            }
        }
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(13, 6, 8));
        cow.setNoAi(true);
        SprinterZombie sprinter = helper.spawn(ModEntities.SPRINTER, new BlockPos(2, 5, 8));
        sprinter.setTarget(cow);
        passWhen(helper, "sprinterJumpsGapWithoutBaritone", () ->
                helper.assertTrue(isHurt(cow), "the sprinter has not jumped across to the cow yet: " + state(helper, sprinter)));
    }

    /** A mob standing where a squadmate is working is asked to move, and gets out of those cells. */
    @GameTest(structure = ARENA, maxTicks = 200)
    public void mobsMakeWayForWork(GameTestHelper helper) {
        Swarmer swarmer = helper.spawn(ModEntities.SWARMER, new BlockPos(8, 1, 8));
        BuilderZombie builder = helper.spawn(ModEntities.BUILDER, new BlockPos(5, 1, 8));
        builder.setNoAi(true);
        BlockPos spot = helper.absolutePos(new BlockPos(8, 1, 8));
        LongSet cells = new LongOpenHashSet();
        cells.add(spot.asLong());
        cells.add(spot.above().asLong());
        swarmer.pathController().makeWay(cells, builder.pathController(), false);
        helper.assertTrue(swarmer.pathController().isYielding(), "the swarmer should have been asked to move: swarmer at " + swarmer.getBoundingBox()
                + ", spot " + spot);
        helper.succeedWhen(() -> {
            helper.assertFalse(swarmer.getBoundingBox().intersects(new AABB(spot)), "the swarmer is still in the way: " + state(helper, swarmer));
            LOGGER.info("mobsMakeWayForWork passed on tick {}", helper.getTick());
        });
    }

    /** Mobs from different squads heading for one target spread out instead of stacking on the same cells. */
    @GameTest(structure = ARENA, maxTicks = 120)
    public void mobsSpreadOutOnTheWay(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(14, 1, 8));
        cow.setNoAi(true);
        List<Mob> mobs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Swarmer swarmer = helper.spawn(ModEntities.SWARMER, new BlockPos(1, 1, 4 + i / 2 * 2 + (i % 2)));
            swarmer.setTarget(cow);
            mobs.add(swarmer);
        }
        int[] samples = {0};
        int[] overlapping = {0};
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 20 || tick > 100) {
                return;
            }
            samples[0]++;
            for (int a = 0; a < mobs.size(); a++) {
                for (int b = a + 1; b < mobs.size(); b++) {
                    double d = mobs.get(a).position().distanceTo(mobs.get(b).position());
                    closest[0] = Math.min(closest[0], d);
                    boolean farFromCow = mobs.get(a).distanceTo(cow) > 3 && mobs.get(b).distanceTo(cow) > 3;
                    if (farFromCow && mobs.get(a).blockPosition().equals(mobs.get(b).blockPosition())) {
                        overlapping[0]++;
                    }
                }
            }
            if (tick == 100) {
                LOGGER.info("mobsSpreadOutOnTheWay: {} pair-ticks on the same block over {} ticks, closest {}", overlapping[0], samples[0], closest[0]);
                helper.succeed();
            }
        });
    }

    /** An axe knocks a Warrior's shield down for a while, and while it is up the Warrior is slow and can't hit. */
    @GameTest(structure = ARENA, maxTicks = 200)
    public void warriorShieldBehavesLikeAPlayers(GameTestHelper helper) {
        Warrior warrior = helper.spawn(ModEntities.WARRIOR, new BlockPos(8, 1, 8));
        warrior.setNoAi(true);
        warrior.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        double normal = warrior.getAttributeValue(Attributes.MOVEMENT_SPEED);
        warrior.startUsingItem(InteractionHand.OFF_HAND);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(warrior.isBlocking(), "the shield should be up");
            helper.assertTrue(!warrior.doHurtTarget(helper.getLevel(), helper.spawn(EntityTypes.COW, new BlockPos(9, 1, 8))),
                    "a Warrior can't attack with its shield up");
            warrior.tick();
            double blocking = warrior.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(blocking > normal * 0.69 && blocking < normal * 0.71,
                    "the shield should slow the Warrior to 0.7 of its speed, not " + blocking / normal);
            Zombie attacker = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(9, 1, 8));
            attacker.setNoAi(true);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            attacker.startUsingItem(InteractionHand.MAIN_HAND);
            warrior.setYRot(-90.0F);
            warrior.setYHeadRot(-90.0F);
            warrior.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);
            helper.assertFalse(warrior.isBlocking(), "an axe hit should disable the shield");
            warrior.tick();
            helper.assertTrue(warrior.getAttributeValue(Attributes.MOVEMENT_SPEED) > normal * 0.99, "the slowdown should end with the shield");
            helper.succeed();
        });
    }

    /**
     * A 1-wide strip over a drop with a two-high pillar on it and nothing beside it: the only way on is round the pillar
     * through the air beside it, a neo.
     */
    @GameTest(structure = ARENA, maxTicks = 400)
    public void sprinterNeosAroundPillarWithoutBaritone(GameTestHelper helper) {
        for (int x = 1; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 4, 8), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(5, 5, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 6, 8), Blocks.STONE);
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(8, 5, 8));
        cow.setNoAi(true);
        SprinterZombie sprinter = helper.spawn(ModEntities.SPRINTER, new BlockPos(2, 5, 8));
        sprinter.setTarget(cow);
        passWhen(helper, "sprinterNeosAroundPillarWithoutBaritone", () ->
                helper.assertTrue(isHurt(cow), "the sprinter has not got round the pillar yet: " + state(helper, sprinter)));
    }

    /** With nothing to attack or reach, a zombie stays put: its vanilla strolling and village wandering are gone. */
    @GameTest(structure = ARENA, maxTicks = 300)
    public void zombiesDontWanderOnTheirOwn(GameTestHelper helper) {
        Swarmer swarmer = helper.spawn(ModEntities.SWARMER, new BlockPos(8, 1, 8));
        net.minecraft.world.phys.Vec3 start = swarmer.position();
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(swarmer.position().distanceToSqr(start) < 0.5, "the swarmer wandered off: " + state(helper, swarmer));
            helper.succeed();
        });
    }

    /** The air a builder's stair is walked through is kept free of new building once built; the planks themselves are not. */
    @GameTest(structure = ARENA)
    public void builtRoutesKeepTheirAirFree(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(1, 1, 8));
        BlockPos first = helper.absolutePos(new BlockPos(2, 2, 8));
        BlockPos second = helper.absolutePos(new BlockPos(3, 3, 8));
        List<PathStep> steps = List.of(
                new PathStep(start, MoveType.START, List.of(), List.of()),
                new PathStep(first, MoveType.ASCEND, List.of(), List.of(first.below())),
                new PathStep(second, MoveType.ASCEND, List.of(), List.of(second.below())));
        steps.forEach(step -> AccessCorridors.reserve(helper.getLevel(), step));
        helper.assertTrue(AccessCorridors.isReserved(helper.getLevel(), second.getX(), second.getY() + 1, second.getZ()),
                "headroom above the stair should be reserved");
        helper.assertTrue(AccessCorridors.isReserved(helper.getLevel(), first.getX(), first.getY(), first.getZ()),
                "the step's own cell should be reserved");
        helper.assertFalse(AccessCorridors.isReserved(helper.getLevel(), first.getX(), first.getY() - 1, first.getZ()),
                "a plank cell is not reserved");
        helper.assertFalse(AccessCorridors.isReserved(helper.getLevel(), start.getX(), start.getY(), start.getZ()),
                "the way up to the first plank is not reserved");
        helper.succeed();
    }

    /** An Assassin hunts alone, in a black leather chestplate, with a crossbow at the ready and a dagger in the other hand. */
    @GameTest(structure = ARENA)
    public void assassinsWorkAlone(GameTestHelper helper) {
        Assassin assassin = helper.spawn(ModEntities.ASSASSIN, new BlockPos(3, 1, 8));
        assassin.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(assassin.blockPosition()), EntitySpawnReason.EVENT, null);
        helper.assertTrue(Squads.of(assassin) == null, "an Assassin should not be in a squad");
        ItemStack chest = assassin.getItemBySlot(EquipmentSlot.CHEST);
        DyedItemColor colour = chest.get(DataComponents.DYED_COLOR);
        helper.assertTrue(chest.is(Items.LEATHER_CHESTPLATE) && colour != null && colour.rgb() == DyeColor.BLACK.getTextureDiffuseColor(),
                "a black leather chestplate, not " + chest);
        helper.assertTrue(assassin.getMainHandItem().is(Items.CROSSBOW) && assassin.getOffhandItem().is(ModItems.IRON_DAGGER),
                "crossbow in hand and dagger in the other");
        helper.succeed();
    }

    /** Too close to its target, an Assassin backs off to 6 blocks while it winds its crossbow, fires, and draws its dagger on the slowed target. */
    @GameTest(structure = ARENA, maxTicks = 400)
    public void assassinShootsThenDrawsDagger(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(8, 1, 8));
        cow.setNoAi(true);
        Assassin assassin = helper.spawn(ModEntities.ASSASSIN, new BlockPos(6, 1, 8));
        assassin.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(assassin.blockPosition()), EntitySpawnReason.EVENT, null);
        assassin.setTarget(cow);
        boolean[] backedOff = {false};
        passWhen(helper, "assassinShootsThenDrawsDagger", () -> {
            backedOff[0] |= assassin.distanceTo(cow) >= Assassin.RANGE - 0.5;
            helper.assertTrue(backedOff[0], "the assassin should back off before shooting: " + state(helper, assassin));
            MobEffectInstance slow = cow.getEffect(MobEffects.SLOWNESS);
            helper.assertTrue(slow != null && slow.getAmplifier() == 1, "the cow should have been shot with Slowness II: " + state(helper, assassin));
            helper.assertTrue(assassin.holdsDagger(), "the assassin should draw its dagger on a slowed target");
        });
    }

    /**
     * One bolt per load: the crossbow starts winding from nothing after each shot, and the target stays slowed (so the
     * dagger is out) long after. Swapping weapons never loses one.
     */
    @GameTest(structure = ARENA, maxTicks = 140)
    public void assassinFiresOneBoltPerLoad(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(11, 1, 8));
        cow.setNoAi(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        cow.setHealth(1000.0F);
        Assassin assassin = helper.spawn(ModEntities.ASSASSIN, new BlockPos(6, 1, 8));
        assassin.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(assassin.blockPosition()), EntitySpawnReason.EVENT, null);
        assassin.setTarget(cow);
        Set<UUID> bolts = new HashSet<>();
        int[] emptyHandTicks = {0};
        helper.onEachTick(() -> {
            for (Arrow arrow : helper.getLevel().getEntitiesOfClass(Arrow.class, assassin.getBoundingBox().inflate(16.0),
                    arrow -> arrow.getOwner() == assassin)) {
                bolts.add(arrow.getUUID());
            }
            boolean crossbow = assassin.getMainHandItem().is(Items.CROSSBOW) || assassin.getOffhandItem().is(Items.CROSSBOW);
            boolean dagger = assassin.getMainHandItem().is(ModItems.IRON_DAGGER) || assassin.getOffhandItem().is(ModItems.IRON_DAGGER);
            emptyHandTicks[0] = crossbow && dagger ? 0 : emptyHandTicks[0] + 1;
            // Only for the one tick a swap takes.
            helper.assertTrue(emptyHandTicks[0] <= 1, "the assassin lost a weapon: " + assassin.getMainHandItem() + " / " + assassin.getOffhandItem());
        });
        helper.runAfterDelay(130, () -> {
            helper.assertTrue(bolts.size() == 1, "one bolt, then the dagger while the target is slowed; fired " + bolts.size());
            helper.succeed();
        });
    }

    /** A wave spider climbs straight up a sheer wall to the target on top, where a walker would need help. */
    @GameTest(structure = ARENA, maxTicks = 400)
    public void spiderClimbsAWall(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(6, 1, 5), new BlockPos(10, 4, 11))) {
            helper.setBlock(pos.immutable(), Blocks.STONE);
        }
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(8, 5, 8));
        cow.setNoAi(true);
        SneakingSpider spider = helper.spawn(ModEntities.SNEAKING_SPIDER, START);
        spider.setTarget(cow);
        boolean[] climbed = {false};
        helper.onEachTick(() -> {
            MobPath path = spider.pathController().path();
            climbed[0] |= path != null && path.steps().stream().anyMatch(step -> step.type() == MoveType.WALL_CLIMB);
        });
        passWhen(helper, "spiderClimbsAWall", () -> {
            helper.assertTrue(climbed[0], "the spider's path should climb the wall: " + state(helper, spider));
            helper.assertTrue(isHurt(cow), "the spider has not reached the cow yet: " + state(helper, spider));
        });
    }

    /** A web shot slows whoever it hits and leaves Decaying Cobwebs, in air only, around them. */
    @GameTest(structure = ARENA, maxTicks = 100)
    public void webShotsSlowAndLeaveWebs(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(10, 1, 8));
        cow.setNoAi(true);
        Webber webber = helper.spawn(ModEntities.WEBBER, new BlockPos(4, 1, 8));
        webber.setNoAi(true);
        webber.shootWeb(cow);
        passWhen(helper, "webShotsSlowAndLeaveWebs", () -> {
            MobEffectInstance slow = cow.getEffect(MobEffects.SLOWNESS);
            helper.assertTrue(slow != null && slow.getAmplifier() == 0, "the cow should be slowed by the web");
            int webs = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(8, 0, 6)), helper.absolutePos(new BlockPos(12, 3, 10)))) {
                webs += helper.getLevel().getBlockState(pos).is(ModBlocks.DECAYING_COBWEB) ? 1 : 0;
            }
            helper.assertTrue(webs >= 1 && webs <= 3, "one to three webs around the cow, not " + webs);
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(10, 0, 8))).is(Blocks.STONE),
                    "the floor under the cow should not be replaced");
        });
    }

    /** A Decaying Cobweb holds for at least 2 seconds and is gone after 4. */
    @GameTest(structure = ARENA, maxTicks = 120)
    public void decayingCobwebsDecay(GameTestHelper helper) {
        BlockPos web = new BlockPos(8, 1, 8);
        helper.setBlock(web, ModBlocks.DECAYING_COBWEB);
        helper.runAfterDelay(35, () -> helper.assertBlockPresent(ModBlocks.DECAYING_COBWEB, web));
        helper.runAfterDelay(85, () -> {
            helper.assertBlockPresent(Blocks.AIR, web);
            helper.succeed();
        });
    }

    /** A Sneaking Spider creeps up invisible, and only shows itself (for good) within 5 blocks of its target. */
    @GameTest(structure = ARENA, maxTicks = 400)
    public void sneakingSpidersRevealUpClose(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(13, 1, 8));
        cow.setNoAi(true);
        SneakingSpider spider = helper.spawn(ModEntities.SNEAKING_SPIDER, new BlockPos(2, 1, 8));
        spider.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(spider.blockPosition()), EntitySpawnReason.EVENT, null);
        helper.assertTrue(spider.isSneaking(), "a Sneaking Spider starts out invisible");
        MobEffectInstance invisibility = spider.getEffect(MobEffects.INVISIBILITY);
        helper.assertTrue(invisibility != null && !invisibility.isVisible(), "its invisibility gives off no particles");
        spider.setTarget(cow);
        double[] revealedAt = {-1};
        helper.onEachTick(() -> {
            if (revealedAt[0] < 0 && !spider.isSneaking()) {
                revealedAt[0] = spider.distanceTo(cow);
            }
        });
        passWhen(helper, "sneakingSpidersRevealUpClose", () -> {
            helper.assertTrue(revealedAt[0] >= 0, "the spider has not shown itself yet");
            helper.assertTrue(revealedAt[0] <= SneakingSpider.REVEAL_RANGE + 0.5, "it showed itself too far off: " + revealedAt[0]);
            helper.assertTrue(isHurt(cow), "it should go on to bite the cow");
            helper.assertFalse(spider.isInvisible(), "it stays visible");
        });
    }

    // ---------------------------------------------------------------- items

    @GameTest(structure = ARENA)
    public void creativeTabHasEverything(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(UntilDawn.id("untildawn")), "the UntilDawn creative tab is registered");
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(UntilDawn.id("untildawn"));
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
        for (var item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(UntilDawn.MOD_ID)) {
                helper.assertTrue(tab.contains(new ItemStack(item)), "the creative tab is missing " + BuiltInRegistries.ITEM.getKey(item));
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static List<ItemStack> parseItems(GameTestHelper helper, String text) {
        try {
            return ItemList.parse(text, helper.getLevel().registryAccess());
        } catch (CommandSyntaxException e) {
            throw new AssertionError("should parse " + text + ": " + e.getMessage(), e);
        }
    }

    private static void passWhen(GameTestHelper helper, String name, Runnable asserter) {
        helper.succeedWhen(() -> {
            asserter.run();
            LOGGER.info("{} passed on tick {}", name, helper.getTick());
        });
    }

    /** A Builder's plan from {@code start} (relative to the arena), alone, with time enough to finish searching. */
    private static @Nullable MobPath plan(GameTestHelper helper, PathAbilities abilities, BlockPos start, PathGoal goal) {
        UntilDawnConfig.SEARCH_TIME_LIMIT.set("1000");
        try {
            PathBudget.resetForTick();
            return MobPathfinder.find(helper.getLevel(), abilities, SquadSupport.NONE, new LongOpenHashSet(), helper.absolutePos(start), goal);
        } finally {
            UntilDawnConfig.SEARCH_TIME_LIMIT.reset();
        }
    }

    private static String moves(GameTestHelper helper, @Nullable MobPath path) {
        if (path == null) {
            return "no path";
        }
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return path.steps().stream().map(step -> step.type() + "@" + step.pos().subtract(origin).toShortString()
                + (step.places().isEmpty() ? "" : " places " + step.places().stream().map(p -> p.subtract(origin).toShortString()).toList())).toList().toString();
    }

    /** Every stair the route builds goes straight on the way it started: no zig-zags. */
    private static void assertStairsStraight(GameTestHelper helper, MobPath path) {
        int[] heading = null;
        List<PathStep> steps = path.steps();
        for (int i = 1; i < steps.size(); i++) {
            PathStep step = steps.get(i);
            if (!step.standsOnBuilt()) {
                heading = null;
                continue;
            }
            if (step.isBuiltStair()) {
                BlockPos from = steps.get(i - 1).pos();
                int[] way = {step.pos().getX() - from.getX(), step.pos().getZ() - from.getZ()};
                helper.assertTrue(heading == null || Arrays.equals(heading, way), "a stair turns: " + moves(helper, path));
                heading = way;
            }
        }
    }

    /** Every tick: the Builder's route never turns a stair, and it never falls further than is safe. */
    private static void watchBuilder(GameTestHelper helper, Mob builder) {
        double[] highest = {Double.NaN};
        helper.onEachTick(() -> {
            MobPath path = ((WaveMob) builder).pathController().path();
            if (path != null) {
                assertStairsStraight(helper, path);
            }
            if (builder.onGround() || builder.onClimbable() || builder.isInWater()) {
                if (!Double.isNaN(highest[0])) {
                    double fell = highest[0] - builder.getY();
                    helper.assertTrue(fell <= UntilDawnConfig.MAX_FALL.get() + 0.5,
                            "the builder fell " + String.format("%.1f", fell) + " blocks: " + state(helper, builder));
                }
                highest[0] = Double.NaN;
            } else if (Double.isNaN(highest[0]) || builder.getY() > highest[0]) {
                highest[0] = builder.getY();
            }
        });
    }

    /** How many of a block there are in the arena. */
    private static int count(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(15, 9, 15)))) {
            count += helper.getLevel().getBlockState(pos).is(block) ? 1 : 0;
        }
        return count;
    }

    private static boolean isHurt(LivingEntity entity) {
        return entity.getHealth() < entity.getMaxHealth() || !entity.isAlive();
    }

    private static String state(GameTestHelper helper, Mob mob) {
        MobPathController controller = ((WaveMob) mob).pathController();
        MobPath path = controller.path();
        BlockPos at = mob.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
        return "at " + at.toShortString() + ", alive=" + mob.isAlive() + ", target=" + mob.getTarget()
                + ", status=" + controller.status()
                + (path == null ? ", no path" : ", path " + controller.index() + "/" + (path.steps().size() - 1)
                + (path.reachesGoal() ? " complete" : " partial") + " breaks=" + path.breakCount() + " places=" + path.placeCount());
    }
}
