package moze_intel.projecte;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Configuration groundwork for exact item-variant transmutation. */
public final class PEPreservationConfig {
    private static final String FILE_NAME = "projecte-preservation.properties";
    private static boolean preserveItemData;
    private static boolean preserveCustomNames;
    private static boolean preserveEnchantments;
    private static boolean preservePotionEffects;
    private static boolean preserveCustomData;

    private PEPreservationConfig() {}

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            } catch (IOException exception) {
                PECore.LOGGER.warn("Could not read {}", file, exception);
            }
        }
        preserveItemData = read(properties, "preserve_item_data", true);
        preserveCustomNames = read(properties, "preserve_custom_names", true);
        preserveEnchantments = read(properties, "preserve_enchantments", true);
        preservePotionEffects = read(properties, "preserve_potion_effects", true);
        preserveCustomData = read(properties, "preserve_custom_data", true);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "ProjectEF item preservation settings");
            }
        } catch (IOException exception) {
            PECore.LOGGER.warn("Could not write {}", file, exception);
        }
    }

    private static boolean read(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        boolean result = value == null ? defaultValue : Boolean.parseBoolean(value.trim());
        properties.setProperty(key, Boolean.toString(result));
        return result;
    }

    /** Wear never identifies a learned item; purchases use its ordinary undamaged state. */
    public static net.minecraft.world.item.ItemStack withoutDurability(net.minecraft.world.item.ItemStack source) {
        var stack = UtilityPowerItem.stripStoredValue(KleinStarItem.emptyCopy(source)).copyWithCount(1);
        restore(stack, new net.minecraft.world.item.ItemStack(source.getItem()),
                net.minecraft.core.component.DataComponents.DAMAGE);
        return stack;
    }

    /** Normalize configured properties and always ignore durability wear. */
    public static net.minecraft.world.item.ItemStack exemplar(net.minecraft.world.item.ItemStack source) {
        var stack = withoutDurability(source);
        var defaults = new net.minecraft.world.item.ItemStack(source.getItem());
        if (!customNames()) restore(stack, defaults, net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (!enchantments()) {
            restore(stack, defaults, net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            restore(stack, defaults, net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        }
        if (!potionEffects()) restore(stack, defaults, net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (!customData()) restore(stack, defaults, net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return stack;
    }
    private static <T> void restore(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.item.ItemStack defaults, net.minecraft.core.component.DataComponentType<T> type) {
        T value = defaults.get(type);
        if (value == null) stack.remove(type); else stack.set(type, value);
    }

    public static boolean enabled() { return preserveItemData; }
    public static boolean customNames() { return preserveItemData && preserveCustomNames; }
    public static boolean enchantments() { return preserveItemData && preserveEnchantments; }
    public static boolean potionEffects() { return preserveItemData && preservePotionEffects; }
    public static boolean customData() { return preserveItemData && preserveCustomData; }
}
