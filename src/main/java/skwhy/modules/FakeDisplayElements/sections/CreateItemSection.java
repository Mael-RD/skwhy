package skwhy.modules.FakeDisplayElements.sections;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.ItemDisplayData;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import skwhy.data.Vec3;
import skwhy.data.Quat4;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Name("Create Fake Item Display")
@Description("Creates a new fake item display entity using a configuration section. Accepted keys: " +
    "item/itemstack/material (ItemType, ItemStack, or Material), " +
    "head (String — sets item to PLAYER_HEAD and applies the skin value; mutually exclusive with item), " +
    "mode/displaymode (none/thirdperson_lefthand/thirdperson_righthand/firstperson_lefthand/firstperson_righthand/head/gui/ground/fixed, or 0–8), " +
    "scale/translation (Vector or Number), " +
    "rotation/leftrotation/rightrotation (Quaternionf), " +
    "glow (int RGB), shadow/shadowradius/radius, strength/shadowstrength, range/viewrange, " +
    "billboard (0–3), interpolation/interpolationduration, interpolationstart/interpolationdelay (Number).")
@Examples({
    "# Create a basic item display showing dirt",
    "set {_display} to [a new fake item display]:",
    "    item: dirt",
    "    scale: vector(1, 1, 1)",
    "    translation: vector(0, 0.5, 0)",
    "    billboard: 0",
    "    glow: 0",
    "    shadow: 0.5",
    "    strength: 1",
    "    range: 64",
    "    interpolation: 5",
    "    interpolationstart: 0",
    "    mode: head",
    "",
    "# Create a player head display using a skin value",
    "set {_display} to [a new fake item display]:",
    "    head: eyJ0ZXh0dXJlcy...",
    "    scale: vector(1, 1, 1)",
    "    mode: gui"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class CreateItemSection extends Section {

    private Expression<?> resultVar;
    private final Map<String, Expression<?>> fields = new LinkedHashMap<>();

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult,
                        @Nullable SectionNode sectionNode,
                        List<TriggerItem> triggerItems) {
        resultVar = exprs[0];

        if (sectionNode == null) {
            Skript.error("'new fake item display' nécessite une section de configuration.");
            return false;
        }

        for (Node node : sectionNode) {
            String raw = node.getKey();
            int colon = raw.indexOf(':');
            if (colon <= 0) continue;

            String key   = raw.substring(0, colon).toLowerCase().trim();
            String value = raw.substring(colon + 1).trim();

            Expression<?> expr = parseExpressionForKey(key, value);
            if (expr != null) {
                fields.put(key, expr);
            } else {
                Skript.error("Valeur invalide pour le champ '" + key + "' : " + value);
                return false;
            }
        }

        return true;
    }

    @Nullable
    private Expression<?> parseExpressionForKey(String key, String value) {
        int flags = SkriptParser.PARSE_EXPRESSIONS | SkriptParser.PARSE_LITERALS;
        SkriptParser parser = new SkriptParser(value, flags);

        return switch (key) {
            case "scale", "translation" ->
                parser.parseExpression(Vector.class, Number.class);
            case "rotation", "leftrotation", "rightrotation" ->
                parser.parseExpression(Quaternionf.class);
            case "glow", "shadow", "shadowradius", "radius",
                 "strength", "shadowstrength", "range", "viewrange",
                 "billboard", "interpolation", "interpolationduration",
                 "interpolationstart", "interpolationdelay" ->
                parser.parseExpression(Number.class);
            case "item", "itemstack", "material" ->
                parser.parseExpression(
                    ch.njol.skript.aliases.ItemType.class, 
                    org.bukkit.inventory.ItemStack.class, 
                    org.bukkit.Material.class, 
                    String.class
                );
            case "head" ->
                parser.parseExpression(String.class);
            case "mode", "displaymode" ->
                parser.parseExpression(String.class, Number.class);
            default ->
                parser.parseExpression(Object.class);
        };
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        ItemDisplayData display = new ItemDisplayData();
        
        // Vérifier que head et item ne coexistent pas
        boolean hasHead = fields.containsKey("head");
        boolean hasItem = fields.containsKey("item") || fields.containsKey("itemstack") || fields.containsKey("material");
        
        if (hasHead && hasItem) {
            Skript.error("Les champs 'head' et 'item' sont mutuellement exclusifs.");
            return getNext();
        }

        fields.forEach((key, expr) -> {
            Object value = expr.getSingle(event);
            if (value == null) return;

            switch (key) {
                case "scale" -> {
                    if (value instanceof Vector v) display.setScale(new Vec3(v));
                    else if (value instanceof Number n) display.setScale(new Vec3(n.floatValue()));
                }
                case "translation" -> {
                    if (value instanceof Vector v) display.setTranslation(new Vec3(v));
                    else if (value instanceof Number n) display.setTranslation(new Vec3(n.floatValue()));
                }
                case "rotation", "leftrotation" -> {
                    if (value instanceof Quaternionf q) display.setLeftRotation(new Quat4(q));
                }
                case "rightrotation" -> {
                    if (value instanceof Quaternionf q) display.setRightRotation(new Quat4(q));
                }
                case "glow" -> {
                    if (value instanceof Number n) display.setGlowColor(n.intValue());
                }
                case "shadow", "shadowradius", "radius" -> {
                    if (value instanceof Number n) display.setShadowRadius(n.floatValue());
                }
                case "strength", "shadowstrength" -> {
                    if (value instanceof Number n) display.setShadowStrength(n.floatValue());
                }
                case "range", "viewrange" -> {
                    if (value instanceof Number n) display.setViewRange(n.floatValue());
                }
                case "billboard" -> {
                    if (value instanceof Number n) display.setBillboardMode(n.intValue());
                }
                case "interpolation", "interpolationduration" -> {
                    if (value instanceof Number n) display.setInterpolationDuration(n.intValue());
                }
                case "interpolationstart", "interpolationdelay" -> {
                    if (value instanceof Number n) display.setInterpolationStart(n.intValue());
                }
                case "item", "itemstack", "material" -> {
                    // Convertit en majuscule et remplace les espaces par des tirets du bas si c'est parsé comme texte classique depuis Skript
                    if (value instanceof ch.njol.skript.aliases.ItemType it) {
                        display.setItemStack(it.getMaterial().name());
                    } else if (value instanceof org.bukkit.inventory.ItemStack is) {
                        display.setItemStack(is.getType().name());
                    } else if (value instanceof org.bukkit.Material m) {
                        display.setItemStack(m.name());
                    } else {
                        display.setItemStack(value.toString().toUpperCase().replace(" ", "_"));
                    }
                }
                case "head" -> {
                    // Si head est présent, forcer item à player_head
                    display.setItemStack("PLAYER_HEAD");
                    // Utiliser setHead pour définir la valeur de head
                    if (value instanceof String s) {
                        display.setHead(s);
                    }
                }
                case "mode", "displaymode" -> {
                    if (value instanceof Number n) {
                        display.setDisplayMode(Math.min(8, Math.max(0, n.intValue())));
                    } else {
                        display.setDisplayMode(parseDisplayMode(value.toString()));
                    }
                }
            }
        });

        resultVar.change(event, new Object[]{ display }, ChangeMode.SET);
        return getNext();
    }

    private int parseDisplayMode(String value) {
        return switch (value.toLowerCase().trim()) {
            case "none"                  -> 0;
            case "thirdperson_lefthand"  -> 1;
            case "thirdperson_righthand" -> 2;
            case "firstperson_lefthand"  -> 3;
            case "firstperson_righthand" -> 4;
            case "head"                  -> 5;
            case "gui"                   -> 6;
            case "ground"                -> 7;
            case "fixed"                 -> 8;
            default -> 0;
        };
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set " + resultVar.toString(event, debug) + " to new fake item display";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(CreateItemSection.class)
                .addPattern("set %~objects% to [a] [new] [fake] item display")
                .build()
        );
    }
}