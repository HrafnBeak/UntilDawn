package org.kennyerf.monster.debug;

import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.kennyerf.monster.config.MonsterConfig;
import org.kennyerf.monster.config.Setting;
import org.kennyerf.monster.entity.WaveMob;
import org.kennyerf.monster.entity.WaveMobs;
import org.kennyerf.monster.entity.ai.MobPathController;
import org.kennyerf.monster.pathing.MobPath;
import org.kennyerf.monster.pathing.PathStep;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Debug commands (operators only):
 * <pre>
 * /monster path show              draw the paths of every wave mob near you
 * /monster path show &lt;mobs&gt;       draw the paths of specific mobs
 * /monster path hide              stop drawing
 * /monster path info &lt;mobs&gt;       print path details in chat
 * /monster path recompute &lt;mobs&gt;  make mobs throw their path away and plan again
 *
 * /monster config                 list every setting (click one to edit it)
 * /monster config &lt;key&gt;           show a setting
 * /monster config &lt;key&gt; &lt;value&gt;   change a setting (saved, applied to loaded mobs immediately)
 * /monster config reset &lt;key|all&gt; back to defaults
 * /monster config reload          re-read config/monster.json after editing it by hand
 * </pre>
 */
public final class MonsterCommand {
    private static final int MAX_INFO_LINES = 8;
    private static final DynamicCommandExceptionType UNKNOWN_SETTING =
            new DynamicCommandExceptionType(key -> Component.literal("Unknown setting: " + key + " (see /monster config)"));

    private MonsterCommand() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("monster")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("path")
                        .then(Commands.literal("show")
                                .executes(MonsterCommand::showNearby)
                                .then(Commands.argument("mobs", EntityArgument.entities()).executes(MonsterCommand::showSelected)))
                        .then(Commands.literal("hide").executes(MonsterCommand::hide))
                        .then(Commands.literal("info")
                                .then(Commands.argument("mobs", EntityArgument.entities()).executes(MonsterCommand::info)))
                        .then(Commands.literal("recompute")
                                .then(Commands.argument("mobs", EntityArgument.entities()).executes(MonsterCommand::recompute))))
                .then(Commands.literal("config")
                        .executes(MonsterCommand::configList)
                        .then(Commands.literal("reload").executes(MonsterCommand::configReload))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("all").executes(MonsterCommand::configResetAll))
                                .then(Commands.argument("key", StringArgumentType.word()).suggests(MonsterCommand::suggestKeys)
                                        .executes(MonsterCommand::configReset)))
                        .then(Commands.argument("key", StringArgumentType.word()).suggests(MonsterCommand::suggestKeys)
                                .executes(MonsterCommand::configGet)
                                .then(Commands.argument("value", StringArgumentType.greedyString()).suggests(MonsterCommand::suggestValues)
                                        .executes(MonsterCommand::configSet)))));
    }

    // ---------------------------------------------------------------- /monster config

    private static int configList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Monster config (click a line to edit, gold = changed from default):")
                .withStyle(ChatFormatting.GOLD), false);
        String group = "";
        for (Setting<?> setting : MonsterConfig.all()) {
            String settingGroup = setting.key().substring(0, setting.key().indexOf('.'));
            if (!settingGroup.equals(group)) {
                group = settingGroup;
                String header = group;
                source.sendSuccess(() -> Component.literal("- " + header).withStyle(ChatFormatting.DARK_AQUA), false);
            }
            source.sendSuccess(() -> settingLine(setting), false);
        }
        return MonsterConfig.all().size();
    }

    private static Component settingLine(Setting<?> setting) {
        Component hover = Component.literal(setting.description() + "\nRange: " + setting.range() + "\nDefault: " + setting.displayDefault());
        return Component.literal("  " + setting.key() + " = ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(setting.display()).withStyle(setting.isDefault() ? ChatFormatting.WHITE : ChatFormatting.GOLD))
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.SuggestCommand("/monster config " + setting.key() + " "))
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
        MonsterConfig.all().forEach(Setting::reset);
        return saveAndApply(context, "All settings reset to defaults");
    }

    private static int configReload(CommandContext<CommandSourceStack> context) {
        MonsterConfig.load();
        return saveAndApply(context, "Reloaded config/monster.json");
    }

    private static int saveAndApply(CommandContext<CommandSourceStack> context, String message) {
        MonsterConfig.save();
        int mobs = WaveMobs.applyConfigToAll(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(message + " (applied to " + mobs + " loaded mob(s))")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static Setting<?> setting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, "key");
        Setting<?> setting = MonsterConfig.get(key);
        if (setting == null) {
            throw UNKNOWN_SETTING.create(key);
        }
        return setting;
    }

    private static CompletableFuture<Suggestions> suggestKeys(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(MonsterConfig.all().stream().map(Setting::key).toList(), builder);
    }

    private static CompletableFuture<Suggestions> suggestValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Setting<?> setting = MonsterConfig.get(StringArgumentType.getString(context, "key"));
        if (setting == null) {
            return builder.buildFuture();
        }
        if (setting.get() instanceof Boolean) {
            return SharedSuggestionProvider.suggest(List.of("true", "false"), builder);
        }
        if (setting.get() instanceof Identifier) {
            return SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder);
        }
        return SharedSuggestionProvider.suggest(List.of(setting.display(), setting.displayDefault()), builder);
    }

    private static int showNearby(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PathDebug.watchNearby(player);
        context.getSource().sendSuccess(() -> Component.literal(
                "Showing paths of Monster mobs within 48 blocks. Green walk, yellow up/down, red break, blue place, gold goal (purple = partial).")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int showSelected(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<Mob> mobs = waveMobs(EntityArgument.getEntities(context, "mobs"));
        if (mobs.isEmpty()) {
            context.getSource().sendFailure(Component.literal("None of those are Monster mobs."));
            return 0;
        }
        PathDebug.watch(player, mobs);
        context.getSource().sendSuccess(() -> Component.literal("Showing paths of " + mobs.size() + " mob(s).")
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
            context.getSource().sendFailure(Component.literal("None of those are Monster mobs."));
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

    private static Component describe(Mob mob) {
        MobPathController controller = ((WaveMob) mob).pathController();
        MobPath path = controller.path();
        LivingEntity target = mob.getTarget();
        StringBuilder text = new StringBuilder()
                .append(mob.getName().getString()).append(" #").append(mob.getId())
                .append(" | ").append(controller.status())
                .append(" | target: ").append(target != null ? target.getName().getString() : "the Core");
        if (path == null) {
            text.append(" | no path");
        } else {
            int index = Math.min(controller.index(), path.steps().size() - 1);
            PathStep step = path.steps().get(index);
            BlockPos pos = step.pos();
            text.append(String.format(" | path to %s: %d steps, cost %.0f, %d breaks, %d places, %s, %d nodes in %.1f ms",
                            path.goalDescription(), path.steps().size() - 1, path.cost(), path.breakCount(), path.placeCount(),
                            path.reachesGoal() ? "complete" : "PARTIAL", path.nodesExplored(), path.micros() / 1000.0))
                    .append(String.format(" | step %d: %s at %d %d %d", controller.index(), step.type().name().toLowerCase(),
                            pos.getX(), pos.getY(), pos.getZ()));
        }
        return Component.literal(text.toString()).withStyle(ChatFormatting.YELLOW);
    }

    private static List<Mob> waveMobs(java.util.Collection<? extends Entity> entities) {
        return entities.stream()
                .filter(e -> e instanceof Mob && e instanceof WaveMob)
                .map(e -> (Mob) e)
                .toList();
    }
}
