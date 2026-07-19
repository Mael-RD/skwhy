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

import org.joml.Quaternionf;
import org.bukkit.util.Vector;
import skwhy.data.Quat4;
import skwhy.data.Vec3;
import skwhy.data.TextDisplayData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Name("Create Fake Text Display")
@Description("Creates a new fake text display entity using a configuration section. Accepted keys: " +
    "text (String), " +
    "seethrough/see_through/through (Boolean), " +
    "alignment/align/textalignment (center/left/right or 0/1/2), " +
    "background/backgroundcolor/bgcolor (Color, int RGB, or hex string), " +
    "linewidth/width/line_width (Number), " +
    "scale/translation (Vector or Number), " +
    "rotation/leftrotation/rightrotation (Quaternionf), " +
    "glow (int RGB), shadow/shadowradius/radius, strength/shadowstrength, range/viewrange, " +
    "billboard (0–3), interpolation/interpolationduration, interpolationstart/interpolationdelay (Number).")
@Examples({
    "set {_display} to [a new fake text display]:",
    "    text: \"&aHello world!\"",
    "    seethrough: true",
    "    alignment: center",
    "    background: 0",
    "    linewidth: 200",
    "    scale: vector(1, 1, 1)",
    "    translation: vector(0, 0.5, 0)",
    "    billboard: 3",
    "    glow: 0",
    "    shadow: 0.5",
    "    strength: 1",
    "    range: 64",
    "    interpolation: 5",
    "    interpolationstart: 0",
    "    rotation: {_quaternion}",
    "    rightrotation: {_quaternion}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class CreateTextSection extends Section {

    private Expression<?> resultVar;
    private final Map<String, Expression<?>> fields = new LinkedHashMap<>();

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult,
                        @Nullable SectionNode sectionNode,
                        List<TriggerItem> triggerItems) {
        resultVar = exprs[0];

        if (sectionNode == null) {
            Skript.error("'new fake text display' nécessite une section de configuration.");
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
                 "interpolationstart", "interpolationdelay",
                 "linewidth", "width", "line_width" ->
                parser.parseExpression(Number.class);
            case "text" -> 
                parser.parseExpression(String.class);
            case "seethrough", "see_through", "through" -> 
                parser.parseExpression(Boolean.class, String.class);
            case "alignment", "align", "textalignment" -> 
                parser.parseExpression(String.class, Number.class);
            case "background", "backgroundcolor", "bgcolor" -> 
                parser.parseExpression(ch.njol.skript.util.Color.class, org.bukkit.Color.class, String.class, Number.class);
            default ->
                parser.parseExpression(Object.class);
        };
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        TextDisplayData display = new TextDisplayData();

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
                case "text" -> {
                    display.setText(value.toString());
                }
                case "background", "backgroundcolor", "bgcolor" -> {
                    if (value instanceof Number n) {
                        display.setBackgroundColor(n.intValue());
                    } else if (value instanceof ch.njol.skript.util.Color c) {
                        display.setBackgroundColor(c.asBukkitColor().asRGB());
                    } else if (value instanceof org.bukkit.Color c) {
                        display.setBackgroundColor(c.asRGB());
                    } else {
                        display.setBackgroundColor(parseColor(value.toString()));
                    }
                }
                case "seethrough", "see_through", "through" -> {
                    if (value instanceof Boolean b) {
                        display.setSeeThrough(b);
                    } else {
                        display.setSeeThrough(parseBoolean(value.toString()));
                    }
                }
                case "alignment", "align", "textalignment" -> {
                    if (value instanceof Number n) {
                        display.setTextAlignment(n.intValue());
                    } else {
                        display.setTextAlignment(parseAlignment(value.toString()));
                    }
                }
                case "linewidth", "width", "line_width" -> {
                    if (value instanceof Number n) display.setLineWidth(n.intValue());
                }
            }
        });

        resultVar.change(event, new Object[]{ display }, ChangeMode.SET);
        return getNext();
    }

    private int parseColor(String value) {
        String v = value.trim();
        try {
            if (v.startsWith("#"))                      return Integer.parseInt(v.substring(1), 16);
            if (v.startsWith("0x") || v.startsWith("0X")) return Integer.parseInt(v.substring(2), 16);
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            Skript.warning("Couleur invalide : '" + value + "', utilisation de blanc (#FFFFFF)");
            return 0xFFFFFF;
        }
    }

    private boolean parseBoolean(String value) {
        return switch (value.toLowerCase().trim()) {
            case "true", "yes", "1", "on" -> true;
            default -> false;
        };
    }

    private int parseAlignment(String value) {
        return switch (value.toLowerCase().trim()) {
            case "center", "0" -> 0;
            case "left",   "1" -> 1;
            case "right",  "2" -> 2;
            default -> {
                Skript.warning("Alignement invalide : '" + value + "', utilisation de 'center'");
                yield 0;
            }
        };
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set " + resultVar.toString(event, debug) + " to new fake text display";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(CreateTextSection.class)
                .addPattern("set %~objects% to [a] [new] [fake] text display")
                .build()
        );
    }
}