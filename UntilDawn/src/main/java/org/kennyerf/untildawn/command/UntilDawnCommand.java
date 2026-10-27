package org.kennyerf.untildawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.kennyerf.untildawn.UntilDawn;
import org.kennyerf.untildawn.baritone.BaritonePrediction;
import org.kennyerf.untildawn.debug.PathDebug;
import org.kennyerf.untildawn.entity.WaveMob;
import org.kennyerf.untildawn.entity.ai.MobPathController;
import org.kennyerf.untildawn.game.CoreState;
import org.kennyerf.untildawn.pathing.MobPath;
import org.kennyerf.untildawn.pathing.PathStep;
import org.kennyerf.untildawn.squad.Squad;
import org.kennyerf.untildawn.squad.Squads;
import org.kennyerf.untildawn.config.UntilDawnConfig;
import org.kennyerf.untildawn.config.Setting;
import org.kennyerf.untildawn.entity.WaveMobs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Commands (operators only):
 * <pre>
 * /untildawn path show              draw (with Baritone, on your client) the paths of every wave mob near you
 * /untildawn path show &lt;mobs&gt;       draw the paths of specific mobs
 * /untildawn path hide              stop drawing
 * /untildawn path info &lt;mobs&gt;       print path, squad and help details in chat
 * /untildawn path recompute &lt;mobs&gt;  make mobs throw their path away and plan again
 * /untildawn path predict &lt;from&gt; &lt;to&gt; [items]
 *                                 ask Baritone how something carrying the items (default: your inventory) would get
 *                                 from one block to the other; draws the path and sums up what it breaks and uses
 *
 * /untildawn night                  show which night comes next
 * /untildawn night &lt;number&gt;         make the next night that starts this night (its wave size and mob types follow)
 *
 * /untildawn config                 list every setting (click one to edit it)
 * /untildawn config &lt;key&gt;           show a setting
 * /untildawn config &lt;key&gt; &lt;value&gt;   change a setting (saved, applied to loaded mobs immediately)
 * /untildawn config reset &lt;key|all&gt; back to defaults
 * /untildawn config reload          re-read config/untildawn.json5 after editing it by hand
 * </pre>
 */
public final class UntilDawnCommand {
    private static final int MAX_INFO_LINES = 8;
    private static final String NOT_WAVE_MOBS = "None of those are UntilDawn mobs.";
    private static final String CANT_DRAW = "Paths are drawn by Baritone on your client: install Monster API (this mod's"
            + " Baritone) to see them. /untildawn path info works without it.";
    private static final String DRAWING = "Drawn by Baritone in your path colours: blocks to break and place are boxed,"
            + " and paths that can't reach their goal yet use your 'best path so far' colour.";
    private static final String NO_BARITONE = "Predictions need Baritone (the build with entity pathing) installed,"
            + " and only work in singleplayer or on a LAN world you host.";
    private static final String NO_CORE = "No Core is active: place one to start a game.";
    private static final DynamicCommandExceptionType UNKNOWN_SETTING =
            new DynamicCommandExceptionType(key -> Component.literal("Unknown setting: " + key + " (see /untildawn config)"));

    private UntilDawnCommand() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("untildawn")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("path")
                        .then(Commands.literal("show")
                                .executes(UntilDawnCommand::showNearby)
                                .then(Commands.argument("mobs", EntityArgument.entities()).executes(UntilDawnCommand::showSelected)))
                        .then(Commands.literal("hide").executes(UntilDawnCommand::hide))
                        .then(Commands.literal("info")
                                .then(Commands.argument("mobs", EntityArgument.entities()).executes(UntilDawnCommand::info)))
                        .then(Commands.literal("recompute")
                                .then(Commands.argument("mobs", EntityArgument.entities()).executes(UntilDawnCommand::recompute)))
                        .then(Commands.literal("predict")
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(context -> predict(context, null))
                                                .then(Commands.argument("items", StringArgumentType.greedyString())
                                                        .suggests((context, builder) -> ItemList.suggest(builder, context.getSource().registryAccess()))
                                                        .executes(context -> predict(context, StringArgumentType.getString(context, "items"))))))))
                .then(Commands.literal("night")
                        .executes(UntilDawnCommand::nightGet)
                        .then(Commands.argument("number", IntegerArgumentType.integer(1)).executes(UntilDawnCommand::nightSet)))
                .then(Commands.literal("config")
                        .executes(UntilDawnCommand::configList)
                        .then(Commands.literal("reload").executes(UntilDawnCommand::configReload))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("all").executes(UntilDawnCommand::configResetAll))
                                .then(Commands.argument("key", StringArgumentType.word()).suggests(UntilDawnCommand::suggestKeys)
                                        .executes(UntilDawnCommand::configReset)))
                        .then(Commands.argument("key", StringArgumentType.word()).suggests(UntilDawnCommand::suggestKeys)
                                .executes(UntilDawnCommand::configGet)
                                .then(Commands.argument("value", StringArgumentType.greedyString()).suggests(UntilDawnCommand::suggestValues)
                                        .executes(UntilDawnCommand::configSet)))));
    }

    // ---------------------------------------------------------------- /untildawn night

    private static int nightGet(CommandContext<CommandSourceStack> context) {
        CoreState state = CoreState.get(context.getSource().getServer());
        if (!state.isActive()) {
            context.getSource().sendFailure(Component.literal(NO_CORE));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("The next night will be night " + state.upcomingNight() + ".")
                .withStyle(ChatFormatting.GRAY), false);
        return state.upcomingNight();
    }

    private static int nightSet(CommandContext<CommandSourceStack> context) {
        CoreState state = CoreState.get(context.getSource().getServer());
        if (!state.isActive()) {
            context.getSource().sendFailure(Component.literal(NO_CORE));
            return 0;
        }
        int number = IntegerArgumentType.getInteger(context, "number");
        state.setNextNight(number);
        context.getSource().sendSuccess(() -> Component.literal("The next night will be night " + number
                + " (a night already under way keeps its wave).").withStyle(ChatFormatting.GREEN), true);
        return number;
    }

    // ---------------------------------------------------------------- /untildawn config

    private static int configList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("UntilDawn config (click a line to edit, gold = changed from default):")
                .withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, List<Setting<?>>> group : UntilDawnConfig.groups().entrySet()) {
            source.sendSuccess(() -> Component.literal("- " + group.getKey()).withStyle(ChatFormatting.DARK_AQUA), false);
            for (Setting<?> setting : group.getValue()) {
                source.sendSuccess(() -> settingLine(setting), false);
            }
        }
        return UntilDawnConfig.all().size();
    }

    private static Component settingLine(Setting<?> setting) {
        Component hover = Component.literal(setting.description() + "\nRange: " + setting.range() + "\nDefault: " + setting.displayDefault());
        return Component.literal("  " + setting.key() + " = ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(setting.display()).withStyle(setting.isDefault() ? ChatFormatting.WHITE : ChatFormatting.GOLD))
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.SuggestCommand("/untildawn config " + setting.key() + " "))
                        .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static int configGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Setting<?> setting = setting(context);
        context.getSource().sendSuccess(() -> settingLine(setting).copy()
                .append(Component.literal("  (" + setting.description() + "; " + setting.range() + "; default " + setting.displayDefault() + ")")
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        return 1;
    }

    private static int configSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Setting<?> setting = setting(context);
        String error = setting.set(StringArgumentType.getString(context, "value"));
        if (error != null) {
            context.getSource().sendFailure(Component.literal(setting.key() + ": " + error));
            return 0;
        }
        return saveAndApply(context, setting.key() + " = " + setting.display());
    }

    private static int configReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Setting<?> setting = setting(context);
        setting.reset();
        return saveAndApply(context, setting.key() + " reset to " + setting.display());
    }

    private static int configResetAll(CommandContext<CommandSourceStack> context) {
        UntilDawnConfig.all().forEach(Setting::reset);
        return saveAndApply(context, "All settings reset to defaults");
    }

    private static int configReload(CommandContext<CommandSourceStack> context) {
        List<String> problems = UntilDawnConfig.load();
        for (String problem : problems) {
            context.getSource().sendFailure(Component.literal(problem));
        }
        int mobs = WaveMobs.applyConfigToAll(context.getSource().getServer());
        String message = problems.isEmpty()
                ? "Reloaded config/" + UntilDawnConfig.FILE_NAME
                : "Reloaded config/" + UntilDawnConfig.FILE_NAME + " with " + problems.size() + " problem(s)";
        context.getSource().sendSuccess(() -> Component.literal(message + " (applied to " + mobs + " loaded mob(s))")
                .withStyle(problems.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return problems.isEmpty() ? 1 : 0;
    }

    private static int saveAndApply(CommandContext<CommandSourceStack> context, String message) {
        if (!UntilDawnConfig.save()) {
            context.getSource().sendFailure(Component.literal("Could not write config/" + UntilDawnConfig.FILE_NAME
                    + "; the change only lasts until restart (see the server log)"));
        }
        int mobs = WaveMobs.applyConfigToAll(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(message + " (applied to " + mobs + " loaded mob(s))")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static Setting<?> setting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, "key");
        Setting<?> setting = UntilDawnConfig.get(key);
        if (setting == null) {
            throw UNKNOWN_SETTING.create(key);
        }
        return setting;
    }

    private static CompletableFuture<Suggestions> suggestKeys(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(UntilDawnConfig.all().stream().map(Setting::key).toList(), builder);
    }

    private static CompletableFuture<Suggestions> suggestValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Setting<?> setting = UntilDawnConfig.get(StringArgumentType.getString(context, "key"));
        if (setting == null) {
            return builder.buildFuture();
        }
        if (setting.get() instanceof Boolean) {
            return SharedSuggestionProvider.suggest(List.of("true", "false"), builder);
        }
        return SharedSuggestionProvider.suggest(List.of(setting.display(), setting.displayDefault()), builder);
    }

    // ---------------------------------------------------------------- /untildawn path

    private static int showNearby(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!PathDebug.canDraw(player)) {
            context.getSource().sendFailure(Component.literal(CANT_DRAW));
            return 0;
        }
        PathDebug.watchNearby(player);
        context.getSource().sendSuccess(() -> Component.literal("Showing paths of UntilDawn mobs near you. " + DRAWING)
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int showSelected(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<Mob> mobs = waveMobs(EntityArgument.getEntities(context, "mobs"));
        if (mobs.isEmpty()) {
            context.getSource().sendFailure(Component.literal(NOT_WAVE_MOBS));
            return 0;
        }
        if (!PathDebug.canDraw(player)) {
            context.getSource().sendFailure(Component.literal(CANT_DRAW));
            return 0;
        }
        PathDebug.watch(player, mobs);
        context.getSource().sendSuccess(() -> Component.literal("Showing paths of " + mobs.size() + " mob(s). " + DRAWING)
                .withStyle(ChatFormatting.GRAY), false);
        return mobs.size();
    }

    private static int hide(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        boolean wasShowing = PathDebug.stop(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.literal(wasShowing ? "Path display off." : "Path display was not on.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<Mob> mobs = waveMobs(EntityArgument.getEntities(context, "mobs"));
        if (mobs.isEmpty()) {
            context.getSource().sendFailure(Component.literal(NOT_WAVE_MOBS));
            return 0;
        }
        for (Mob mob : mobs.subList(0, Math.min(MAX_INFO_LINES, mobs.size()))) {
            context.getSource().sendSuccess(() -> describe(mob), false);
        }
        if (mobs.size() > MAX_INFO_LINES) {
            context.getSource().sendSuccess(() -> Component.literal("... and " + (mobs.size() - MAX_INFO_LINES) + " more")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return mobs.size();
    }

    private static int recompute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<Mob> mobs = waveMobs(EntityArgument.getEntities(context, "mobs"));
        mobs.forEach(mob -> ((WaveMob) mob).pathController().forceRepath());
        context.getSource().sendSuccess(() -> Component.literal(mobs.size() + " mob(s) will plan a new path.")
                .withStyle(ChatFormatting.GRAY), false);
        return mobs.size();
    }

    private static int predict(CommandContext<CommandSourceStack> context, @Nullable String itemList) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!BaritonePrediction.isAvailable()) {
            source.sendFailure(Component.literal(NO_BARITONE));
            return 0;
        }
        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "to");
        List<ItemStack> items = itemList != null ? ItemList.parse(itemList, source.registryAccess()) : carried(source);
        ServerPlayer player = source.getPlayer();
        source.sendSuccess(() -> Component.literal("Asking Baritone...").withStyle(ChatFormatting.GRAY), false);
        BaritonePrediction.predict(source.getLevel(), from, to, items).whenComplete((prediction, error) -> {
            if (error != null) {
                UntilDawn.LOGGER.error("Baritone prediction failed", error);
                source.sendFailure(Component.literal("Baritone prediction failed: " + error));
                return;
            }
            prediction.summary().forEach(line -> source.sendSuccess(() -> line, false));
            if (player != null && PathDebug.canDraw(player)) {
                PathDebug.showPrediction(player, prediction.path());
                if (prediction.path() != null) {
                    source.sendSuccess(() -> Component.literal("Drawn by Baritone until /untildawn path hide.")
                            .withStyle(ChatFormatting.DARK_GRAY), false);
                }
            }
        });
        return 1;
    }

    /** Everything the player running the command carries, armour and off hand included; nothing for a command block. */
    private static List<ItemStack> carried(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return List.of();
        }
        Inventory inventory = player.getInventory();
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        return items;
    }

    private static Component describe(Mob mob) {
        MobPathController controller = ((WaveMob) mob).pathController();
        MobPath path = controller.path();
        LivingEntity target = mob.getTarget();
        Squad squad = Squads.of(mob);
        StringBuilder text = new StringBuilder()
                .append(mob.getName().getString()).append(" #").append(mob.getId())
                .append(" | ").append(squad != null ? squad.toString() : "no squad")
                .append(" | ").append(controller.status())
                .append(" | target: ").append(target != null ? target.getName().getString() : "the squad's goal");
        if (controller.job() != null) {
            text.append(" | helping with request #").append(controller.job().id());
        }
        if (path == null) {
            text.append(" | no path");
        } else {
            int index = Math.min(controller.index(), path.steps().size() - 1);
            PathStep step = path.steps().get(index);
            BlockPos pos = step.pos();
            text.append(String.format(Locale.ROOT, " | path to %s: %d steps, cost %.0f, %d breaks, %d places, %s, %d nodes in %.1f ms",
                            path.goalDescription(), path.steps().size() - 1, path.cost(), path.breakCount(), path.placeCount(),
                            path.reachesGoal() ? "complete" : "PARTIAL", path.nodesExplored(), path.micros() / 1000.0))
                    .append(String.format(Locale.ROOT, " | step %d: %s at %d %d %d", controller.index(),
                            step.type().name().toLowerCase(Locale.ROOT), pos.getX(), pos.getY(), pos.getZ()));
        }
        return Component.literal(text.toString()).withStyle(ChatFormatting.YELLOW);
    }

    private static List<Mob> waveMobs(Collection<? extends Entity> entities) {
        return entities.stream()
                .filter(e -> e instanceof Mob && e instanceof WaveMob)
                .map(e -> (Mob) e)
                .toList();
    }
}
