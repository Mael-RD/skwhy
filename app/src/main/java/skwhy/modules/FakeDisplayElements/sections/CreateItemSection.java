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
import skwhy.data.ItemDisplayData;
import ch.njol.skript.log.SkriptLogger;

import java.util.HashMap;
import java.util.Map;

public class CreateItemSection extends SimpleExpression<ItemDisplayData> {

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
    protected @Nullable ItemDisplayData[] get(Event event) {
        ItemDisplayData display = new ItemDisplayData();

        fields.forEach((key, value) -> {
            switch (key) {

                // ── Item ──────────────────────────────────────────────────────
                case "item", "itemstack", "material" ->
                    display.setItemStack(value.toUpperCase());

                // ── Mode d'affichage ──────────────────────────────────────────
                // Accepte le nom ("gui", "head"...) ou un entier (0-8)
                case "mode", "displaymode" -> {
                    int mode = parseDisplayMode(value);
                    display.setDisplayMode(mode);
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

        return new ItemDisplayData[]{ display };
    }

    /**
     * Accepte le nom textuel du mode ("gui", "head"...) ou un entier direct.
     */
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
            default -> {
                Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                yield (n != null) ? Math.min(8, Math.max(0, n.intValue())) : 0;
            }
        };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends ItemDisplayData> getReturnType() { return ItemDisplayData.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "new item display section";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CreateItemSection.class, ItemDisplayData.class)
                .addPattern("[a] [new] [fake] item display")
                .build()
        );
    }
}