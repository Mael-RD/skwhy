package skwhy.modules.RandomStuff.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.modules.RandomStuff.sections.ReplaceRegexSection;

import java.util.List;

@Name("Previous Last Format")
@Description({
    "In a 'replace regex ... with code' section, the MiniMessage tags (colors and decorations) that are active at the end of "
        + "the text placed before the current match, replacements already made included.",
    "Add it after '<reset>' at the end of a replacement to give the following text the format it had before the match. "
        + "Click, hover and insertion are not included. Empty when no format is active."
})
@Examples({
    "replace regex \"@\\w+\" in {_text} with code:",
    "\tset replaced text to \"<aqua>%matched text%<reset>%previous last format%\""
})
@Since("1.4.0")
public class PreviousLastFormat extends SimpleExpression<String> {

    /** Caractère privé ajouté en fin de texte pour obtenir un noeud qui porte le style effectif, même après un tag vide. */
    private static final String SENTINEL = "";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private ReplaceRegexSection section;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        section = getParser().getCurrentSection(ReplaceRegexSection.class);
        if (section == null) {
            Skript.error("'previous last format' ne peut être utilisé que dans une section 'replace regex ... with code'.");
            return false;
        }
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        ReplaceRegexSection.ReplaceState state = section.getState(event);
        if (state == null) return new String[0];

        Component parsed = MINI.deserialize(state.getOutputSoFar() + SENTINEL);
        Style style = lastStyle(parsed, Style.empty());
        if (style == null) return new String[]{ "" };

        Style plain = style.clickEvent(null).hoverEvent(null).insertion(null);
        // La couleur d'abord : le parser de Skript remet à zéro les décorations quand une couleur suit
        Style color = plain.color() == null ? Style.empty() : Style.style(plain.color());
        Style decorations = plain.color(null);
        String tags = tagsOf(color) + tagsOf(decorations);
        return new String[]{ tags };
    }

    private static String tagsOf(Style style) {
        if (style.isEmpty()) return "";
        String tags = MINI.serialize(Component.text(SENTINEL).style(style)).replace(SENTINEL, "");
        return tags.replaceAll("(</[^>]*>)+$", "");
    }

    /** Style effectif du noeud qui contient le caractère sentinelle (le dernier du texte). */
    private static @Nullable Style lastStyle(Component component, Style parent) {
        Style effective = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && text.content().contains(SENTINEL)) return effective;
        List<Component> children = component.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            Style found = lastStyle(children.get(i), effective);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "previous last format";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(PreviousLastFormat.class, String.class)
                .addPattern("[the] previous last format")
                .build()
        );
    }
}
