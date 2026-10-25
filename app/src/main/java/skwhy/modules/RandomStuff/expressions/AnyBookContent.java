package skwhy.modules.RandomStuff.expressions;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Event;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.util.TextComponents;

import java.util.ArrayList;
import java.util.List;

/**
 * Pages d'un livre, signé (written_book) <b>ou non signé</b> (writable_book).
 * Skript's {@code content of %book%} ne gère plus les livres non signés (tag de données différent) :
 * ici on passe par {@link BookMeta}, qui sait lire/écrire les deux sortes de livres.
 */
@Name("Content of Any Book")
@Description({
    "The pages of a book, whether it is signed (written book) or unsigned (book and quill).",
    "Skript's own 'content of book' no longer works on unsigned books because they store their pages in a different item tag.",
    "Returns one text component per page. Text given to set/add goes through Skript's text parser (<red>, <#ff8800>, &a...).",
    "Not a book: nothing is returned and nothing is changed. Supports set, add, delete and reset."
})
@Examples({
    "set {_pages::*} to content of any book of player's tool",
    "add \"<green>Page 2\" to content of any book of player's tool",
    "set content of any book of {_book} to \"first page\" and \"second page\"",
    "reset content of any book of player's tool"
})
@Since("1.6.0")
public class AnyBookContent extends SimpleExpression<Component> {

    private Expression<ItemType> items;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        items = (Expression<ItemType>) exprs[0];
        return true;
    }

    @Override
    protected Component @Nullable [] get(Event event) {
        List<Component> out = new ArrayList<>();
        for (ItemType item : items.getArray(event)) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BookMeta book) out.addAll(book.pages());
        }
        return out.toArray(new Component[0]);
    }

    @Override
    public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET, ADD -> CollectionUtils.array(String[].class, Component[].class);
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        List<Component> given = new ArrayList<>();
        if (delta != null) {
            for (Object o : delta) {
                if (o instanceof Component c) given.add(c);
                else if (o != null) given.add(TextComponents.fromSkript(o.toString()));
            }
        }
        items.changeInPlace(event, item -> {
            if (!(item.getItemMeta() instanceof BookMeta book)) return item;
            switch (mode) {
                case SET -> book.pages(given);
                case ADD -> book.addPages(given.toArray(new Component[0]));
                default -> book.pages(List.of());
            }
            item.setItemMeta(book);
            return item;
        });
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public Class<? extends Component> getReturnType() {
        return Component.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "content of any book of " + items.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(AnyBookContent.class, Component.class)
                .addPattern("[the] content of any book of %itemtypes%")
                .build()
        );
    }
}
