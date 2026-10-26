package skwhy.modules.CustomEntityElements.expressions;

import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

@Name("[Custom Entity] Definition Number Parameters")
@Description("Reads or writes a number parameter of a custom entity definition, in its YAML file: 'scale' of the look (above 0, up to 16), " +
    "and the width and height of the 'hitbox' of the interaction that receives the clicks (above 0; real entities are resized too). " +
    "Deleting a parameter gives it back its default value (scale 1, hitbox 0.6 x 1.8). A value that makes the definition invalid is refused and nothing is written.")
@Examples({
    "set custom entity scale of definition \"example_wolf\" to 2",
    "set custom entity hitbox width of definition \"example_wolf\" to 1.2",
    "set custom entity hitbox height of definition \"example_wolf\" to 1.5",
    "reset custom entity scale of definition \"example_wolf\""
})
@Since("1.6.0")
public class ExprDefParamNumber extends ExprDefParam<Number> {

    private static final String[] PATHS = { "scale", "hitbox.width", "hitbox.height" };

    public ExprDefParamNumber() {
        super(Number.class);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprDefParamNumber.class, Number.class)
                .addPattern("custom entity scale of definition %string%")
                .addPattern("custom entity hitbox width of definition %string%")
                .addPattern("custom entity hitbox height of definition %string%")
                .build()
        );
    }

    @Override
    protected String[] paths() { return PATHS; }
}
