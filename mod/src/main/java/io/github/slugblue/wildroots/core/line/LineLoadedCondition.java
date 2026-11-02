package io.github.slugblue.wildroots.core.line;

import io.github.slugblue.wildroots.core.Ids;
import com.google.gson.JsonObject;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * Condition {@code wildroots:line_loaded} (docs/05-architecture.md §9.2–9.3;
 * S1): true when the line is enabled in {@link LineRegistry}. Works in
 * recipes and advancements ({@code "conditions"}) and in data-pack registry
 * entries ({@code "forge:conditions"}). Registered with
 * {@code CraftingHelper.register} in {@code FMLCommonSetupEvent#enqueueWork}.
 *
 * <pre>{"type": "wildroots:line_loaded", "line": "carrot"}</pre>
 */
public final class LineLoadedCondition implements ICondition {

    public static final ResourceLocation ID = Ids.mod("line_loaded");

    private final String line;
    private final Predicate<String> enabled;

    public LineLoadedCondition(String line) {
        this(line, l -> LineRegistry.get().isEnabled(l));
    }

    /** With an explicit view of the enabled lines (tests). */
    public LineLoadedCondition(String line, Predicate<String> enabled) {
        this.line = line;
        this.enabled = enabled;
    }

    public String line() {
        return line;
    }

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return enabled.test(line);
    }

    @Override
    public String toString() {
        return "line_loaded(\"" + line + "\")";
    }

    public static final IConditionSerializer<LineLoadedCondition> SERIALIZER = new IConditionSerializer<>() {
        @Override
        public void write(JsonObject json, LineLoadedCondition value) {
            json.addProperty("line", value.line());
        }

        @Override
        public LineLoadedCondition read(JsonObject json) {
            return new LineLoadedCondition(GsonHelper.getAsString(json, "line"));
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    };
}
