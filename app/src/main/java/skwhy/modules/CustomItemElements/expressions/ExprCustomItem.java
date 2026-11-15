package skwhy.modules.CustomItemElements.expressions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import skwhy.customitem.CustomItemDef;
import skwhy.customitem.CustomItemFactory;
import skwhy.customitem.CustomItemIdentity;
import skwhy.customitem.CustomItemManager;

@Name("[Custom Item] Custom Item")
@Description("Builds one item from the definition 'custom-items/<id>.yml' (spaces of the id are read as '_', case is ignored). " +
    "'for' is the player used for {uuid} / {name} in the definition and for the 'owner' parameter. " +
    "'with parameters' is a list among \"owner\" (the item belongs to the player), \"safe\", \"save\", \"invulnerable\" and \"preserve\": they are only stored in the item (custom_data), scripts give them their effect. " +
    "'with placeholders' is a list of \"key=value\" texts that replace {key} in the definition. " +
    "'named' replaces the name ('[name]' stands for the name of the definition); 'with lore' adds lines to the lore ('[lore]' stands for the lore of the definition, lines before it go before). " +
    "The item gets the tags 'item' (id) and 'version' in its custom_data. " +
    "An id with no definition gives a barrier named \"Item inconnu\" that remembers the id ('attendu').")
@Examples({
    "give custom item \"billet\" for player with placeholders \"value=50\" to player",
    "set {_i} to custom item \"epee_de_feu\" for player with parameters \"owner\" and \"safe\"",
    "set {_i} to custom item \"potion\" named \"<red>Potion de [name]\" with lore \"<gray>Ligne ajoutee\""
})
@Since("1.6.0")
public class ExprCustomItem extends SimpleExpression<ItemStack> {

    private Expression<String> id;
    private Expression<OfflinePlayer> player;
    private Expression<String> parameters;
    private Expression<String> placeholders;
    private Expression<String> name;
    private Expression<String> lore;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCustomItem.class, ItemStack.class)
                .addPattern("[the|a] custom item %string% [for %-offlineplayer%] [with param[eter]s %-strings%] [with placeholders %-strings%] [named %-string%] [with lore %-strings%]")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        id = (Expression<String>) exprs[0];
        player = (Expression<OfflinePlayer>) exprs[1];
        parameters = (Expression<String>) exprs[2];
        placeholders = (Expression<String>) exprs[3];
        name = (Expression<String>) exprs[4];
        lore = (Expression<String>) exprs[5];
        return true;
    }

    @Override
    protected ItemStack @Nullable [] get(Event event) {
        CustomItemManager manager = CustomItemManager.get();
        String key = id.getSingle(event);
        if (manager == null || key == null) return new ItemStack[0];
        CustomItemDef def = manager.registry().get(key);
        if (def == null) return new ItemStack[]{ CustomItemFactory.unknown(skwhy.customitem.CustomItemIds.normalize(key)) };

        List<String> params = new ArrayList<>();
        if (parameters != null) {
            for (String p : parameters.getArray(event)) {
                String lower = p.trim().toLowerCase(java.util.Locale.ROOT);
                if (lower.equals("owner") || CustomItemIdentity.FLAGS.contains(lower)) params.add(lower);
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (placeholders != null) {
            for (String entry : placeholders.getArray(event)) {
                int eq = entry.indexOf('=');
                if (eq > 0) values.put(entry.substring(0, eq).trim(), entry.substring(eq + 1));
            }
        }
        List<String> lines = lore == null ? List.of() : List.of(lore.getArray(event));
        CustomItemFactory.Options options = new CustomItemFactory.Options(
            player == null ? null : player.getSingle(event), params, name == null ? null : name.getSingle(event), lines, values);
        ItemStack stack = CustomItemFactory.create(def, options);
        return stack == null ? new ItemStack[]{ CustomItemFactory.unknown(def.id()) } : new ItemStack[]{ stack };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends ItemStack> getReturnType() { return ItemStack.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "custom item " + id.toString(event, debug) + (player == null ? "" : " for " + player.toString(event, debug));
    }
}
