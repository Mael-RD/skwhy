package skwhy.modules.CustomEntityElements.effects;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityManager;

@Name("[Custom Entity] Destroy / Reload Custom Entities / Definitions")
@Description("'destroy custom entity' removes custom entities: a real one is removed from the world, a fake one disappears for every player. " +
    "'reload custom entities' reads the files of the custom-entities folder again (the file 'example.yml' is written the first time) " +
    "and updates what the players see; a custom entity whose id is no longer defined is hidden until it is defined again. " +
    "'create custom entity definition' adds a definition with only its type to the file created.yml of the folder (nothing happens if the id exists or the type is unknown), " +
    "and 'delete custom entity definition' removes it from its file (the entities that use it are hidden). Change the other parameters with the 'custom entity <parameter> of definition' expressions.")
@Examples({
    "destroy custom entity {_pet}",
    "destroy all fake custom entities",
    "reload custom entities",
    "create custom entity definition \"chat\" with type \"cat\"",
    "delete custom entity definition \"chat\""
})
@Since("1.6.0")
public class EffCustomEntity extends Effect {

    private int mode;
    private Expression<CustomEntity> entities;
    private Expression<String> idExpr;
    private Expression<String> typeExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffCustomEntity.class)
                .addPattern("destroy custom entit(y|ies) %customentities%")
                .addPattern("destroy %customentities%")
                .addPattern("reload [all] custom entities")
                .addPattern("create custom entity definition %string% with type %string%")
                .addPattern("delete custom entity definition %string%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        mode = matchedPattern;
        if (mode <= 1) entities = (Expression<CustomEntity>) exprs[0];
        if (mode >= 3) idExpr = (Expression<String>) exprs[0];
        if (mode == 3) typeExpr = (Expression<String>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        if (manager == null) return;
        switch (mode) {
            case 2 -> manager.reload();
            case 3 -> {
                String id = idExpr.getSingle(event);
                String type = typeExpr.getSingle(event);
                if (id != null && type != null) manager.createDefinition(id, type);
            }
            case 4 -> {
                String id = idExpr.getSingle(event);
                if (id != null) manager.deleteDefinition(id);
            }
            default -> {
                for (CustomEntity entity : entities.getArray(event)) manager.destroy(entity);
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (mode) {
            case 2 -> "reload custom entities";
            case 3 -> "create custom entity definition " + idExpr.toString(event, debug) + " with type " + typeExpr.toString(event, debug);
            case 4 -> "delete custom entity definition " + idExpr.toString(event, debug);
            default -> "destroy " + entities.toString(event, debug);
        };
    }
}
