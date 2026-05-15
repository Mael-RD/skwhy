package skwhy.modules.FakeDisplayElements.sections;

import ch.njol.skript.Skript;
import ch.njol.skript.config.EntryNode;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.registrations.Classes;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.TextDisplayData;
import ch.njol.skript.log.SkriptLogger;

import java.util.HashMap;
import java.util.Map;

public class CreateTextSection extends SimpleExpression<TextDisplayData> {

    private final Map<String, String> fields = new HashMap<>();

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        Node node = SkriptLogger.getNode();

        if (!(node instanceof SectionNode sectionNode)) {
            return true;
        }

        for (Node child : sectionNode) {
            if (child instanceof EntryNode entry) {
                String key   = entry.getKey().toLowerCase().trim();
                String value = entry.getValue().trim();
                fields.put(key, value);
            }
        }
        return true;
    }

    @Override
    protected @Nullable TextDisplayData[] get(Event event) {
        TextDisplayData display = new TextDisplayData();

        fields.forEach((key, value) -> {
            switch (key) {

                // ── Texte ─────────────────────────────────────────────────────
                case "text" ->
                    display.setText(value);

                // ── Couleur du texte (hex: "#FF0000" ou entier) ───────────────
                case "textcolor", "color", "textcolour" -> {
                    int color = parseColor(value);
                    display.setTextColor(color);
                }

                // ── Couleur de fond ───────────────────────────────────────────
                case "background", "backgroundcolor", "bgcolor" -> {
                    int color = parseColor(value);
                    display.setBackgroundColor(color);
                }

                // ── Fond par défaut ───────────────────────────────────────────
                case "defaultbackground", "defaultbg" -> {
                    boolean b = parseBoolean(value);
                    display.setDefaultBackground(b);
                }

                // ── Contour ───────────────────────────────────────────────────
                case "outline", "shadow_text" -> {
                    boolean b = parseBoolean(value);
                    display.setOutline(b);
                }

                // ── See through ───────────────────────────────────────────────
                case "seethrough", "see_through", "through" -> {
                    boolean b = parseBoolean(value);
                    display.setSeeThrough(b);
                }

                // ── Alignement ────────────────────────────────────────────────
                // Accepte "center"/"left"/"right" ou 0/1/2
                case "alignment", "align", "textalignment" -> {
                    int align = parseAlignment(value);
                    display.setTextAlignment(align);
                }

                // ── Largeur de ligne ──────────────────────────────────────────
                case "linewidth", "width", "line_width" -> {
                    Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                    if (n != null) display.setLineWidth(n.intValue());
                }

                // ── Scale ─────────────────────────────────────────────────────
                case "scale" -> {
                    org.bukkit.util.Vector v = Classes.parse(value, org.bukkit.util.Vector.class, ParseContext.DEFAULT);
                    if (v != null) {
                        display.setScale((float) v.getX(), (float) v.getY(), (float) v.getZ());
                    } else {
                        Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                        if (n != null) display.setScale(n.floatValue(), n.floatValue(), n.floatValue());
                        else Skript.warning("Valeur invalide pour 'scale' : " + value);
                    }
                }

                // ── Propriétés communes ───────────────────────────────────────
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

                default -> Skript.warning("Champ inconnu ignoré : '" + key + "'");
            }
        });

        return new TextDisplayData[]{ display };
    }

    // ── Utilitaires de parsing ─────────────────────────────────────────────────

    /**
     * Parse une couleur depuis "#RRGGBB", "0xRRGGBB" ou un entier brut.
     */
    private int parseColor(String value) {
        String v = value.trim();
        try {
            if (v.startsWith("#"))  return Integer.parseInt(v.substring(1), 16);
            if (v.startsWith("0x") || v.startsWith("0X")) return Integer.parseInt(v.substring(2), 16);
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            Skript.warning("Couleur invalide : '" + value + "', utilisation de blanc (#FFFFFF)");
            return 0xFFFFFF;
        }
    }

    /**
     * Parse un booléen depuis "true"/"false"/"yes"/"no"/"1"/"0".
     */
    private boolean parseBoolean(String value) {
        return switch (value.toLowerCase().trim()) {
            case "true", "yes", "1", "on" -> true;
            default -> false;
        };
    }

    /**
     * Parse l'alignement depuis "center"/"left"/"right" ou 0/1/2.
     */
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
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends TextDisplayData> getReturnType() { return TextDisplayData.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "new text display section";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CreateTextSection.class, TextDisplayData.class)
                .addPattern("[a] [new] [fake] text display")
                .build()
        );
    }
}