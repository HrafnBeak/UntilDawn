package org.kennyerf.untildawn.workbench;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;

/**
 * One item in the Workbench's tech tree. It is loaded from a datapack file, {@code data/<namespace>/untildawn/tech_tree/<name>.json},
 * whose path is its id:
 * <pre>{@code
 * {
 *   "result": "minecraft:stone_sword",                  // what crafting it gives; or { "id": ..., "count": ..., "components": ... }
 *   "parent": "untildawn:wooden_sword",                // must be researched first; leave out for the top of a tree
 *   "research_cost": 15,                               // Scrap, paid once per bench
 *   "craft_cost": [                                    // paid every time it is crafted
 *     { "ingredient": "#minecraft:stone_tool_materials", "count": 2 }
 *   ],
 *   "order": 0                                         // optional: sorts siblings left to right (then by id)
 * }
 * }</pre>
 * An ingredient is an item id, a {@code #tag}, or a list of item ids.
 */
public record TechNode(Identifier id, ItemStackTemplate result, Optional<Identifier> parent, int researchCost, List<Cost> craftCost, int order) {

    /** {@code count} of anything matching {@code ingredient}. */
    public record Cost(Ingredient ingredient, int count) {
        static final Codec<Cost> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Cost::ingredient),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Cost::count)
        ).apply(i, Cost::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Cost> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Cost::ingredient,
                ByteBufCodecs.VAR_INT, Cost::count,
                Cost::new);
    }

    private static final Identifier UNNAMED = Identifier.fromNamespaceAndPath("untildawn", "unnamed");

    /** The file format. The id is not in the file (it is the file's path), so it is filled in by {@link #withId}. */
    public static final Codec<TechNode> FILE_CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(TechNode::result),
            Identifier.CODEC.optionalFieldOf("parent").forGetter(TechNode::parent),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("research_cost", 0).forGetter(TechNode::researchCost),
            Cost.CODEC.listOf().optionalFieldOf("craft_cost", List.of()).forGetter(TechNode::craftCost),
            Codec.INT.optionalFieldOf("order", 0).forGetter(TechNode::order)
    ).apply(i, (result, parent, research, craft, order) -> new TechNode(UNNAMED, result, parent, research, craft, order)));

    public static final StreamCodec<RegistryFriendlyByteBuf, TechNode> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, TechNode::id,
            ItemStackTemplate.STREAM_CODEC, TechNode::result,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), TechNode::parent,
            ByteBufCodecs.VAR_INT, TechNode::researchCost,
            Cost.STREAM_CODEC.apply(ByteBufCodecs.list()), TechNode::craftCost,
            ByteBufCodecs.VAR_INT, TechNode::order,
            TechNode::new);

    /** A fresh stack of what crafting this gives. */
    public ItemStack createResult() {
        return result.create();
    }

    TechNode withId(Identifier id) {
        return new TechNode(id, result, parent, researchCost, craftCost, order);
    }
}
