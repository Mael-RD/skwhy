package skwhy.modules.CustomEntityElements.sections;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.util.SimpleLiteral;
import ch.njol.util.Kleenean;
import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityDef;
import skwhy.customentity.CustomEntityManager;

@Name("[Custom Entity] Create Custom Entity With Overrides")
@Description("Creates a real or fake custom entity from a definition and overrides some of its parameters for this entity only, " +
    "so a unique case does not need a new definition. Each line is 'parameter: expression' and the expression is parsed like anywhere else in Skript " +
    "(variables, functions, vectors...). The parameters are the ones of a definition: " +
    "type (text), ai (text: type of the mob whose vanilla AI drives the entity, e.g. zombie, villager, wolf), name (text, colors like <green>), name_visible (boolean), " +
    "scale (number above 0, up to 16), glowing/invisible/silent/on_fire (boolean), hitbox_width/hitbox_height (numbers above 0), skin_value/skin_signature (text), " +
    "an equipment slot (main_hand, off_hand, head, chest, legs, feet, body: a material name) and metadata_<index> or a 'metadata:' sub-section with '<index>: type:value' lines. " +
    "'hitbox', 'skin' and 'equipment' can also be written as sub-sections ('width: 1' under 'hitbox:'). " +
    "A text that is a single word (zombie, diamond_helmet, byte:1) can be written without quotes. A real entity keeps its overrides in its NBT, a fake one in memory. " +
    "The new entity is put in the variable; it stays unset when the id is not defined. A value that cannot be applied is ignored with a warning in the console. " +
    "Overrides can be changed afterwards with 'custom entity parameter ... of ...'.")
@Examples({
    "set {_gardien} to a new real custom entity \"example_wolf\" at location of player:",
    "\tscale: 2.5",
    "\tname: \"<red>Gardien de %player%\"",
    "\tglowing: true",
    "\tai: zombie",
    "\thead: diamond_helmet",
    "\thitbox:",
    "\t\twidth: 1.2",
    "\t\theight: 2",
    "set {_decor} to a new fake custom entity \"example_mannequin\" at location of player:",
    "\tscale: {_taille} * 1.5",
    "\tname_visible: false"
})
@Since("1.6.0")
public class SecNewCustomEntity extends Section {

    /** Un mot sans espace ni guillemet : id d'entité, matériau, metadata {@code byte:1}... */
    private static final Pattern BARE_WORD = Pattern.compile("[A-Za-z0-9_:./+=-]+");

    private boolean fake;
    private Expression<?> resultVar;
    private Expression<String> idExpr;
    private Expression<Location> locationExpr;
    private final Map<String, Expression<?>> overrides = new LinkedHashMap<>();

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(SecNewCustomEntity.class)
                .addPattern("set %~objects% to [a] [new] (1¦real|2¦fake) custom entity %string% at %location%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult,
                        @Nullable SectionNode sectionNode, List<TriggerItem> triggerItems) {
        resultVar = exprs[0];
        idExpr = (Expression<String>) exprs[1];
        locationExpr = (Expression<Location>) exprs[2];
        fake = parseResult.mark == 2;
        if (sectionNode == null) {
            Skript.error("'new custom entity' avec des surcharges nécessite une section ('parameter: valeur').");
            return false;
        }
        return readNodes(sectionNode, "");
    }

    private boolean readNodes(SectionNode section, String prefix) {
        for (Node node : section) {
            String key = node.getKey();
            if (key == null) continue;
            if (node instanceof SectionNode child) {
                String name = normalize(key);
                if (!readNodes(child, (prefix.isEmpty() ? "" : prefix + ".") + name)) return false;
                continue;
            }
            int colon = key.indexOf(':');
            if (colon <= 0) {
                Skript.error("Ligne invalide '" + key + "' : 'paramètre: valeur' attendu.");
                return false;
            }
            String path = pathOf(prefix, normalize(key.substring(0, colon)));
            String value = key.substring(colon + 1).trim();
            String kind = CustomEntityDef.kindOf(path);
            if (kind == null) {
                Skript.error("Paramètre inconnu '" + path + "' (attendus : " + String.join(", ", CustomEntityDef.fixedPaths())
                    + ", equipment.<slot>, metadata.<index>).");
                return false;
            }
            if (overrides.containsKey(path)) {
                Skript.error("Le paramètre '" + path + "' est écrit deux fois.");
                return false;
            }
            Expression<?> expr = parseValue(path, kind, value);
            if (expr == null) {
                Skript.error("Valeur invalide pour '" + path + "' : " + value);
                return false;
            }
            overrides.put(path, expr);
        }
        return true;
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    /** Chemin YAML d'une clé, avec les écritures courtes ({@code hitbox_width}, {@code head}, {@code onfire}...). */
    private static String pathOf(String prefix, String key) {
        if (!prefix.isEmpty()) return prefix + "." + key;
        return switch (key) {
            case "hitbox_width" -> "hitbox.width";
            case "hitbox_height" -> "hitbox.height";
            case "skin_value" -> "skin.value";
            case "skin_signature" -> "skin.signature";
            case "namevisible" -> "name_visible";
            case "onfire" -> "on_fire";
            default -> {
                if (CustomEntityDef.equipmentSlots().contains(key)) yield "equipment." + key;
                if (key.startsWith("metadata_")) yield "metadata." + key.substring("metadata_".length());
                yield key;
            }
        };
    }

    /**
     * Parse la valeur comme une expression Skript du bon type. Un texte qui n'est qu'un mot sans guillemets
     * (type, matériau, metadata) reste littéral : sinon Skript le prendrait pour un autre type de littéral.
     */
    private @Nullable Expression<?> parseValue(String path, String kind, String value) {
        if (value.isEmpty()) return null;
        boolean identifier = path.equals("type") || path.equals("ai") || path.startsWith("equipment.")
            || path.startsWith("metadata.") || path.startsWith("skin.");
        if (kind.equals("text") && identifier && BARE_WORD.matcher(value).matches()) {
            return new SimpleLiteral<>(value, false);
        }
        SkriptParser parser = new SkriptParser(value, SkriptParser.PARSE_EXPRESSIONS | SkriptParser.PARSE_LITERALS);
        Expression<?> parsed = switch (kind) {
            case "number" -> parser.parseExpression(Number.class);
            case "boolean" -> parser.parseExpression(Boolean.class);
            default -> parser.parseExpression(String.class);
        };
        if (parsed == null && kind.equals("text")) {
            String raw = value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
            return new SimpleLiteral<>(raw, false);
        }
        return parsed;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        String id = idExpr.getSingle(event);
        Location location = locationExpr.getSingle(event);
        if (manager != null && id != null && location != null) {
            Map<String, Object> values = new LinkedHashMap<>();
            overrides.forEach((path, expr) -> {
                Object value = expr.getSingle(event);
                if (value == null) return;
                values.put(path, value instanceof Number n ? (Object) n.doubleValue() : value instanceof Boolean ? value : value.toString());
            });
            CustomEntity entity = fake ? manager.spawnFake(id, location, values) : manager.spawnReal(id, location, values);
            if (entity != null) resultVar.change(event, new Object[]{ entity }, ChangeMode.SET);
        }
        return getNext();
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set " + resultVar.toString(event, debug) + " to new " + (fake ? "fake" : "real") + " custom entity "
            + idExpr.toString(event, debug) + " at " + locationExpr.toString(event, debug);
    }
}
