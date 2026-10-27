package org.kennyerf.untildawn.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.kennyerf.untildawn.block.BarricadeBlock;
import org.kennyerf.untildawn.block.CrossbowSentryBlock;
import org.kennyerf.untildawn.block.SpikeTrapBlock;
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
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.kennyerf.untildawn.item.HeavyCrossbowItem;
import org.kennyerf.untildawn.item.ScopedHeavyCrossbowItem;
import org.kennyerf.untildawn.recipe.ScopeRecipes;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantment;
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

    /** The mod's datapack tech tree loads, with tags in its craft costs resolved and the Fortification tree as its own root. */
    @GameTest(structure = ARENA)
    public void techTreeLoads(GameTestHelper helper) {
        List<TechNode> nodes = TechTree.all();
        helper.assertValueEqual(nodes.size(), 19, "tech tree items");
        TechNode woodenPike = TechTree.get(UntilDawn.id("wooden_pike"));
        helper.assertTrue(woodenPike != null && woodenPike.parent().isEmpty() && woodenPike.craftCost().getFirst().ingredient().test(new ItemStack(Items.WOODEN_SWORD))
                && woodenPike.craftCost().get(1).ingredient().test(new ItemStack(Items.OAK_PLANKS)), "the Pike tree starts at the Wooden Pike, made from a wooden sword");
        helper.assertValueEqual(TechTree.get(UntilDawn.id("diamond_pike")).parent().orElse(null), UntilDawn.id("iron_pike"), "diamond pike parent");
        helper.assertTrue(TechTree.get(UntilDawn.id("netherite_pike")) == null, "the Netherite Pike is smithed, not researched");
        helper.assertTrue(TechTree.get(UntilDawn.id("grappling_gun")).parent().isEmpty(), "the Grappling Gun has no prerequisite");
        TechNode arrow = TechTree.get(UntilDawn.id("arrow"));
        helper.assertTrue(arrow != null && arrow.createResult().is(Items.ARROW) && arrow.createResult().getCount() == 16 && arrow.parent().isEmpty()
                && arrow.researchCost() == 20, "arrows are a top, 16 at a time, for 20 Scrap");
        TechNode.Cost sticks = arrow.craftCost().getFirst();
        helper.assertTrue(sticks.ingredient().test(new ItemStack(Items.STICK)) && sticks.count() == 16, "16 sticks");
        helper.assertTrue(arrow.craftCost().get(2).ingredient().test(new ItemStack(ModItems.SCRAP)) && arrow.craftCost().get(2).count() == 3, "3 Scrap");
        helper.assertValueEqual(TechTree.get(UntilDawn.id("repeater_crossbow")).parent().orElse(null), UntilDawn.id("arrow"), "repeater parent");
        helper.assertValueEqual(TechTree.get(UntilDawn.id("heavy_crossbow")).parent().orElse(null), UntilDawn.id("arrow"), "heavy crossbow parent");
        helper.assertValueEqual(TechTree.get(UntilDawn.id("chain_crossbow")).parent().orElse(null), UntilDawn.id("repeater_crossbow"), "chain crossbow parent");
        helper.assertValueEqual(TechTree.get(UntilDawn.id("scope")).parent().orElse(null), UntilDawn.id("heavy_crossbow"), "scope parent");

        TechNode primitive = TechTree.get(UntilDawn.id("primitive_barricade"));
        helper.assertTrue(primitive != null && primitive.parent().isEmpty() && primitive.createResult().is(ModItems.PRIMITIVE_BARRICADE),
                "the Primitive Barricade starts the Fortification tree");
        helper.assertValueEqual(TechTree.get(UntilDawn.id("reinforced_barricade")).parent().orElse(null), UntilDawn.id("primitive_barricade"), "reinforced parent");
        helper.assertValueEqual(TechTree.get(UntilDawn.id("spiky_barricade")).parent().orElse(null), UntilDawn.id("primitive_barricade"), "spiky parent");
        helper.assertValueEqual(TechTree.get(UntilDawn.id("crossbow_sentry")).parent().orElse(null), UntilDawn.id("spiky_barricade"), "sentry parent");
        helper.assertValueEqual(TechTree.get(UntilDawn.id("spike_trap")).parent().orElse(null), UntilDawn.id("spiky_barricade"), "spike trap parent");
        helper.succeed();
    }

    /** Sets a barricade up the way placing its item does: the bottom block, then the other two. */
    private static BlockPos placeWall(GameTestHelper helper, Block wall, BlockPos pos, Direction facing) {
        BlockState bottom = wall.defaultBlockState().setValue(BarricadeBlock.FACING, facing);
        helper.setBlock(pos, bottom);
        wall.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), bottom, null, ItemStack.EMPTY);
        return pos;
    }

    private static Husk durableHusk(GameTestHelper helper, Vec3 pos) {
        Husk husk = helper.spawn(EntityTypes.HUSK, pos);
        husk.setNoAi(true);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        husk.setHealth(100.0F);
        return husk;
    }

    /** A wall is three blocks tall; breaking any of them removes all three, and it drops one item. */
    @GameTest(structure = ARENA)
    public void barricadeIsOneThreeBlockWall(GameTestHelper helper) {
        BlockPos pos = placeWall(helper, ModBlocks.PRIMITIVE_BARRICADE, new BlockPos(8, 1, 8), Direction.NORTH);
        for (BarricadeBlock.Part part : BarricadeBlock.Part.values()) {
            BlockState state = helper.getBlockState(pos.above(part.ordinal()));
            helper.assertTrue(state.is(ModBlocks.PRIMITIVE_BARRICADE) && state.getValue(BarricadeBlock.PART) == part, "part " + part);
        }
        helper.getLevel().destroyBlock(helper.absolutePos(pos.above()), true);
        for (int i = 0; i < 3; i++) {
            helper.assertBlockPresent(Blocks.AIR, pos.above(i));
        }
        long drops = helper.getEntities(EntityTypes.ITEM).stream().filter(item -> item.getItem().is(ModItems.PRIMITIVE_BARRICADE)).count();
        helper.assertValueEqual(drops, 1L, "barricades dropped");
        helper.succeed();
    }

    /** Mined from the front a wall is 3 times as hard as from behind (15 against 5), 5 times when reinforced (25) and 4 when spiky (20). */
    @GameTest(structure = ARENA)
    public void barricadeIsHarderFromTheFront(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Block[] walls = {ModBlocks.PRIMITIVE_BARRICADE, ModBlocks.REINFORCED_BARRICADE, ModBlocks.SPIKY_BARRICADE};
        float[] ratios = {3.0F, 5.0F, 4.0F};
        for (int i = 0; i < walls.length; i++) {
            BlockPos pos = placeWall(helper, walls[i], new BlockPos(8, 1, 8), Direction.NORTH);
            BlockPos at = helper.absolutePos(pos);
            BlockState state = helper.getBlockState(pos);
            // Facing north, the front is the north side.
            player.setPos(at.getX() + 0.5, at.getY(), at.getZ() - 2.5);
            float front = state.getDestroyProgress(player, helper.getLevel(), at);
            player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 3.5);
            float back = state.getDestroyProgress(player, helper.getLevel(), at);
            helper.assertTrue(Math.abs(back / front - ratios[i]) < 0.01F, walls[i] + " should be " + ratios[i] + "x easier from behind, was " + back / front);
            helper.getLevel().destroyBlock(at, false);
        }
        helper.succeed();
    }

    /** Spikes hurt whatever touches them, once, then need a second to come back up. */
    @GameTest(structure = ARENA, maxTicks = 100)
    public void spikeTrapHurtsThenRecharges(GameTestHelper helper) {
        BlockPos pos = new BlockPos(8, 1, 8);
        helper.setBlock(pos, ModBlocks.SPIKE_TRAP.defaultBlockState().setValue(SpikeTrapBlock.FACE, AttachFace.FLOOR));
        Husk target = durableHusk(helper, Vec3.atBottomCenterOf(pos));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(Math.abs(target.getHealth() - 80.0F) < 1.0F, "20 damage from the spikes, health was " + target.getHealth());
            helper.assertTrue(!helper.getBlockState(pos).getValue(SpikeTrapBlock.READY), "the spikes should be down");
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getBlockState(pos).getValue(SpikeTrapBlock.READY) || target.getHealth() < 61.0F,
                    "the spikes should be back up (or have struck again)");
            helper.succeed();
        });
    }

    /** A Spiky Barricade hurts what stands against it, and a plain one doesn't. */
    @GameTest(structure = ARENA, maxTicks = 100)
    public void spikyBarricadeHurtsWhatTouchesIt(GameTestHelper helper) {
        placeWall(helper, ModBlocks.SPIKY_BARRICADE, new BlockPos(4, 1, 8), Direction.NORTH);
        placeWall(helper, ModBlocks.PRIMITIVE_BARRICADE, new BlockPos(12, 1, 8), Direction.NORTH);
        Husk spiked = durableHusk(helper, new Vec3(4.5, 1, 7.7));
        Husk plain = durableHusk(helper, new Vec3(12.5, 1, 7.7));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(spiked.getHealth() < 100.0F, "the spiked wall should hurt what touches it");
            helper.assertValueEqual(plain.getHealth(), 100.0F, "health against a plain wall");
            helper.succeed();
        });
    }

    /** A Repeater Crossbow fires its three arrows one pull at a time, each at 80% of a normal crossbow's speed. */
    @GameTest(structure = ARENA, maxTicks = 60)
    public void repeaterCrossbowFiresOneArrowAtATime(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.teleportTo(helper.absolutePos(new BlockPos(8, 1, 8)).getX() + 0.5, helper.absolutePos(new BlockPos(8, 1, 8)).getY(),
                helper.absolutePos(new BlockPos(8, 1, 8)).getZ() + 0.5);
        ItemStack crossbow = new ItemStack(ModItems.REPEATER_CROSSBOW);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW), new ItemStack(Items.ARROW), new ItemStack(Items.ARROW))));
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        for (int shot = 1; shot <= 3; shot++) {
            crossbow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(8.0)).size(), shot, "arrows in the air after pull " + shot);
            ChargedProjectiles left = crossbow.get(DataComponents.CHARGED_PROJECTILES);
            helper.assertValueEqual(left == null ? 0 : left.size(), 3 - shot, "arrows left after pull " + shot);
        }
        for (Arrow arrow : helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(8.0))) {
            double speed = arrow.getDeltaMovement().length();
            helper.assertTrue(speed < 3.15 * 0.8 * 1.05 && speed > 3.15 * 0.8 * 0.5, "an arrow's speed was " + speed);
        }
        helper.succeed();
    }

    /** A Heavy Crossbow takes 1.5 times as long to wind as a crossbow and shoots at twice the speed; Power fits it, Multishot does not, and Power excludes Piercing. */
    @GameTest(structure = ARENA, maxTicks = 60)
    public void heavyCrossbowIsSlowToWindAndFast(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos spot = helper.absolutePos(new BlockPos(8, 1, 8));
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        ItemStack heavy = new ItemStack(ModItems.HEAVY_CROSSBOW);
        helper.assertValueEqual(HeavyCrossbowItem.getChargeDuration(heavy, player), 25, "a crossbow's winding");
        helper.assertValueEqual(((HeavyCrossbowItem) heavy.getItem()).windTicks(heavy, player), 37, "the heavy crossbow's winding");
        heavy.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        player.setItemInHand(InteractionHand.MAIN_HAND, heavy);
        heavy.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        List<Arrow> arrows = helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(8.0));
        helper.assertValueEqual(arrows.size(), 1, "arrows shot");
        double speed = arrows.getFirst().getDeltaMovement().length();
        helper.assertTrue(speed > 3.15 * 2 * 0.9 && speed < 3.15 * 2 * 1.05, "the arrow's speed was " + speed);

        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        helper.assertTrue(enchantments.getOrThrow(Enchantments.POWER).value().canEnchant(heavy), "Power fits a Heavy Crossbow");
        helper.assertTrue(enchantments.getOrThrow(Enchantments.PUNCH).value().canEnchant(heavy), "Punch fits too");
        helper.assertTrue(!enchantments.getOrThrow(Enchantments.MULTISHOT).value().canEnchant(heavy), "Multishot does not");
        helper.assertTrue(enchantments.getOrThrow(Enchantments.PIERCING).value().canEnchant(heavy), "Piercing does");
        helper.assertTrue(enchantments.getOrThrow(Enchantments.MULTISHOT).value().canEnchant(new ItemStack(Items.CROSSBOW)), "Multishot still fits a crossbow");
        helper.assertTrue(!Enchantment.areCompatible(enchantments.getOrThrow(Enchantments.POWER), enchantments.getOrThrow(Enchantments.PIERCING)),
                "Power and Piercing exclude each other");
        helper.succeed();
    }

    /** A Chain Crossbow fires by itself while held, from about a shot a second up to 4 a second after 2 seconds, and cannot take Quick Charge, Piercing or Multishot. */
    @GameTest(structure = ARENA, maxTicks = 60)
    public void chainCrossbowSpoolsUp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos spot = helper.absolutePos(new BlockPos(8, 1, 8));
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        ItemStack chain = new ItemStack(ModItems.CHAIN_CROSSBOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, chain);
        player.getInventory().add(new ItemStack(Items.ARROW, 30));
        chain.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), "holding right click starts it");
        int duration = chain.getUseDuration(player);
        int at40 = -1;
        for (int held = 0; held <= 60; held++) {
            chain.getItem().onUseTick(helper.getLevel(), player, chain, duration - held);
            if (held == 40) {
                at40 = helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(8.0)).size();
            }
        }
        int at60 = helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(8.0)).size();
        helper.assertValueEqual(at40, 5, "arrows in the first 2 seconds");
        helper.assertValueEqual(at60 - at40, 4, "arrows in the next second at full spool (a second is 20 ticks here, so 4)");
        helper.assertValueEqual(player.getInventory().countItem(Items.ARROW), 30 - at60, "arrows taken from the inventory");

        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        helper.assertTrue(!enchantments.getOrThrow(Enchantments.QUICK_CHARGE).value().canEnchant(chain), "no Quick Charge");
        helper.assertTrue(!enchantments.getOrThrow(Enchantments.PIERCING).value().canEnchant(chain), "no Piercing");
        helper.assertTrue(!enchantments.getOrThrow(Enchantments.MULTISHOT).value().canEnchant(chain), "no Multishot");
        helper.assertTrue(enchantments.getOrThrow(Enchantments.UNBREAKING).value().canEnchant(chain), "Unbreaking is fine");
        helper.succeed();
    }

    /** A loaded Scoped Heavy Crossbow zooms instead of shooting when used, and fires on the player's left click. */
    @GameTest(structure = ARENA, maxTicks = 60)
    public void scopedHeavyCrossbowAimsThenShoots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos spot = helper.absolutePos(new BlockPos(8, 1, 8));
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        ItemStack scoped = new ItemStack(ModItems.SCOPED_HEAVY_CROSSBOW);
        scoped.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        player.setItemInHand(InteractionHand.MAIN_HAND, scoped);
        scoped.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isScoping(), "a loaded scoped crossbow looks through its scope");
        helper.assertValueEqual(helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(8.0)).size(), 0, "arrows before the click");
        ScopedHeavyCrossbowItem.fire(player);
        helper.assertValueEqual(helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(8.0)).size(), 1, "arrows after the click");
        helper.assertTrue(!player.isScoping() && !ScopedHeavyCrossbowItem.isAiming(scoped), "the shot ends the aim and empties the crossbow");
        helper.succeed();
    }

    /** A Scope goes on a Heavy Crossbow and comes off again by crafting; the crossbow keeps its enchantments and stays behind. */
    @GameTest(structure = ARENA)
    public void scopeMountsAndDismounts(GameTestHelper helper) {
        ItemStack heavy = new ItemStack(ModItems.HEAVY_CROSSBOW);
        heavy.setDamageValue(7);
        heavy.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), 3);
        CraftingInput both = CraftingInput.of(2, 1, List.of(heavy, new ItemStack(ModItems.SCOPE)));
        ScopeRecipes.Mount mount = new ScopeRecipes.Mount();
        helper.assertTrue(mount.matches(both, helper.getLevel()), "a crossbow and a scope make a scoped one");
        ItemStack scoped = mount.assemble(both);
        helper.assertTrue(scoped.is(ModItems.SCOPED_HEAVY_CROSSBOW) && scoped.getDamageValue() == 7
                && scoped.getEnchantments().equals(heavy.getEnchantments()) && !scoped.getEnchantments().isEmpty(), "the scoped crossbow keeps what the crossbow had");
        helper.assertTrue(!mount.matches(CraftingInput.of(2, 1, List.of(heavy, new ItemStack(Items.SPYGLASS))), helper.getLevel()), "not with a spyglass");

        CraftingInput alone = CraftingInput.of(1, 1, List.of(scoped));
        ScopeRecipes.Dismount dismount = new ScopeRecipes.Dismount();
        helper.assertTrue(dismount.matches(alone, helper.getLevel()) && dismount.assemble(alone).is(ModItems.SCOPE), "a scoped crossbow alone gives the scope");
        ItemStack left = dismount.getRemainingItems(alone).getFirst();
        helper.assertTrue(left.is(ModItems.HEAVY_CROSSBOW) && left.getDamageValue() == 7 && left.getEnchantments().equals(heavy.getEnchantments()),
                "and leaves the crossbow, as it was");
        helper.succeed();
    }

    /** A Crossbow Sentry shoots monsters inside its 160 degree view for 8 damage, and ignores those to the side or behind. */
    @GameTest(structure = ARENA, maxTicks = 200)
    public void crossbowSentryShootsOnlyInItsView(GameTestHelper helper) {
        BlockPos pos = new BlockPos(8, 1, 8);
        // On a wall (a stone block behind it, to the south), facing out to the north.
        helper.setBlock(pos.south(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.CROSSBOW_SENTRY.defaultBlockState()
                .setValue(CrossbowSentryBlock.FACE, AttachFace.WALL).setValue(CrossbowSentryBlock.FACING, Direction.NORTH));
        Husk ahead = durableHusk(helper, new Vec3(8.5, 1, 3.5));
        Husk behind = durableHusk(helper, new Vec3(8.5, 1, 13.5));
        Husk beside = durableHusk(helper, new Vec3(13.5, 1, 8.5));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(Math.abs(ahead.getHealth() - 92.0F) < 1.0F, "8 damage from one shot, health was " + ahead.getHealth());
            helper.assertValueEqual(behind.getHealth(), 100.0F, "health of the one behind");
            helper.assertValueEqual(beside.getHealth(), 100.0F, "health of the one beside (90 degrees off)");
        });
        // Three seconds to reload.
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(ahead.getHealth() <= 85.0F, "a second shot after the reload");
            helper.succeed();
        });
    }

    /** Miners plan through a wall of barricades however hard they are, rather than treating it as unbreakable. */
    @GameTest(structure = ARENA)
    public void minersPlanThroughBarricades(GameTestHelper helper) {
        for (Block wall : List.of(ModBlocks.PRIMITIVE_BARRICADE, ModBlocks.REINFORCED_BARRICADE)) {
            for (int z = 0; z < 16; z++) {
                placeWall(helper, wall, new BlockPos(8, 1, z), Direction.WEST);
            }
            // A small search budget, standing in for open country, where flooding the ground costs far more nodes than this arena has.
            UntilDawnConfig.NODES_PER_SEARCH.set("150");
            MobPath path;
            try {
                path = plan(helper, PathAbilities.MINER, START, PathGoal.near(helper.absolutePos(new BlockPos(13, 1, 8)), 1, "across"));
            } finally {
                UntilDawnConfig.NODES_PER_SEARCH.reset();
            }
            helper.assertTrue(path != null && path.reachesGoal(), wall + ": the Miner should find a way across, got " + moves(helper, path));
            boolean digs = path.steps().stream().anyMatch(step -> step.breaks().stream().anyMatch(b -> helper.getLevel().getBlockState(b).is(wall)));
            helper.assertTrue(digs, wall + ": the way across should be through the wall, got " + moves(helper, path));
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(8, y, z), Blocks.AIR);
                }
            }
        }
        helper.succeed();
    }

    /** A Miner shut in behind a line of barricades digs through to the cow, though they are hard. */
    @GameTest(structure = ARENA, maxTicks = 1500)
    public void minerDigsThroughBarricades(GameTestHelper helper) {
        // Dug quickly so the test is short; the point is that it digs at all.
        UntilDawnConfig.MINER_DIG_SPEED.set("40");
        for (int z = 0; z < 16; z++) {
            placeWall(helper, ModBlocks.REINFORCED_BARRICADE, new BlockPos(8, 1, z), Direction.WEST);
        }
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(13, 1, 8));
        cow.setNoAi(true);
        MinerZombie miner = helper.spawn(ModEntities.MINER, START);
        miner.setTarget(cow);
        passWhen(helper, "minerDigsThroughBarricades", () -> {
            helper.assertTrue(isHurt(cow), "the miner has not got through to the cow yet: " + state(helper, miner));
            UntilDawnConfig.MINER_DIG_SPEED.reset();
        });
    }

    /** Two Miners of a squad at the same wall dig the same block together, and the cow is reached. */
    @GameTest(structure = ARENA, maxTicks = 1500)
    public void minersDigTheSameBlockTogether(GameTestHelper helper) {
        UntilDawnConfig.MINER_DIG_SPEED.set("2");
        for (int z = 0; z < 16; z++) {
            placeWall(helper, ModBlocks.REINFORCED_BARRICADE, new BlockPos(8, 1, z), Direction.WEST);
        }
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(13, 1, 8));
        cow.setNoAi(true);
        MinerZombie first = helper.spawn(ModEntities.MINER, new BlockPos(6, 1, 8));
        MinerZombie second = helper.spawn(ModEntities.MINER, new BlockPos(6, 1, 9));
        Squads.form(helper.getLevel(), List.of(first, second));
        first.setTarget(cow);
        second.setTarget(cow);
        boolean[] helped = {false};
        passWhen(helper, "minersDigTheSameBlockTogether", () -> {
            helped[0] |= first.pathController().status().equals("helping a squadmate dig")
                    || second.pathController().status().equals("helping a squadmate dig");
            helper.assertTrue(helped[0], "neither Miner has helped the other dig yet: " + state(helper, first) + " / " + state(helper, second));
            UntilDawnConfig.MINER_DIG_SPEED.reset();
        });
    }

    /** Breaking a Workbench keeps its research on the dropped item, and placing that item again restores it. */
    @GameTest(structure = ARENA)
    public void workbenchKeepsResearchWhenBroken(GameTestHelper helper) {
        BlockPos pos = new BlockPos(8, 1, 8);
        helper.setBlock(pos, ModBlocks.WORKBENCH);
        Identifier wooden = UntilDawn.id("arrow");
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

    /** A Bomber's blast wears walls down by itself (whatever the mob griefing rule): stone beside it falls, farther blocks only crack. */
    @GameTest(structure = ARENA, maxTicks = 120)
    public void bomberBlastDamagesWalls(GameTestHelper helper) {
        for (int y = 1; y <= 2; y++) {
            for (int x = 9; x <= 11; x++) {
                helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
            }
        }
        org.kennyerf.untildawn.entity.BomberCreeper bomber = helper.spawn(ModEntities.BOMBER, new BlockPos(8, 1, 8));
        bomber.setNoAi(true);
        bomber.ignite();
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(9, 1, 8));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(11, 1, 8));
            helper.succeed();
        });
    }
}
