package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.DisplayData;

@Name("Display Entity ID / UUID")
@Description("Returns the numeric entity ID or the UUID string of fake display entities. The entity ID is used for packet operations; the UUID is used for persistent identification. Targets can be one or more displays or a display group.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to [a new fake item display]:",
    "    set item of display to dirt",
    "",
    "# Get the numeric entity ID",
    "set {_id} to fake display entity id of {_display}",
    "",
    "# Get the UUID string",
    "set {_uuid} to fake display entity uuid of {_display}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayId extends SimpleExpression<Object> {

    // 0 → entity id  (Number)
    // 1 → uuid       (String)
    private int matchedPattern;
    private Expression<?> targets;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.targets = exprs[0];
        return true;
    }

    @Override
    protected @Nullable Object[] get(Event event) {
        Object value = targets.getSingle(event);
        if (!(value instanceof DisplayData display)) return null;
        return switch (matchedPattern) {
            case 0 -> new Number[]{ display.getEntityId() };
            case 1 -> new String[]{ display.getEntityUUID().toString() };
            default -> null;
        };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<?> getReturnType() {
        return matchedPattern == 0 ? Number.class : String.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return (matchedPattern == 0 ? "entity id" : "uuid")
            + " of " + targets.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayId.class, Object.class)
                .addPattern("[the] fake display entity id of %displaydata%")   // 0
                .addPattern("[the] fake display entity uuid of %displaydata%") // 1
                .build()
        );
    }
}