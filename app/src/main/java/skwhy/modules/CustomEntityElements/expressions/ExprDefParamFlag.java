package skwhy.modules.CustomEntityElements.expressions;

import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

@Name("[Custom Entity] Definition Boolean Parameters")
@Description("Reads or writes a true / false parameter of a custom entity definition, in its YAML file: whether the name is visible " +
    "(default true), glowing, invisible, silent (default true) and on fire (default false). " +
    "Deleting a parameter gives it back its default value.")
@Examples({
    "set custom entity glowing of definition \"example_wolf\" to true",
    "set custom entity name visible of definition \"example_wolf\" to false",
    "set custom entity silent of definition \"example_wolf\" to false",
    "reset custom entity invisible of definition \"example_wolf\""
})
@Since("1.6.0")
public class ExprDefParamFlag extends ExprDefParam<Boolean> {

    private static final String[] PATHS = { "name_visible", "glowing", "invisible", "silent", "on_fire" };

    public ExprDefParamFlag() {
        super(Boolean.class);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprDefParamFlag.class, Boolean.class)
                .addPattern("custom entity name visible of definition %string%")
                .addPattern("custom entity glowing of definition %string%")
                .addPattern("custom entity invisible of definition %string%")
                .addPattern("custom entity silent of definition %string%")
                .addPattern("custom entity on fire of definition %string%")
                .build()
        );
    }

    @Override
    protected String[] paths() { return PATHS; }
}
