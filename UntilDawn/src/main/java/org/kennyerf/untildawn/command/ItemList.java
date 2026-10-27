package org.kennyerf.untildawn.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An inventory typed into a command: items separated by spaces, each with an optional {@code *count}, e.g.
 * {@code iron_pickaxe cobblestone*64 diamond_boots[enchantments={frost_walker:2}]}. Counts may exceed a stack.
 */
public final class ItemList {
    private ItemList() {
    }

    public static List<ItemStack> parse(String text, HolderLookup.Provider registries) throws CommandSyntaxException {
        ItemParser parser = new ItemParser(registries);
        StringReader reader = new StringReader(text);
        List<ItemStack> items = new ArrayList<>();
        reader.skipWhitespace();
        while (reader.canRead()) {
            ItemInput item = parser.parse(reader);
            int count = 1;
            if (reader.canRead() && reader.peek() == '*') {
                reader.skip();
                int start = reader.getCursor();
                count = reader.readInt();
                if (count < 1) {
                    reader.setCursor(start);
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().createWithContext(reader, count, 1);
                }
            }
            if (reader.canRead() && reader.peek() != ' ') {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherExpectedArgumentSeparator().createWithContext(reader);
            }
            items.add(new ItemStack(item.item(), count, item.components()));
            reader.skipWhitespace();
        }
        return items;
    }

    /** Suggests items (and their components) for the word being typed; nothing while typing a count. */
    public static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, HolderLookup.Provider registries) {
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + builder.getRemaining().lastIndexOf(' ') + 1);
        if (word.getRemaining().indexOf('*') >= 0) {
            return word.buildFuture();
        }
        return new ItemParser(registries).fillSuggestions(word);
    }
}
