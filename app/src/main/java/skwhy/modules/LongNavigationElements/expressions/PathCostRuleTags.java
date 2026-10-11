package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.modules.LongNavigationElements.types.PathCostRuleData;

import java.util.ArrayList;
import java.util.List;

@Name("[ChunkPath] Path Cost Rule Tags")
@Description("Gets, sets, adds to, removes from, or clears the list of block tags matched by a path cost " +
    "rule (e.g. 'minecraft tag \"leaves\"' — the \"minecraft\" namespace is assumed unless one is given). " +
    "One or several tags can be given at once to 'add', 'remove' and 'set'. " +
    "Tags that no longer resolve to a known block tag are simply ignored when the rule is used, never an " +
    "error. 'delete' and 'reset' both empty the list; the rule's cost itself is untouched by either.")
@Examples({
    "set {_rule} to a new path cost rule with cost 8",
    "add minecraft tag \"leaves\" to tags of path cost rule {_rule}",
    "add minecraft tag \"leaves\" and minecraft tag \"logs\" to tags of path cost rule {_rule}",
    "remove minecraft tag \"leaves\" and minecraft tag \"logs\" from tags of path cost rule {_rule}",
    "set tags of path cost rule {_rule} to minecraft tag \"leaves\" and minecraft tag \"logs\"",
    "delete tags of path cost rule {_rule}"
})
@Since("1.4.0")
@SuppressWarnings("rawtypes") // Tag is used raw to match Skript's own "minecrafttag" ClassInfo<Tag>, which covers both block and entity tags.
public class PathCostRuleTags extends SimpleExpression<Tag> {

    private Expression<PathCostRuleData> ruleExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.ruleExpr = (Expression<PathCostRuleData>) exprs[0];
        return true;
    }

    @Override
    protected Tag @Nullable [] get(Event event) {
        PathCostRuleData[] rules = ruleExpr.getAll(event);
        if (rules == null || rules.length == 0) return null;
        List<Tag> result = new ArrayList<>();
        for (PathCostRuleData rule : rules) {
            if (rule == null) continue;
            for (String tagName : rule.getTags()) {
                Tag<Material> tag = PathCostRuleData.resolveTag(tagName);
                if (tag != null) result.add(tag);
            }
        }
        return result.toArray(new Tag[0]);
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            // Tag[].class (in addition to Tag.class) tells Skript this expression can take a whole batch of
            // tags at once — needed since Skript 2.16 for changers, such as 'tags of %block%', that can never
            // resolve to a single value (Expression#canBeSingle() == false); otherwise it rejects the change
            // at parse time with "Only one tag can be added to ..., not more".
            case SET, ADD, REMOVE -> new Class<?>[]{ Tag.class, Tag[].class };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        PathCostRuleData[] rules = ruleExpr.getAll(event);
        if (rules == null) return;

        List<String> values = toTagKeyList(delta);
        for (PathCostRuleData rule : rules) {
            if (rule == null) continue;
            switch (mode) {
                case SET -> rule.setTags(values);
                case ADD -> rule.addTags(values);
                case REMOVE -> rule.removeTags(values);
                case DELETE, RESET -> rule.clearTags();
                default -> {
                }
            }
        }
    }

    /** Convertit les tags reçus en leur clé texte (ex. "minecraft:leaves"), forme sous laquelle la règle les conserve. */
    private static List<String> toTagKeyList(Object @Nullable [] delta) {
        List<String> list = new ArrayList<>();
        if (delta == null) return list;
        for (Object o : delta) {
            if (o instanceof Tag<?> tag) {
                list.add(tag.getKey().toString());
            } else if (o instanceof Tag<?>[] tags) {
                // Skript may hand back a batch as a single Tag[] element when it resolved the "Tag[].class"
                // branch of acceptChange instead of converting element-by-element — flatten it either way.
                for (Tag<?> tag : tags) {
                    if (tag != null) list.add(tag.getKey().toString());
                }
            }
        }
        return list;
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Tag> getReturnType() { return Tag.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "tags of path cost rule " + ruleExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(PathCostRuleTags.class, Tag.class)
                .addPattern("tags of path cost rule %pathcostrules%")
                .build()
        );
    }
}
