package skwhy.quest.script;

import org.bukkit.OfflinePlayer;

import net.kyori.adventure.text.Component;
import skwhy.quest.QuestEngine;
import skwhy.quest.data.LegacyPlayerData;
import skwhy.util.TextComponents;

/**
 * Texte des quêtes et des NPC. Les balises s'écrivent entre pourcentages :
 * {@code %nl%}, {@code %player%}, {@code %genre::masculin::féminin%} et {@code %variable::quête::nom%}.
 * (Dans une chaîne Skript, un pourcentage littéral s'écrit {@code %%} : {@code "%%player%%"} produit {@code %player%}.)
 * Une balise inconnue est laissée telle quelle.
 */
public final class QuestText {

    private QuestText() {}

    public static String expand(String text, OfflinePlayer player) {
        if (text == null) return "";
        if (text.indexOf('%') < 0) return text;
        String[] parts = text.split("%", -1);
        StringBuilder out = new StringBuilder(parts[0]);
        int i = 1;
        while (i < parts.length) {
            boolean closed = i + 1 < parts.length;
            String tag = parts[i];
            String replacement = closed ? replace(tag, player) : null;
            if (replacement != null) {
                out.append(replacement);
                out.append(parts[i + 1]);
                i += 2;
            } else {
                out.append('%').append(tag);
                i++;
            }
        }
        return out.toString();
    }

    private static String replace(String tag, OfflinePlayer player) {
        String[] values = tag.split("::", -1);
        switch (values[0]) {
            case "nl":
                return values.length == 1 ? "\n" : null;
            case "player":
                return values.length == 1 ? (player.getName() == null ? "" : player.getName()) : null;
            case "genre": {
                if (values.length < 3) return null;
                return LegacyPlayerData.feminine(player.getUniqueId()) ? values[2] : values[1];
            }
            case "variable": {
                if (values.length < 3) return null;
                Object v = QuestEngine.variable(player, values[1], values[2]);
                return v == null ? "" : QuestEngine.format(v);
            }
            default:
                return null;
        }
    }

    public static Component component(String text, OfflinePlayer player) {
        return TextComponents.fromSkript(expand(text, player));
    }
}
