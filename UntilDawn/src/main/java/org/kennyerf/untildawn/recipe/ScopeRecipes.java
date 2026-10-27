package org.kennyerf.untildawn.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.kennyerf.untildawn.UntilDawn;
import org.kennyerf.untildawn.registry.ModItems;

/**
 * Putting a Scope on a Heavy Crossbow and taking it off again, by shapeless crafting. The crossbow keeps everything
 * it has (enchantments, wear, a loaded arrow) in both directions. Taking the scope off leaves the crossbow in the
 * grid, as a bucket is left when you bake a cake.
 */
public final class ScopeRecipes {
    public static final RecipeSerializer<Mount> MOUNT = new RecipeSerializer<>(MapCodec.unit(Mount::new), StreamCodec.unit(new Mount()));
    public static final RecipeSerializer<Dismount> DISMOUNT = new RecipeSerializer<>(MapCodec.unit(Dismount::new), StreamCodec.unit(new Dismount()));

    private ScopeRecipes() {
    }

    public static void init() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, UntilDawn.id("mount_scope"), MOUNT);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, UntilDawn.id("dismount_scope"), DISMOUNT);
    }

    /** A Heavy Crossbow and a Scope make a Scoped Heavy Crossbow. */
    public static class Mount extends CustomRecipe {
        @Override
        public boolean matches(CraftingInput input, Level level) {
            return input.ingredientCount() == 2 && find(input, ModItems.HEAVY_CROSSBOW) >= 0 && find(input, ModItems.SCOPE) >= 0;
        }

        @Override
        public ItemStack assemble(CraftingInput input) {
            return carryOver(input.getItem(find(input, ModItems.HEAVY_CROSSBOW)), ModItems.SCOPED_HEAVY_CROSSBOW);
        }

        @Override
        public RecipeSerializer<Mount> getSerializer() {
            return MOUNT;
        }
    }

    /** A Scoped Heavy Crossbow alone gives its Scope, and the crossbow stays. */
    public static class Dismount extends CustomRecipe {
        @Override
        public boolean matches(CraftingInput input, Level level) {
            return input.ingredientCount() == 1 && find(input, ModItems.SCOPED_HEAVY_CROSSBOW) >= 0;
        }

        @Override
        public ItemStack assemble(CraftingInput input) {
            return new ItemStack(ModItems.SCOPE);
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            NonNullList<ItemStack> left = NonNullList.withSize(input.size(), ItemStack.EMPTY);
            int slot = find(input, ModItems.SCOPED_HEAVY_CROSSBOW);
            if (slot >= 0) {
                left.set(slot, carryOver(input.getItem(slot), ModItems.HEAVY_CROSSBOW));
            }
            return left;
        }

        @Override
        public RecipeSerializer<Dismount> getSerializer() {
            return DISMOUNT;
        }
    }

    private static int find(CraftingInput input, Item item) {
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static ItemStack carryOver(ItemStack from, Item to) {
        ItemStack result = new ItemStack(to);
        result.applyComponents(from.getComponentsPatch());
        return result;
    }
}
