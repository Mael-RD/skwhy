package skwhy.modules.CustomEntityElements.expressions;

import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

@Name("[Custom Entity] Definition Text Parameters")
@Description("Reads or writes a text parameter of a custom entity definition, in its YAML file (comments of the file are not kept). " +
    "'type' is the entity type of the look (wolf, mannequin... it must exist, and it cannot be deleted); 'name' is the displayed name (<green>, &a... accepted); " +
    "'skin value' and 'skin signature' are the base64 texture of a mannequin; 'equipment <slot>' is the item of main_hand, off_hand, head, chest, legs, feet or body; " +
    "'metadata <index>' is a raw entity metadata written as type:value, the type being byte, int, long, float, string or boolean (for example byte:1). " +
    "Deleting a parameter gives it back its default value. A value that makes the definition invalid is refused and nothing is written. " +
    "The entities that use the definition are updated for the players who see them.")
@Examples({
    "set custom entity type of definition \"example_wolf\" to \"fox\"",
    "set custom entity name of definition \"example_wolf\" to \"<green>Renard\"",
    "set custom entity equipment \"head\" of definition \"example_wolf\" to \"golden_helmet\"",
    "set custom entity metadata 15 of definition \"example_wolf\" to \"byte:1\"",
    "delete custom entity name of definition \"example_wolf\"",
    "send custom entity type of definition \"example_wolf\" to player"
})
@Since("1.6.0")
public class ExprDefParamText extends ExprDefParam<String> {

    private static final String[] PATHS = { "type", "name", "skin.value", "skin.signature", "equipment.", "metadata." };

    public ExprDefParamText() {
        super(String.class);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprDefParamText.class, String.class)
                .addPattern("custom entity type of definition %string%")
                .addPattern("custom entity name of definition %string%")
                .addPattern("custom entity skin value of definition %string%")
                .addPattern("custom entity skin signature of definition %string%")
                .addPattern("custom entity equipment %string% of definition %string%")
                .addPattern("custom entity metadata %number% of definition %string%")
                .build()
        );
    }

    @Override
    protected String[] paths() { return PATHS; }
}
