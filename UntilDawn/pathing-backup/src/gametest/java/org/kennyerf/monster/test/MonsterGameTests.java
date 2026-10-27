package org.kennyerf.monster.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.entity.WaveMob;
import org.kennyerf.monster.entity.ai.MobPathController;
import org.kennyerf.monster.mixin.CreeperAccessor;
import org.kennyerf.monster.entity.ArcherSkeleton;
import org.kennyerf.monster.entity.BomberCreeper;
import org.kennyerf.monster.entity.BuilderZombie;
import org.kennyerf.monster.entity.MinerZombie;
import org.kennyerf.monster.pathing.MobPath;
import org.kennyerf.monster.pathing.MobPathfinder;
import org.kennyerf.monster.pathing.PathAbilities;
import org.kennyerf.monster.pathing.PathBudget;
import org.kennyerf.monster.pathing.PathGoal;
import org.kennyerf.monster.pathing.PathStep;
import org.kennyerf.monster.registry.ModEntities;
import org.kennyerf.monster.team.WaveTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scenarios for the pathfinder and the mobs' behaviour, in a 16x16 arena with a stone floor at y=0
 * (mobs stand at y=1). Run with {@code gradlew runGameTest}.
 */
public class MonsterGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("monster-test");
    private static final String ARENA = "monster-test:arena";

    private static final BlockPos START = new BlockPos(3, 1, 8);
    /** Sealed stone room: shell x10-14 y1-3 z6-10, hollow inside, target stands at its centre. */
    private static final BlockPos ROOM_TARGET = new BlockPos(12, 1, 8);
    /** 3x3 platform on a column, top surface at y=5. */
    private static final BlockPos PLATFORM_TARGET = new BlockPos(12, 5, 8);
    /** Wall along x=8 with a gap at z=15. */
    private static final BlockPos BEHIND_WALL_TARGET = new BlockPos(12, 1, 3);

    // ---------------------------------------------------------------- planner

    @GameTest(structure = ARENA)
    public void plannerWalkerGoesAroundWall(GameTestHelper helper) {
        buildWallWithGap(helper);
        Mob archer = frozen(helper, ModEntities.ARCHER, new BlockPos(3, 1, 3));
        MobPath path = plan(helper, archer, PathAbilities.WALKER, BEHIND_WALL_TARGET);
        helper.assertTrue(path != null && path.reachesGoal(), "archer should find a way around the wall");
        helper.assertValueEqual(path.breakCount(), 0, "breaks");
        helper.assertValueEqual(path.placeCount(), 0, "places");
        helper.assertTrue(path.steps().size() - 1 > 12, "path should detour, got " + (path.steps().size() - 1) + " steps");
        helper.succeed();
    }

    @GameTest(structure = ARENA)
    public void plannerMinerDigsIntoSealedRoom(GameTestHelper helper) {
        buildSealedRoom(helper);
        Mob miner = frozen(helper, ModEntities.MINER, START);
        MobPath path = plan(helper, miner, PathAbilities.MINER, ROOM_TARGET);
        helper.assertTrue(path != null && path.reachesGoal(), "miner should plan a route into the room");
        helper.assertTrue(path.breakCount() > 0, "miner route should break blocks");

        Mob archer = frozen(helper, ModEntities.ARCHER, START.south());
        MobPath walkerPath = plan(helper, archer, PathAbilities.WALKER, ROOM_TARGET);
        helper.assertTrue(walkerPath == null || !walkerPath.reachesGoal(), "a mob that can't dig must not get into a sealed room");
        helper.succeed();
    }

    @GameTest(structure = ARENA)
    public void plannerBuilderClimbsToPlatform(GameTestHelper helper) {
        buildPlatform(helper);
        Mob builder = frozen(helper, ModEntities.BUILDER, START);
        MobPath path = plan(helper, builder, PathAbilities.BUILDER, PLATFORM_TARGET);
        helper.assertTrue(path != null && path.reachesGoal(), "builder should plan a way up");
        helper.assertTrue(path.placeCount() > 0, "builder route should place blocks");

        Mob archer = frozen(helper, ModEntities.ARCHER, START.south());
        MobPath walkerPath = plan(helper, archer, PathAbilities.WALKER, PLATFORM_TARGET);
        helper.assertTrue(walkerPath == null || !walkerPath.reachesGoal(), "a mob that can't build must not reach the platform");
        helper.succeed();
    }

    @GameTest(structure = ARENA)
    public void plannerFollowsTeammatesTunnel(GameTestHelper helper) {
        buildSealedRoom(helper);
        ServerLevel level = helper.getLevel();
        Mob miner = frozen(helper, ModEntities.MINER, START);
        MobPath minerPath = plan(helper, miner, PathAbilities.MINER, ROOM_TARGET);
        helper.assertTrue(minerPath != null && minerPath.reachesGoal(), "miner should plan a route into the room");
        Set<BlockPos> claimed = new HashSet<>();
        for (PathStep step : minerPath.steps()) {
            for (BlockPos block : step.breaks()) {
                helper.assertTrue(WaveTeam.claimBreak(level, block, miner), "claim should succeed");
                claimed.add(block);
            }
        }

        // An archer can't dig, but plans to follow through the tunnel the miner has claimed.
        Mob archer = frozen(helper, ModEntities.ARCHER, START.south());
        MobPath follow = plan(helper, archer, PathAbilities.WALKER, ROOM_TARGET);
        WaveTeam.releaseAll(level, miner);
        helper.assertTrue(follow != null && follow.reachesGoal(), "archer should plan through the claimed tunnel");
        for (PathStep step : follow.steps()) {
            for (BlockPos block : step.breaks()) {
                helper.assertTrue(claimed.contains(block), "archer may only pass blocks a teammate claimed, not " + block);
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- regressions and config

    /**
     * Crash report 2026-10-25: a mob reached the end of a short path while the re-plan cooldown was still
     * running, and the next tick indexed past the end of the path (IndexOutOfBoundsException).
     */
    @GameTest(structure = ARENA)
    public void controllerSurvivesFinishingPathDuringCooldown(GameTestHelper helper) {
        Mob archer = frozen(helper, ModEntities.ARCHER, START);
        MobPathController controller = ((WaveMob) archer).pathController();
        PathGoal goal = PathGoal.near(helper.absolutePos(START.east(3)), 0, "test target");
        PathBudget.resetForTick();
        controller.tick(goal);
        helper.assertTrue(controller.path() != null, "should plan a short path");

        // Walk the mob along the path much faster than the 20-tick re-plan cooldown, then keep ticking past the end.
        for (int i = 0; i < 30; i++) {
            MobPath path = controller.path();
            if (path != null && controller.index() < path.steps().size()) {
                archer.snapTo(Vec3.atBottomCenterOf(path.steps().get(controller.index()).pos()));
                archer.setOnGround(true);
            }
            controller.tick(goal);
        }
        helper.succeed();
    }

    @GameTest(structure = ARENA)
    public void configAppliesToNewMobs(GameTestHelper helper) {
        // Settings are global; change them only around the spawns so concurrently running tests are unaffected.
        MonsterConfig.BOMBER_EXPLOSION_RADIUS.set("7");
        MonsterConfig.BOMBER_FUSE.set("50");
        MonsterConfig.MINER.speed.set("2");
        MonsterConfig.MINER.health.set("3");
        BomberCreeper bomber = frozen(helper, ModEntities.BOMBER, START);
        MinerZombie miner = frozen(helper, ModEntities.MINER, START.south(2));
        MonsterConfig.BOMBER_EXPLOSION_RADIUS.reset();
        MonsterConfig.BOMBER_FUSE.reset();
        MonsterConfig.MINER.speed.reset();
        MonsterConfig.MINER.health.reset();

        helper.assertValueEqual(((CreeperAccessor) bomber).monster$getExplosionRadius(), 7, "explosion radius");
        helper.assertValueEqual(((CreeperAccessor) bomber).monster$getMaxSwell(), 50, "fuse");
        helper.assertTrue(Math.abs(miner.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - 0.23 * 2) < 1e-6,
                "miner speed should double, got " + miner.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
        helper.assertValueEqual(miner.getMaxHealth(), 60.0F, "miner max health");
        helper.assertValueEqual(miner.getHealth(), 60.0F, "a fresh miner should spawn at full health");
        helper.assertTrue(MonsterConfig.MINER.speed.set("abc") != null && MonsterConfig.MINER.speed.set("999") != null,
                "invalid values must be rejected");
        helper.assertTrue(MonsterConfig.BUILDER_BLOCK.set("minecraft:not_a_block") != null, "unknown blocks must be rejected");
        helper.succeed();
    }

    // ---------------------------------------------------------------- live behaviour

    @GameTest(structure = ARENA, maxTicks = 1200)
    public void liveMinerDigsToTarget(GameTestHelper helper) {
        buildSealedRoom(helper);
        Villager villager = dummyVillager(helper, ROOM_TARGET);
        MinerZombie miner = helper.spawn(ModEntities.MINER, START);
        miner.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(miner.blockPosition()),
                EntitySpawnReason.COMMAND, null);
        miner.setTarget(villager);
        passWhen(helper, "liveMinerDigsToTarget", () -> helper.assertTrue(isHurt(villager), "miner has not reached the villager yet: " + state(helper, miner)));
    }

    @GameTest(structure = ARENA, maxTicks = 1200)
    public void liveBuilderClimbsToTarget(GameTestHelper helper) {
        buildPlatform(helper);
        Villager villager = dummyVillager(helper, PLATFORM_TARGET);
        BuilderZombie builder = helper.spawn(ModEntities.BUILDER, START);
        builder.setTarget(villager);
        passWhen(helper, "liveBuilderClimbsToTarget", () -> helper.assertTrue(isHurt(villager), "builder has not reached the villager yet"));
    }

    @GameTest(structure = ARENA, maxTicks = 600)
    public void liveBomberBreachesRoom(GameTestHelper helper) {
        buildSealedRoom(helper);
        Villager villager = dummyVillager(helper, ROOM_TARGET);
        BomberCreeper bomber = helper.spawn(ModEntities.BOMBER, START);
        bomber.setTarget(villager);
        passWhen(helper, "liveBomberBreachesRoom", () -> {
            helper.assertTrue(!bomber.isAlive(), "bomber has not detonated yet");
            helper.assertTrue(roomBreached(helper) || isHurt(villager), "explosion should open the room");
        });
    }

    @GameTest(structure = ARENA, maxTicks = 1200)
    public void liveArcherShootsAroundWall(GameTestHelper helper) {
        buildWallWithGap(helper);
        Villager villager = dummyVillager(helper, BEHIND_WALL_TARGET);
        ArcherSkeleton archer = helper.spawn(ModEntities.ARCHER, new BlockPos(3, 1, 3));
        archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        archer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        archer.setTarget(villager);
        passWhen(helper, "liveArcherShootsAroundWall", () -> helper.assertTrue(isHurt(villager), "archer has not shot the villager yet"));
    }

    /**
     * Runs in its own batch: once the room is dug open, mobs in neighbouring test arenas would (correctly)
     * spot this player and abandon their own targets for it.
     */
    @GameTest(structure = ARENA, maxTicks = 1200, environment = "monster-test:isolated")
    public void liveMinerHuntsHiddenPlayer(GameTestHelper helper) {
        buildSealedRoom(helper);
        ServerPlayer player = survivalPlayer(helper, ROOM_TARGET);
        MinerZombie miner = helper.spawn(ModEntities.MINER, START);
        passWhen(helper, "liveMinerHuntsHiddenPlayer", () -> {
            helper.assertTrue(miner.getTarget() == player, "miner should pick the walled-in player it can hear"
                    + " (target=" + miner.getTarget() + ", player at " + player.blockPosition() + " in " + player.level().dimension().identifier()
                    + ", distance=" + String.format("%.1f", miner.distanceTo(player))
                    + ", inLevelPlayers=" + helper.getLevel().players().contains(player)
                    + ", alive=" + player.isAlive() + ", gameMode=" + player.gameMode() + ", seenAsEnemy=" + player.canBeSeenAsEnemy() + ")");
            helper.assertTrue(isHurt(player), "miner has not dug through to the player yet");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // ---------------------------------------------------------------- helpers

    /** Like succeedWhen, but logs the tick the test passed on so the result is visible in the log. */
    private static void passWhen(GameTestHelper helper, String name, Runnable asserter) {
        helper.succeedWhen(() -> {
            asserter.run();
            LOGGER.info("{} passed on tick {}", name, helper.getTick());
        });
    }

    /** Where a live mob is and what its pathing is doing, for failure messages. */
    private static String state(GameTestHelper helper, Mob mob) {
        MobPathController controller = ((WaveMob) mob).pathController();
        MobPath path = controller.path();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos at = mob.blockPosition().subtract(origin);
        return "at " + at.toShortString() + ", alive=" + mob.isAlive() + ", target=" + mob.getTarget()
                + ", status=" + controller.status()
                + (path == null ? ", no path" : ", path " + controller.index() + "/" + (path.steps().size() - 1)
                + (path.reachesGoal() ? " complete" : " partial") + " breaks=" + path.breakCount());
    }

    /**
     * Plans without the search time limit: planner tests check what the search finds, and the first searches on a
     * cold JVM can otherwise hit the limit and return a partial path.
     */
    private static MobPath plan(GameTestHelper helper, Mob mob, PathAbilities abilities, BlockPos relativeTarget) {
        PathBudget.resetForTick();
        PathGoal goal = PathGoal.near(helper.absolutePos(relativeTarget), 1, "test target");
        MonsterConfig.SEARCH_TIME_LIMIT.set("1000");
        try {
            return MobPathfinder.find(helper.getLevel(), mob, abilities, mob.blockPosition(), goal);
        } finally {
            MonsterConfig.SEARCH_TIME_LIMIT.reset();
        }
    }

    private static <T extends Mob> T frozen(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
        T mob = helper.spawn(type, pos);
        mob.setNoAi(true);
        return mob;
    }

    private static Villager dummyVillager(GameTestHelper helper, BlockPos pos) {
        Villager villager = helper.spawn(EntityTypes.VILLAGER, pos);
        villager.setNoAi(true);
        return villager;
    }

    private static boolean isHurt(LivingEntity entity) {
        return !entity.isAlive() || entity.getHealth() < entity.getMaxHealth();
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-survivor"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // The test server defaults to creative; switch properly so abilities (invulnerability) follow.
        player.setGameMode(GameType.SURVIVAL);
        // Teleport through the connection; a plain position change is reset while the (absent) client hasn't confirmed.
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(pos));
        player.connection.teleport(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
    }

    private static List<BlockPos> roomShell() {
        List<BlockPos> shell = new ArrayList<>();
        for (int x = 10; x <= 14; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 6; z <= 10; z++) {
                    boolean inside = x > 10 && x < 14 && z > 6 && z < 10 && y < 3;
                    if (!inside) {
                        shell.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return shell;
    }

    private static void buildSealedRoom(GameTestHelper helper) {
        for (BlockPos pos : roomShell()) {
            helper.setBlock(pos, Blocks.STONE);
        }
    }

    private static boolean roomBreached(GameTestHelper helper) {
        for (BlockPos pos : roomShell()) {
            if (helper.getLevel().getBlockState(helper.absolutePos(pos)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static void buildPlatform(GameTestHelper helper) {
        fill(helper, 12, 1, 8, 12, 3, 8);
        fill(helper, 11, 4, 7, 13, 4, 9);
    }

    private static void buildWallWithGap(GameTestHelper helper) {
        fill(helper, 8, 1, 0, 8, 3, 14);
    }
}
