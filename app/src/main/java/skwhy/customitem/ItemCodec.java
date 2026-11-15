package skwhy.customitem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import com.mojang.serialization.DataResult;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import skwhy.quest.data.PlayerNbt;

/**
 * Conversion entre un {@link ItemStack} et l'arbre YAML d'un item custom (voir {@link CustomItemDef}). Seule classe NMS du
 * module : elle passe par le codec vanilla de l'item, donc tous les composants sont conservés ; ceux qui ont une clé
 * lisible (voir {@link #KEYS}) sont renommés, les autres restent dans {@code components}.
 */
public final class ItemCodec {

    private static final String NS = "minecraft:";

    /** Clé lisible → composant vanilla. */
    private static final Map<String, String> KEYS = new LinkedHashMap<>();

    static {
        KEYS.put("data", "custom_data");
        KEYS.put("stack", "max_stack_size");
        KEYS.put("unbreakable", "unbreakable");
        KEYS.put("glint", "enchantment_glint_override");
        KEYS.put("model", "item_model");
        KEYS.put("color", "dyed_color");
        KEYS.put("attributes", "attribute_modifiers");
        KEYS.put("enchantments", "enchantments");
        KEYS.put("potion", "potion_contents");
        KEYS.put("tooltip_display", "tooltip_display");
        KEYS.put("equippable", "equippable");
        KEYS.put("consumable", "consumable");
        KEYS.put("use_cooldown", "use_cooldown");
        KEYS.put("fireworks", "fireworks");
        KEYS.put("swing_animation", "swing_animation");
    }

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    /** Ancien format des fichiers : {@code &6}, {@code &l}, {@code &#RRGGBB} (une couleur remet les formats à zéro, comme en jeu). */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final java.util.regex.Pattern MINI_TAG = java.util.regex.Pattern.compile("<(/?[a-zA-Z_]+|[!/#][^>]*)>");

    private ItemCodec() {}

    private static RegistryOps<Tag> ops() {
        RegistryAccess access = CraftRegistry.getMinecraftRegistry();
        return access.createSerializationContext(NbtOps.INSTANCE);
    }

    /** Arbre YAML d'un item (le nombre d'exemplaires n'est pas conservé), sans {@code version}. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toTree(ItemStack stack) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack.asOne());
        Tag tag = net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops(), nms).getOrThrow();
        Map<String, Object> raw = (Map<String, Object>) PlayerNbt.toTree(tag);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("material_id", stripNs(String.valueOf(raw.get("id"))));

        Component name = stack.getData(DataComponentTypes.CUSTOM_NAME);
        if (name != null) out.put("name", writeText(name));
        ItemLore lore = stack.getData(DataComponentTypes.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            List<Object> lines = new ArrayList<>();
            for (Component line : lore.lines()) lines.add(writeText(line));
            out.put("lore", lines);
        }

        Object comps = raw.get("components");
        Map<String, Object> components = comps instanceof Map<?, ?> ? new LinkedHashMap<>((Map<String, Object>) comps) : new LinkedHashMap<>();
        components.remove(NS + "custom_name");
        components.remove(NS + "lore");
        for (Map.Entry<String, String> e : KEYS.entrySet()) {
            Object value = components.remove(NS + e.getValue());
            if (value != null) out.put(e.getKey(), value);
        }
        if (!components.isEmpty()) out.put("components", components);
        return out;
    }

    /**
     * Fabrique un item (1 exemplaire) à partir de l'arbre d'une définition.
     *
     * @return l'item, ou {@code null} si le matériau ou un composant est invalide
     */
    public static ItemStack fromTree(Map<String, Object> tree) {
        CompoundTag root = root(tree);
        if (root == null) return null;
        DataResult<net.minecraft.world.item.ItemStack> parsed = net.minecraft.world.item.ItemStack.CODEC.parse(ops(), root);
        net.minecraft.world.item.ItemStack nms = parsed.result().orElse(null);
        if (nms == null || nms.isEmpty()) return null;
        ItemStack stack = CraftItemStack.asBukkitCopy(nms);

        if (tree.get("name") instanceof String name) stack.setData(DataComponentTypes.CUSTOM_NAME, noItalic(parseText(name)));
        if (tree.get("lore") instanceof List<?> lines) {
            List<Component> parts = new ArrayList<>();
            for (Object line : lines) parts.add(noItalic(parseText(String.valueOf(line))));
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(parts));
        }
        return stack;
    }

    /** Message d'erreur du codec pour cet arbre, ou {@code null} s'il est valide. */
    public static String check(Map<String, Object> tree) {
        CompoundTag root = root(tree);
        if (root == null) return "material_id manquant";
        return net.minecraft.world.item.ItemStack.CODEC.parse(ops(), root).error().map(DataResult.Error::message).orElse(null);
    }

    /** {@code {id, count, components}} du codec vanilla, ou {@code null} sans {@code material_id}. */
    @SuppressWarnings("unchecked")
    private static CompoundTag root(Map<String, Object> tree) {
        if (!(tree.get("material_id") instanceof String id) || id.isBlank()) return null;
        CompoundTag root = new CompoundTag();
        String lower = id.trim().toLowerCase(java.util.Locale.ROOT);
        root.putString("id", lower.contains(":") ? lower : NS + lower);
        root.putInt("count", 1);
        CompoundTag components = new CompoundTag();
        if (tree.get("components") instanceof Map<?, ?> raw) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) raw).entrySet()) components.put(e.getKey(), toTag(e.getValue()));
        }
        for (Map.Entry<String, String> e : KEYS.entrySet()) {
            if (tree.containsKey(e.getKey())) components.put(NS + e.getValue(), toTag(tree.get(e.getKey())));
        }
        if (!components.isEmpty()) root.put("components", components);
        return root;
    }

    /** Retire de {@code data} les tags propres à un exemplaire (id, version, propriétaire, drapeaux) : ils ne font pas partie d'une définition. */
    @SuppressWarnings("unchecked")
    public static void scrubInstanceTags(Map<String, Object> tree) {
        if (!(tree.get("data") instanceof Map<?, ?> raw)) return;
        Map<String, Object> data = (Map<String, Object>) raw;
        data.remove("item");
        data.remove("version");
        data.remove("owner");
        for (String flag : CustomItemIdentity.FLAGS) data.remove(flag);
        if (data.isEmpty()) tree.remove("data");
    }

    /**
     * Texte d'un nom / d'une ligne de lore : format {@code &} par défaut ; un fichier plus ancien en balises MiniMessage est lu avec
     * MiniMessage <em>standard</em> (le parseur de Skript remet les formats à zéro à chaque couleur : {@code <bold><red>x} perdrait le gras).
     */
    static Component parseText(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (MINI_TAG.matcher(text).find()) {
            try {
                return MINI.deserialize(text);
            } catch (RuntimeException e) {
                // balises invalides : lecture en ancien format
            }
        }
        return LEGACY.deserialize(text.replace('§', '&'));
    }

    /** Ancien format {@code &#RRGGBB&lTexte} (sans les décorations explicitement fausses). */
    static String writeText(Component component) {
        return LEGACY.serialize(component);
    }

    private static Component noItalic(Component component) {
        return component.decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC, net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    private static String stripNs(String id) {
        return id.startsWith(NS) ? id.substring(NS.length()) : id;
    }

    /** Un booléen YAML est un octet, un entier un {@code int} (le codec convertit vers le type attendu), un décimal un {@code double}. */
    @SuppressWarnings("unchecked")
    private static Tag toTag(Object value) {
        if (value instanceof Map<?, ?> map) {
            CompoundTag out = new CompoundTag();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) map).entrySet()) out.put(e.getKey(), toTag(e.getValue()));
            return out;
        }
        if (value instanceof List<?> list) {
            ListTag out = new ListTag();
            for (Object o : list) out.add(toTag(o));
            return out;
        }
        if (value instanceof Boolean b) return ByteTag.valueOf(b);
        if (value instanceof Long l) return LongTag.valueOf(l);
        if (value instanceof Double || value instanceof Float) return DoubleTag.valueOf(((Number) value).doubleValue());
        if (value instanceof Number n) return IntTag.valueOf(n.intValue());
        return StringTag.valueOf(String.valueOf(value));
    }
}
