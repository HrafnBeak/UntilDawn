package org.kennyerf.untildawn.test;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.entity.EntityPathRequest;
import baritone.api.pathing.entity.EntityPathResult;
import baritone.api.pathing.entity.IEntityPathRenderer;
import baritone.api.pathing.goals.GoalBlock;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.kennyerf.untildawn.baritone.BaritonePrediction;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.entity.SprinterZombie;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.pathing.MobPath;
import org.kennyerf.untildawn.pathing.MoveType;
import org.kennyerf.untildawn.pathing.PathGoal;
import org.kennyerf.untildawn.game.WaveManager;
import org.kennyerf.untildawn.registry.ModBlocks;
import org.kennyerf.untildawn.registry.ModEntities;
import org.kennyerf.untildawn.squad.Squads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Baritone's predictions for entities, end to end. Baritone only plans inside the client, so these run in a real
 * singleplayer world: run with {@code gradlew runClientGameTest} (opens a game window for a minute or so).
 * <p>
 * Everything is built in the sky above the superflat test world so terrain can't interfere: a stone floor one block
 * thick with nothing under it but a deadly drop, so the only ways anywhere are the ones each test leaves open.
 */
public class UntilDawnClientGameTests implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("untildawn-client-test");
    private static final int Y = 100;
    private static final int PLAN_TIMEOUT_TICKS = 20 * 30;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();
            TestServerContext server = singleplayer.getServer();
            BlockPos spawn = server.computeOnServer(s -> singleplayer.getConnection().getServerPlayer().blockPosition());
            BlockPos origin = new BlockPos(spawn.getX(), Y, spawn.getZ());

            server.runOnServer(s -> {
                fill(s.overworld(), origin.offset(-10, -1, -6), origin.offset(10, 9, 6), Blocks.AIR.defaultBlockState());
                fill(s.overworld(), origin.offset(-10, -1, -6), origin.offset(10, -1, 6), Blocks.STONE.defaultBlockState());
            });

            walksAcrossFloor(server, origin);
            pillarsWithBlocks(server, origin);

            // A wall too high to climb across the whole floor: through it is the only way to the other side.
            server.runOnServer(s -> fill(s.overworld(), origin.offset(0, 0, -6), origin.offset(0, 8, 6), Blocks.STONE.defaultBlockState()));
            tunnelsWithTools(server, origin);
            untildawnPredictionAndCommand(context, server, origin);
            sprinterJumpsGap(context, server, origin.offset(0, 0, 20));

            sprinterJumpCourse(server, new BlockPos(spawn.getX(), 140, spawn.getZ()));
            sprinterReachesCore(server, spawn);
            builderMakesRoomForMiner(server, new BlockPos(spawn.getX() + 120, Y, spawn.getZ()));
            squadClimbsToSkyCore(server, "a tower with an overhang", new BlockPos(spawn.getX() + 40, Y, spawn.getZ()), true);
            squadClimbsToSkyCore(server, "a floating island", new BlockPos(spawn.getX() + 80, Y, spawn.getZ()), false);
            playerBaritoneStillPaths(context, singleplayer, spawn);
        }
        LOGGER.info("All Baritone client game tests passed");
    }

    private static void walksAcrossFloor(TestServerContext server, BlockPos origin) {
        EntityPathResult walk = plan(server, origin.offset(-8, 0, 0), origin.offset(8, 0, 0), List.of(), true);
        check(walk.reachesGoal(), "should walk across an open floor: " + walk.type());
        check(walk.breakCount() == 0 && walk.placeCount() == 0, "walking needs no work, got " + walk.breakCount() + " breaks, " + walk.placeCount() + " places");
        LOGGER.info("walk: {} moves, cost {}", walk.steps().size(), walk.cost());
    }

    private static void pillarsWithBlocks(TestServerContext server, BlockPos origin) {
        BlockPos from = origin.offset(-8, 0, -4);
        BlockPos up = from.above(4);

        EntityPathResult enough = plan(server, from, up, List.of(new ItemStack(Items.COBBLESTONE, 64)), true);
        check(enough.reachesGoal(), "should pillar up with 64 cobblestone: " + enough.type());
        check(enough.placeCount() >= 4, "pillaring 4 up places at least 4 blocks, got " + enough.placeCount());
        check(enough.steps().stream().anyMatch(step -> step.move() == EntityPathResult.Move.PILLAR), "route should pillar");
        check(enough.inventory().blocksCarried() == 64 && !enough.inventory().runsOutOfBlocks(), "64 blocks are plenty: " + enough.inventory());

        EntityPathResult none = plan(server, from, up, List.of(new ItemStack(Items.IRON_PICKAXE)), true);
        check(!none.reachesGoal(), "can't get up into the air without blocks: " + none.type());

        EntityPathResult few = plan(server, from, up, List.of(new ItemStack(Items.COBBLESTONE, 2)), true);
        check(few.reachesGoal(), "with some blocks Baritone plans the pillar anyway: " + few.type());
        check(few.inventory().runsOutOfBlocks(), "2 blocks can't make a 4 high pillar: " + few.inventory());
        LOGGER.info("pillar: {} places; with 2 blocks runs out at move {}", enough.placeCount(), few.inventory().runsOutOfBlocksAt() + 1);
    }

    private static void tunnelsWithTools(TestServerContext server, BlockPos origin) {
        BlockPos from = origin.offset(-8, 0, 0);
        BlockPos to = origin.offset(8, 0, 0);

        EntityPathResult pickaxe = plan(server, from, to, List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIRT, 3)), true);
        check(pickaxe.reachesGoal(), "an iron pickaxe digs through the wall: " + pickaxe.type());
        check(pickaxe.breakCount() >= 2, "a 2 high hole needs at least 2 breaks, got " + pickaxe.breakCount());
        pickaxe.steps().forEach(step -> step.breaks().forEach(mined ->
                check(mined.tool().is(Items.IRON_PICKAXE), "stone should be mined with the pickaxe, not " + mined.tool())));
        check(pickaxe.inventory().blocksPickedUp() >= 1, "mined stone drops cobblestone: " + pickaxe.inventory());

        EntityPathResult hands = plan(server, from, to, List.of(), true);
        check(hands.reachesGoal(), "bare hands can still dig stone, slowly: " + hands.type());
        check(hands.cost() > pickaxe.cost(), "digging by hand should cost more: " + hands.cost() + " vs " + pickaxe.cost());
        hands.steps().forEach(step -> step.breaks().forEach(mined -> check(mined.tool().isEmpty(), "nothing to mine with but hands")));
        check(hands.inventory().blocksPickedUp() == 0, "stone mined by hand drops nothing: " + hands.inventory());

        EntityPathResult noBreaking = plan(server, from, to, List.of(new ItemStack(Items.IRON_PICKAXE)), false);
        check(!noBreaking.reachesGoal(), "with breaking off the wall is impassable: " + noBreaking.type());

        ItemStack worn = new ItemStack(Items.WOODEN_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 1);
        EntityPathResult wornOut = plan(server, from, to, List.of(worn), true);
        check(wornOut.reachesGoal(), "a worn pickaxe still plans: " + wornOut.type());
        check(!wornOut.inventory().wornOutTools().isEmpty(), "a pickaxe with one use left breaks in the tunnel: " + wornOut.inventory());
        LOGGER.info("tunnel: pickaxe cost {} vs hands {}; {} blocks picked up", pickaxe.cost(), hands.cost(), pickaxe.inventory().blocksPickedUp());
    }

    private static void untildawnPredictionAndCommand(ClientGameTestContext context, TestServerContext server, BlockPos origin) {
        check(BaritonePrediction.isAvailable(), "Baritone should be available in the client");
        CompletableFuture<BaritonePrediction.Prediction> future = server.computeOnServer(s -> BaritonePrediction.predict(
                s.overworld(), origin.offset(-8, 0, 0), origin.offset(8, 0, 0), List.of(new ItemStack(Items.IRON_PICKAXE))));
        server.waitFor(s -> future.isDone(), PLAN_TIMEOUT_TICKS);
        BaritonePrediction.Prediction prediction = future.join();
        check(prediction.path() != null && prediction.path().reachesGoal() && prediction.path().breakCount() >= 2,
                "UntilDawn's prediction should carry Baritone's path: " + prediction.path());
        check(prediction.summary().stream().anyMatch(line -> line.getString().startsWith("Breaks")), "summary should list the breaks: " + prediction.summary());
        prediction.summary().forEach(line -> LOGGER.info("summary: {}", line.getString()));

        // From the server console: no player to draw for, just the summary in the log.
        server.runCommand(String.format("untildawn path predict %d %d %d %d %d %d iron_pickaxe cobblestone*16",
                origin.getX() - 8, origin.getY(), origin.getZ(), origin.getX() + 8, origin.getY(), origin.getZ()));
        context.waitTicks(40);
    }

    /**
     * Two platforms with a 2 block gap between them and a deadly drop all around: the only way across is a jump.
     * Baritone's route for a Sprinter must jump it, and a live Sprinter must make the jump to reach a cow.
     */
    private static void sprinterJumpsGap(ClientGameTestContext context, TestServerContext server, BlockPos origin) {
        server.runCommand("difficulty normal");
        server.runOnServer(s -> {
            fill(s.overworld(), origin.offset(-10, -1, -4), origin.offset(10, 4, 4), Blocks.AIR.defaultBlockState());
            fill(s.overworld(), origin.offset(-8, -1, -2), origin.offset(-2, -1, 2), Blocks.STONE.defaultBlockState());
            fill(s.overworld(), origin.offset(1, -1, -2), origin.offset(8, -1, 2), Blocks.STONE.defaultBlockState());
        });
        BlockPos from = origin.offset(-6, 0, 0);
        BlockPos to = origin.offset(6, 0, 0);

        CompletableFuture<MobPath> plan = server.computeOnServer(s ->
                BaritonePrediction.planSprinterPath(s.overworld(), from, PathGoal.near(to, 1, "the far platform")));
        server.waitFor(s -> plan.isDone(), PLAN_TIMEOUT_TICKS);
        MobPath path = plan.join();
        check(path != null && path.reachesGoal(), "Baritone should find a way across for a Sprinter: " + path);
        check(path.steps().stream().anyMatch(step -> step.type() == MoveType.PARKOUR), "the way across is a jump: " + path.steps());
        check(path.breakCount() == 0 && path.placeCount() == 0, "Sprinters don't dig or build: " + path);

        SprinterZombie sprinter = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            // Persistent: the player stands on the ground far below, beyond the distance monsters despawn at.
            Cow cow = EntityTypes.COW.spawn(level, to, EntitySpawnReason.COMMAND);
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            SprinterZombie mob = ModEntities.SPRINTER.spawn(level, from, EntitySpawnReason.COMMAND);
            mob.setPersistenceRequired();
            mob.setTarget(cow);
            return mob;
        });
        // The player watches it: its path should reach Baritone's renderer on the client. /untildawn is for operators.
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
            s.getPlayerList().op(player.nameAndId());
        });
        context.waitTicks(5);
        context.runOnClient(mc -> mc.player.connection.sendCommand("untildawn path show @e[type=untildawn:sprinter]"));
        IEntityPathRenderer overlay = BaritoneAPI.getProvider().getEntityPathRenderer();
        boolean[] drawn = {false};
        int edgeX = origin.getX() + 1;
        int[] ticks = {0};
        server.waitFor(s -> {
            drawn[0] |= !overlay.getPaths().isEmpty();
            if (++ticks[0] % 20 == 0) {
                MobPathController controller = sprinter.pathController();
                MobPath current = controller.path();
                LOGGER.info("sprinter at {} ({}), alive={}, onGround={}, target={}, status={}, step {}/{}{}",
                        sprinter.position(), sprinter.blockPosition(), sprinter.isAlive(), sprinter.onGround(), sprinter.getTarget(),
                        controller.status(), controller.index(), current == null ? "-" : current.steps().size() - 1,
                        current == null ? "" : " " + current.steps().stream().map(step -> step.type() + "@" + step.pos().toShortString()).toList());
            }
            return sprinter.isAlive() && sprinter.onGround() && sprinter.getX() >= edgeX && sprinter.getY() >= origin.getY() - 0.1;
        }, PLAN_TIMEOUT_TICKS);
        LOGGER.info("sprinter jumped the gap: {} moves, now at {}", path.steps().size() - 1, sprinter.blockPosition());
        check(drawn[0], "/untildawn path show should have handed the sprinter's path to Baritone's renderer");
        context.runOnClient(mc -> mc.player.connection.sendCommand("untildawn path hide"));
        context.waitFor(mc -> overlay.getPaths().isEmpty(), 100);
        server.runOnServer(s -> sprinter.discard());
    }

    /**
     * One lane of the jump course: a gap of {@code gap} blocks, landing {@code rise} blocks higher, on {@code floor}.
     *
     * @param bend         the run-up comes in from the side
     * @param disturb      the target keeps moving (the Sprinter keeps re-planning), and its path is thrown away mid-air
     * @param neo          a neo instead of a gap: a wall {@code gap - 1} thick right in front of a single take-off block,
     *                     covering one side, and a single landing block behind it, so the only way on is around the
     *                     wall's open end
     */
    private record Lane(String name, int gap, int rise, BlockState floor, boolean bend, boolean disturb, boolean neo) {
        Lane(String name, int gap, int rise, BlockState floor, boolean bend, boolean disturb) {
            this(name, gap, rise, floor, bend, disturb, false);
        }
    }

    /**
     * Lanes of gaps over a deadly drop, a Sprinter on one side of each and a cow on the other: every Sprinter must jump
     * across. Leaves stand in for tree tops; the bent lane makes the Sprinter turn just before it jumps.
     */
    private static void sprinterJumpCourse(TestServerContext server, BlockPos origin) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        List<Lane> lanes = List.of(
                new Lane("1 gap", 1, 0, Blocks.STONE.defaultBlockState(), false, false),
                new Lane("2 gap", 2, 0, Blocks.STONE.defaultBlockState(), false, false),
                new Lane("3 gap", 3, 0, Blocks.STONE.defaultBlockState(), false, false),
                new Lane("1 gap, 1 up", 1, 1, Blocks.STONE.defaultBlockState(), false, false),
                new Lane("2 gap, 1 up", 2, 1, Blocks.STONE.defaultBlockState(), false, false),
                new Lane("2 gap on leaves", 2, 0, leaves, false, false),
                new Lane("3 gap after a bend", 3, 0, Blocks.STONE.defaultBlockState(), true, false),
                new Lane("3 gap, target moving", 3, 0, Blocks.STONE.defaultBlockState(), false, true),
                new Lane("2 gap, 1 up, target moving", 2, 1, Blocks.STONE.defaultBlockState(), false, true),
                new Lane("neo around a wall", 2, 0, Blocks.STONE.defaultBlockState(), false, false, true),
                new Lane("neo around a thick wall", 3, 0, Blocks.STONE.defaultBlockState(), false, false, true));
        List<Cow> cows = new java.util.ArrayList<>();
        List<SprinterZombie> sprinters = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            fill(level, origin.offset(-12, -2, -20), origin.offset(16, 4, 50), Blocks.AIR.defaultBlockState());
            List<SprinterZombie> mobs = new java.util.ArrayList<>();
            for (int i = 0; i < lanes.size(); i++) {
                Lane lane = lanes.get(i);
                BlockPos row = origin.offset(0, 0, -18 + i * 6);
                fill(level, row.offset(-9, -1, -1), row.offset(-1, -1, 1), lane.floor());
                if (lane.bend()) {
                    // The run-up comes in from the side: the Sprinter starts off the lane and turns onto it.
                    fill(level, row.offset(-9, -1, -1), row.offset(-3, -1, 1), Blocks.AIR.defaultBlockState());
                    fill(level, row.offset(-3, -1, -1), row.offset(-1, -1, 1), lane.floor());
                    fill(level, row.offset(-3, -1, 2), row.offset(-3, -1, 4), lane.floor());
                }
                if (lane.neo()) {
                    BlockState wall = Blocks.STONE.defaultBlockState();
                    fill(level, row.offset(0, -1, 0), row.offset(0, -1, 0), lane.floor());
                    fill(level, row.offset(1, 0, -2), row.offset(lane.gap() - 1, 2, 0), wall);
                    fill(level, row.offset(lane.gap(), -1, 0), row.offset(lane.gap() + 1, -1, 0), lane.floor());
                    fill(level, row.offset(lane.gap() + 2, -1, -1), row.offset(lane.gap() + 8, -1, 1), lane.floor());
                } else {
                    fill(level, row.offset(lane.gap(), lane.rise() - 1, -1), row.offset(lane.gap() + 7, lane.rise() - 1, 1), lane.floor());
                }
                Cow cow = EntityTypes.COW.spawn(level, row.offset(lane.gap() + 5, lane.rise(), 0), EntitySpawnReason.COMMAND);
                cow.setNoAi(true);
                cow.setPersistenceRequired();
                cows.add(cow);
                BlockPos start = lane.bend() ? row.offset(-3, 0, 4) : row.offset(-7, 0, 0);
                SprinterZombie mob = ModEntities.SPRINTER.spawn(level, start, EntitySpawnReason.COMMAND);
                mob.setPersistenceRequired();
                mob.setTarget(cow);
                mobs.add(mob);
            }
            return mobs;
        });
        String[] results = new String[lanes.size()];
        boolean[] airborne = new boolean[lanes.size()];
        int[] ticks = {0};
        server.waitFor(s -> {
            ticks[0]++;
            boolean done = true;
            for (int i = 0; i < lanes.size(); i++) {
                if (results[i] != null) {
                    continue;
                }
                Lane lane = lanes.get(i);
                SprinterZombie mob = sprinters.get(i);
                if (lane.disturb()) {
                    // The target hops 4 blocks to and fro along the far platform: far enough to make the Sprinter re-plan.
                    if (ticks[0] % 6 == 0) {
                        Cow cow = cows.get(i);
                        cow.teleportTo(cow.getX() + ((ticks[0] / 6) % 2 == 0 ? 4 : -4), cow.getY(), cow.getZ());
                    }
                    if (!airborne[i] && !mob.onGround() && mob.getX() > origin.getX() - 1) {
                        airborne[i] = true;
                        mob.pathController().forceRepath();
                    }
                }
                if (mob.getY() < origin.getY() - 3) {
                    results[i] = "FELL (" + mob.pathController().status() + ", path " + moves(mob) + ")";
                    mob.discard();
                } else if (mob.onGround() && mob.getX() >= origin.getX() + lane.gap() && mob.getY() >= origin.getY() + lane.rise() - 0.1) {
                    results[i] = "made it in " + ticks[0] + " ticks";
                    mob.discard();
                } else {
                    done = false;
                }
            }
            return done || ticks[0] >= 20 * 20;
        }, 20 * 21);
        int made = 0;
        for (int i = 0; i < lanes.size(); i++) {
            String result = results[i] != null ? results[i]
                    : "STUCK (" + sprinters.get(i).pathController().status() + ", path " + moves(sprinters.get(i)) + ")";
            LOGGER.info("jump course, {}: {}", lanes.get(i).name(), result);
            made += result.startsWith("made") ? 1 : 0;
        }
        server.runOnServer(s -> sprinters.forEach(mob -> {
            if (!mob.isRemoved()) {
                mob.discard();
            }
        }));
        check(made == lanes.size(), "every Sprinter should make its jump, " + made + "/" + lanes.size() + " did");
    }

    /**
     * What a player does in a creative superflat world: place the Core on the ground and spawn a Sprinter nearby. Wave
     * mobs ignore creative players, so the Core is its goal, and it must get there.
     */
    private static void sprinterReachesCore(TestServerContext server, BlockPos spawn) {
        server.runCommand("gamemode creative @a");
        BlockPos core = spawn.offset(8, 0, 0);
        SprinterZombie sprinter = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            level.setBlockAndUpdate(core, ModBlocks.CORE.defaultBlockState());
            WaveManager.onCorePlaced(level, core, null);
            SprinterZombie mob = ModEntities.SPRINTER.spawn(level, spawn.offset(-7, 0, 3), EntitySpawnReason.SPAWN_ITEM_USE);
            mob.setPersistenceRequired();
            return mob;
        });
        int[] ticks = {0};
        server.waitFor(s -> {
            if (++ticks[0] % 20 == 0) {
                logSprinter(sprinter);
            }
            return WaveManager.isInCoreArea(sprinter);
        }, 20 * 20);
        LOGGER.info("sprinter reached the Core after {} ticks", ticks[0]);
        server.runOnServer(s -> sprinter.discard());
    }

    /**
     * A Core high above the floor, where only building gets anywhere: a Builder and three Swarmers, one squad, must all
     * get up to it. On a tower the Core sits on a platform that overhangs a one-block pillar (a wall for ladders, but one
     * that ends under the platform); on a floating island there is nothing under it at all. The Swarmers can't build, so
     * they need the Builder's way up, or to ask for one.
     */
    private static void squadClimbsToSkyCore(TestServerContext server, String name, BlockPos origin, boolean tower) {
        endCore(server);
        // The first ones up would wear the Core down, ending the game, before the rest got there.
        server.runOnServer(s -> UntilDawnConfig.CORE_DAMAGE_PER_MOB.set("0"));
        int height = 12;
        BlockPos core = origin.above(height);
        List<Mob> squad = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            fill(level, origin.offset(-14, -1, -10), origin.offset(10, height + 5, 10), Blocks.AIR.defaultBlockState());
            fill(level, origin.offset(-14, -1, -10), origin.offset(10, -1, 10), Blocks.STONE.defaultBlockState());
            if (tower) {
                fill(level, origin, origin.above(height - 2), Blocks.STONE.defaultBlockState());
                fill(level, core.offset(-1, -1, -1), core.offset(1, -1, 1), Blocks.STONE.defaultBlockState());
            } else {
                fill(level, core.offset(-2, -1, -2), core.offset(2, -1, 2), Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(core, ModBlocks.CORE.defaultBlockState());
            WaveManager.onCorePlaced(level, core, null);
            List<Mob> mobs = new java.util.ArrayList<>();
            mobs.add(ModEntities.BUILDER.spawn(level, origin.offset(-10, 0, 0), EntitySpawnReason.COMMAND));
            for (int i = 0; i < 3; i++) {
                mobs.add(ModEntities.SWARMER.spawn(level, origin.offset(-11, 0, i - 1), EntitySpawnReason.COMMAND));
            }
            mobs.forEach(Mob::setPersistenceRequired);
            Squads.form(level, mobs);
            return mobs;
        });
        String[] arrived = new String[squad.size()];
        double[] highest = new double[squad.size()];
        String[] fallingFrom = new String[squad.size()];
        int[] fallCount = {0};
        int[] ticks = {0};
        int limit = 20 * 120;
        server.waitFor(s -> {
            ticks[0]++;
            int inside = 0;
            for (int i = 0; i < squad.size(); i++) {
                Mob mob = squad.get(i);
                // Standing in the area, as the game counts it (a mob just under it, head in it, hurts the Core too); not just jumping into it.
                if (arrived[i] == null && mob.isAlive() && WaveManager.isInCoreArea(mob) && mob.onGround()) {
                    arrived[i] = "arrived after " + ticks[0] + " ticks";
                }
                inside += arrived[i] != null ? 1 : 0;
                // Falls of more than 3 blocks (none are planned), with what the mob was doing when it left its footing.
                MobPathController controller = ((WaveMob) mob).pathController();
                if (mob.onGround() || mob.onClimbable() || mob.isInWater()) {
                    if (fallingFrom[i] != null && highest[i] - mob.getY() > 3.0) {
                        fallCount[0]++;
                        LOGGER.info("sky core ({}), {} FELL {} blocks to {}, {}", name, mob.getType().getDescription().getString(),
                                String.format("%.1f", highest[i] - mob.getY()), mob.blockPosition().subtract(origin).toShortString(), fallingFrom[i]);
                    }
                    fallingFrom[i] = null;
                    highest[i] = mob.getY();
                } else if (fallingFrom[i] == null || mob.getY() > highest[i]) {
                    MobPath path = controller.path();
                    int k = controller.index();
                    highest[i] = mob.getY();
                    fallingFrom[i] = "left " + mob.blockPosition().subtract(origin).toShortString() + " " + controller.status()
                            + (path == null || k >= path.steps().size() ? "" : ", step " + path.steps().get(k).type() + "@"
                            + path.steps().get(k).pos().subtract(origin).toShortString() + " from " + path.steps().get(k - 1).type() + "@"
                            + path.steps().get(k - 1).pos().subtract(origin).toShortString() + " places " + path.steps().get(k).places().stream()
                            .map(p -> p.subtract(origin).toShortString()).toList() + (controller.hasJob() ? " (job)" : ""));
                }
            }
            if (ticks[0] % 100 == 0) {
                for (Mob mob : squad) {
                    MobPathController controller = ((WaveMob) mob).pathController();
                    MobPath path = controller.path();
                    int k = controller.index();
                    String step = path == null || k >= path.steps().size() ? "" : ", next " + path.steps().get(k).type() + "@"
                            + path.steps().get(k).pos().subtract(origin).toShortString() + " places " + path.steps().get(k).places().stream()
                            .map(p -> p.subtract(origin).toShortString()).toList();
                    LOGGER.info("sky core ({}), {} at {}{}: {}, step {}/{}{}{}{}", name, mob.getType().getDescription().getString(),
                            mob.blockPosition().subtract(origin).toShortString(), mob.isAlive() ? "" : " (dead)", controller.status(),
                            k, path == null ? "-" : path.steps().size() - 1,
                            path == null ? "" : (path.reachesGoal() ? " complete" : " partial") + ", " + path.nodesExplored() + " nodes, "
                                    + path.placeCount() + " places", step, controller.hasJob() ? " (job)" : "");
                }
            }
            return inside == squad.size() || ticks[0] >= limit;
        }, limit + 20);
        int[] built = server.computeOnServer(s -> {
            int planks = 0, ladders = 0;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-14, 0, -10), origin.offset(10, height + 4, 10))) {
                BlockState state = s.overworld().getBlockState(pos);
                planks += state.is(ModBlocks.UNSTABLE_PLANKS) ? 1 : 0;
                ladders += state.is(ModBlocks.UNSTABLE_LADDER) ? 1 : 0;
            }
            return new int[]{planks, ladders};
        });
        int made = 0;
        for (int i = 0; i < squad.size(); i++) {
            Mob mob = squad.get(i);
            String result = arrived[i] != null ? arrived[i]
                    : "STUCK at " + mob.blockPosition().subtract(origin).toShortString() + (mob.isAlive() ? "" : " (dead)")
                    + " (" + ((WaveMob) mob).pathController().status() + ")";
            LOGGER.info("sky core ({}), {}: {}", name, mob.getType().getDescription().getString(), result);
            made += arrived[i] != null ? 1 : 0;
        }
        LOGGER.info("sky core ({}): {}/{} up in {} ticks, {} planks and {} ladders built, {} falls", name, made, squad.size(), ticks[0],
                built[0], built[1], fallCount[0]);
        server.runOnServer(s -> {
            squad.forEach(Entity::discard);
            UntilDawnConfig.CORE_DAMAGE_PER_MOB.reset();
        });
        check(arrived[0] != null, "the Builder should get up to the Core on " + name);
        check(made == squad.size(), "the whole squad should get up to the Core on " + name + ", " + made + "/" + squad.size() + " did");
    }

    /**
     * A Core on a wide stone slab above the floor: the Builder ladders up under it, then needs the slab broken to get
     * through. It calls for the Miner, stepping aside if there is a floor to step onto, and the Miner digs the way through
     * from wherever it can reach it. Logged as data: who got up, falls, what each did and what was built (the camera looks
     * at the slab so the run can be watched).
     */
    private static void builderMakesRoomForMiner(TestServerContext server, BlockPos origin) {
        endCore(server);
        server.runOnServer(s -> UntilDawnConfig.CORE_DAMAGE_PER_MOB.set("0"));
        // Out here nobody is near, so the chunks have to be kept loaded or the mobs don't tick; the player is put back
        // afterwards, as the tests after this one need the chunks around it ticking.
        net.minecraft.world.phys.Vec3 back = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).position());
        String area = (origin.getX() - 16) + " " + (origin.getZ() - 12) + " " + (origin.getX() + 12) + " " + (origin.getZ() + 12);
        server.runCommand("forceload add " + area);
        server.runCommand("tp @p " + (origin.getX() - 12) + " " + (origin.getY() + 1) + " " + (origin.getZ() + 0.5));
        server.waitFor(s -> s.overworld().hasChunkAt(origin), 200);
        int height = 10;
        BlockPos core = origin.above(height);
        List<Mob> squad = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            fill(level, origin.offset(-14, -1, -10), origin.offset(10, height + 5, 10), Blocks.AIR.defaultBlockState());
            fill(level, origin.offset(-14, -1, -10), origin.offset(10, -1, 10), Blocks.STONE.defaultBlockState());
            fill(level, core.offset(-6, -1, -6), core.offset(6, -1, 6), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(core, ModBlocks.CORE.defaultBlockState());
            WaveManager.onCorePlaced(level, core, null);
            List<Mob> mobs = new java.util.ArrayList<>();
            mobs.add(ModEntities.BUILDER.spawn(level, origin.offset(-8, 0, 0), EntitySpawnReason.COMMAND));
            mobs.add(ModEntities.MINER.spawn(level, origin.offset(-9, 0, 1), EntitySpawnReason.COMMAND));
            mobs.forEach(Mob::setPersistenceRequired);
            Squads.form(level, mobs);
            return mobs;
        });
        // A free camera beside the pillar, looking at where the Builder climbs.
        server.runCommand("gamemode spectator @p");
        server.runCommand("tp @p " + (origin.getX() - 7) + " " + (origin.getY() + height - 4) + " " + (origin.getZ() + 3.5)
                + " facing " + (origin.getX() - 1) + " " + (origin.getY() + height - 3) + " " + origin.getZ());
        boolean[] arrived = new boolean[squad.size()];
        String[] lastStatus = new String[squad.size()];
        double[] highest = new double[squad.size()];
        boolean[] falling = new boolean[squad.size()];
        int[] falls = {0};
        int[] ticks = {0};
        int limit = 20 * 90;
        server.waitFor(s -> {
            ticks[0]++;
            int inside = 0;
            for (int i = 0; i < squad.size(); i++) {
                Mob mob = squad.get(i);
                String name = mob.getType().getDescription().getString();
                arrived[i] |= mob.isAlive() && WaveManager.isInCoreArea(mob) && mob.onGround();
                inside += arrived[i] ? 1 : 0;
                String status = ((WaveMob) mob).pathController().status();
                if (!status.equals(lastStatus[i])) {
                    lastStatus[i] = status;
                    LOGGER.info("make room t={}: {} at {}: {}", ticks[0], name, mob.blockPosition().subtract(origin).toShortString(), status);
                }
                if (mob.onGround() || mob.onClimbable()) {
                    if (falling[i] && highest[i] - mob.getY() > 1.5) {
                        falls[0]++;
                        LOGGER.info("make room t={}: {} FELL {} blocks to {}", ticks[0], name, String.format("%.1f", highest[i] - mob.getY()),
                                mob.blockPosition().subtract(origin).toShortString());
                    }
                    falling[i] = false;
                    highest[i] = mob.getY();
                } else if (!falling[i] || mob.getY() > highest[i]) {
                    falling[i] = true;
                    highest[i] = Math.max(highest[i], mob.getY());
                }
            }
            return inside == squad.size() || ticks[0] >= limit;
        }, limit + 20);
        server.runCommand("gamemode creative @p");
        server.runCommand("tp @p " + back.x + " " + back.y + " " + back.z);
        int[] built = server.computeOnServer(s -> {
            int planks = 0, ladders = 0;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-14, 0, -10), origin.offset(10, height + 4, 10))) {
                BlockState state = s.overworld().getBlockState(pos);
                planks += state.is(ModBlocks.UNSTABLE_PLANKS) ? 1 : 0;
                ladders += state.is(ModBlocks.UNSTABLE_LADDER) ? 1 : 0;
            }
            return new int[]{planks, ladders};
        });
        int made = 0;
        for (boolean up : arrived) {
            made += up ? 1 : 0;
        }
        LOGGER.info("make room: {}/{} up in {} ticks, {} falls, {} planks and {} ladders left standing", made, squad.size(), ticks[0],
                falls[0], built[0], built[1]);
        server.runOnServer(s -> {
            squad.forEach(Entity::discard);
            UntilDawnConfig.CORE_DAMAGE_PER_MOB.reset();
        });
        server.runCommand("forceload remove " + area);
        check(made == squad.size(), "the whole squad should get up to the Core, " + made + "/" + squad.size() + " did");
    }

    /** Takes away the Core, if there is one, and waits for the game to end so another can be placed. */
    private static void endCore(TestServerContext server) {
        server.runOnServer(s -> {
            BlockPos core = WaveManager.corePos(s.overworld());
            if (core != null) {
                s.overworld().removeBlock(core, false);
            }
        });
        server.waitFor(s -> WaveManager.corePos(s.overworld()) == null, 100);
    }

    private static String moves(SprinterZombie sprinter) {
        MobPath path = sprinter.pathController().path();
        return path == null ? "none" : path.steps().stream().map(step -> step.type() + "@" + step.pos().toShortString()).toList().toString();
    }

    private static void logSprinter(SprinterZombie sprinter) {
        MobPathController controller = sprinter.pathController();
        MobPath current = controller.path();
        LOGGER.info("sprinter at {} ({}), alive={}, onGround={}, target={}, status={}, step {}/{}{}",
                sprinter.position(), sprinter.blockPosition(), sprinter.isAlive(), sprinter.onGround(), sprinter.getTarget(),
                controller.status(), controller.index(), current == null ? "-" : current.steps().size() - 1,
                current == null ? "" : " " + current.goalDescription() + " " + current.steps().stream().map(step -> step.type() + "@" + step.pos().toShortString()).toList());
    }

    /** The refactored CalculationContext, ToolSet and BlockStateInterface still serve the player's own Baritone. */
    private static void playerBaritoneStillPaths(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos spawn) {
        BlockPos target = context.computeOnClient(mc -> mc.player.blockPosition().offset(12, 0, 5));
        context.runOnClient(mc -> BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target)));
        context.waitFor(mc -> mc.player.blockPosition().equals(target), 20 * 60);
        LOGGER.info("player's Baritone walked from {} to {}", spawn, target);
    }

    private static EntityPathResult plan(TestServerContext server, BlockPos from, BlockPos to, List<ItemStack> items, boolean allowBreak) {
        CompletableFuture<EntityPathResult> future = server.computeOnServer(s -> BaritoneAPI.getProvider().getEntityPathPlanner().plan(
                EntityPathRequest.builder(s.overworld(), from, new GoalBlock(to), s)
                        .items(items)
                        .allowBreak(allowBreak)
                        .timeouts(2000, 4000)
                        .build()));
        server.waitFor(s -> future.isDone(), PLAN_TIMEOUT_TICKS);
        return future.join();
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
