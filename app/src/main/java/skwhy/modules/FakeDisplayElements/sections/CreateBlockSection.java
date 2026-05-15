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
import skwhy.data.BlockDisplayData;
import ch.njol.skript.log.SkriptLogger;

import java.util.HashMap;
import java.util.Map;

public class CreateBlockSection extends SimpleExpression<BlockDisplayData> {
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
    protected @Nullable BlockDisplayData[] get(Event event) {
        BlockDisplayData display = new BlockDisplayData();

        fields.forEach((key, value) -> {
            switch (key) {
                case "block" -> display.setBlockData(value);

                case "scale" -> {
                    org.bukkit.util.Vector v = Classes.parse(value, org.bukkit.util.Vector.class, ParseContext.DEFAULT);
                    if (v != null) display.setScale((float) v.getX(), (float) v.getY(), (float) v.getZ());
                    else {
                        // Fallback : valeur unique appliquée aux 3 axes (ex: "scale: 2")
                        Number n = Classes.parse(value, Number.class, ParseContext.DEFAULT);
                        if (n != null) display.setScale(n.floatValue(), n.floatValue(), n.floatValue());
                        else Skript.warning("Valeur invalide pour 'scale' : " + value);
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
                default -> Skript.warning("Champ inconnu ignoré : '" + key + "'");
            }
        });

        return new BlockDisplayData[]{ display };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends BlockDisplayData> getReturnType() { return BlockDisplayData.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "new block display section";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CreateBlockSection.class, BlockDisplayData.class)
                .addPattern("[a] [new] [fake] block display")
                .build()
        );
    }
}