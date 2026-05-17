package skwhy.modules.FakeDisplayElements.sections;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.config.EntryNode;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.registrations.Classes;
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

public class CreateTextSection extends Section {

    private Expression<?> resultVar;
    private final Map<String, String> fields = new LinkedHashMap<>();

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
            if (node instanceof EntryNode entry) {
                // Valeurs simples : block: oak_log, scale: 2, billboard: 0
                fields.put(
                    entry.getKey().toLowerCase().trim(),
                    entry.getValue().trim()
                );
            } else {
                String raw = node.getKey();
                int colon = raw.indexOf(':');
                if (colon > 0) {
                    fields.put(
                        raw.substring(0, colon).toLowerCase().trim(),
                        raw.substring(colon + 1).trim()
                    );
                }
            }
        }

        return true;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        TextDisplayData display = new TextDisplayData();

        fields.forEach((key, value) -> {
            switch (key) {
                case "scale" -> {
                    Vector v = Classes.parse(value, Vector.class, ParseContext.DEFAULT);
                    if (v != null) {
                        display.setScale(new Vec3(v));
                    } else {
                        Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                        if (n != null) display.setScale(new Vec3(n.floatValue()));
                        else Skript.warning("Valeur invalide pour 'scale' : " + value);
                    }
                }
                case "translation" -> {
                    Vector v = Classes.parse(value, Vector.class, ParseContext.DEFAULT);
                    if (v != null) {
                        display.setTranslation(new Vec3(v));
                    } else {
                        Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                        if (n != null) display.setTranslation(new Vec3(n.floatValue()));
                        else Skript.warning("Valeur invalide pour 'translation' : " + value);
                    }
                }
                case "rotation", "leftrotation" -> {
                    Quaternionf q = Classes.parse(value, Quaternionf.class, ParseContext.DEFAULT);
                    if (q != null) {
                        display.setLeftRotation(new Quat4(q));
                    } else {
                        // Format : "x, y, z, w"
                        String[] parts = value.split(",");
                        if (parts.length == 4) {
                            try {
                                display.setLeftRotation(
                                    Float.parseFloat(parts[0].trim()),
                                    Float.parseFloat(parts[1].trim()),
                                    Float.parseFloat(parts[2].trim()),
                                    Float.parseFloat(parts[3].trim())
                                );
                            } catch (NumberFormatException e) {
                                Skript.warning("Quaternion invalide pour 'rotation' : " + value);
                            }
                        }
                    }
                }
                case "rightrotation" -> {
                    Quaternionf q = Classes.parse(value, Quaternionf.class, ParseContext.DEFAULT);
                    if (q != null) {
                        display.setRightRotation(new Quat4(q));
                    } else {
                        // Format : "x, y, z, w"
                        String[] parts = value.split(",");
                        if (parts.length == 4) {
                            try {
                                display.setRightRotation(
                                    Float.parseFloat(parts[0].trim()),
                                    Float.parseFloat(parts[1].trim()),
                                    Float.parseFloat(parts[2].trim()),
                                    Float.parseFloat(parts[3].trim())
                                );
                            } catch (NumberFormatException e) {
                                Skript.warning("Quaternion invalide pour 'rightrotation' : " + value);
                            }
                        }
                    }
                }
                case "glow" -> {
                    Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                    if (n != null) display.setGlowColor(n.intValue());
                }
                case "shadow", "shadowradius", "radius" -> {
                    Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                    if (n != null) display.setShadowRadius(n.floatValue());
                }
                case "strength", "shadowstrength" -> {
                    Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                    if (n != null) display.setShadowStrength(n.floatValue());
                }
                case "range", "viewrange" -> {
                    Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                    if (n != null) display.setViewRange(n.floatValue());
                }
                case "billboard" -> {
                    Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                    if (n != null) display.setBillboardMode(n.intValue());
                }
                case "interpolation", "interpolationduration" -> {
                    Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                    if (n != null) display.setInterpolationDuration(n.intValue());
                }
                case "interpolationstart", "interpolationdelay" -> {
                    Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                    if (n != null) display.setInterpolationStart(n.intValue());
                }

                case "text" ->
                    display.setText(value);

                case "background", "backgroundcolor", "bgcolor" ->
                    display.setBackgroundColor(parseColor(value));

                case "seethrough", "see_through", "through" ->
                    display.setSeeThrough(parseBoolean(value));

                case "alignment", "align", "textalignment" ->
                    display.setTextAlignment(parseAlignment(value));

                case "linewidth", "width", "line_width" -> {
                    Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                    if (n != null) display.setLineWidth(n.intValue());
                }
                default -> Skript.warning("Champ inconnu ignoré : '" + key + "'");
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