package skwhy.modules.FakeDisplayElements.sections;

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
import skwhy.data.BlockDisplayData;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import skwhy.data.Vec3;
import skwhy.data.Quat4;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CreateBlockSection extends Section {

    private Expression<?> resultVar;

    // Stocke les expressions parsées plutôt que des strings brutes
    private final Map<String, Expression<?>> fields = new LinkedHashMap<>();

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult,
                        @Nullable SectionNode sectionNode,
                        List<TriggerItem> triggerItems) {
        resultVar = exprs[0];

        if (sectionNode == null) {
            Skript.error("'new fake block display' nécessite une section de configuration.");
            return false;
        }

        for (Node node : sectionNode) {
            // Récupère la ligne brute que ce soit un EntryNode ou un SimpleNode
            String raw = node.getKey();
            int colon = raw.indexOf(':');
            if (colon <= 0) continue;

            String key   = raw.substring(0, colon).toLowerCase().trim();
            String value = raw.substring(colon + 1).trim();

            // Parse la valeur comme une vraie expression Skript
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

    /**
     * Parse la valeur avec le type Skript approprié selon la clé.
     */
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
            case "block" ->parser.parseExpression(
                ch.njol.skript.aliases.ItemType.class, 
                org.bukkit.inventory.ItemStack.class, 
                org.bukkit.block.data.BlockData.class, 
                org.bukkit.Material.class, 
                String.class
            );
            default ->
                parser.parseExpression(Object.class);
        };
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        BlockDisplayData display = new BlockDisplayData();

        fields.forEach((key, expr) -> {
            Object value = expr.getSingle(event);
            if (value == null) return;

            switch (key) {
                case "block" ->
                    display.setBlockData(value.toString());

                case "scale" -> {
                    if (value instanceof Vector v)
                        display.setScale(new Vec3(v));
                    else if (value instanceof Number n)
                        display.setScale(new Vec3(n.floatValue()));
                }
                case "translation" -> {
                    if (value instanceof Vector v)
                        display.setTranslation(new Vec3(v));
                    else if (value instanceof Number n)
                        display.setTranslation(new Vec3(n.floatValue()));
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
            }
        });

        resultVar.change(event, new Object[]{ display }, ChangeMode.SET);
        return getNext();
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set " + resultVar.toString(event, debug) + " to new fake block display";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(CreateBlockSection.class)
                .addPattern("set %~objects% to [a] [new] [fake] block display")
                .build()
        );
    }
}