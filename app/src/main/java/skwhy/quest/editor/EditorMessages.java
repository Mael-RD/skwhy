package skwhy.quest.editor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import skwhy.SkWhy;

/** Messages de l'éditeur envoyés au joueur : lien, récapitulatif des modifications avec boutons, renouvellement. */
final class EditorMessages {

    private EditorMessages() {}

    static Component prefix() {
        return Component.text("[Éditeur] ", NamedTextColor.GOLD);
    }

    static Component info(String text) {
        return prefix().append(Component.text(text, NamedTextColor.GRAY));
    }

    static Component error(String text) {
        return prefix().append(Component.text(text, NamedTextColor.RED));
    }

    /** Le lien cliquable de l'éditeur. */
    static Component link(String name, String url, long minutes, boolean renewed) {
        return prefix()
            .append(Component.text(renewed ? "Nouveau lien de l'éditeur de " : "Lien de l'éditeur de ", NamedTextColor.GRAY))
            .append(Component.text(name, NamedTextColor.YELLOW))
            .append(Component.text(" : ", NamedTextColor.GRAY))
            .append(Component.text("[Ouvrir l'éditeur]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.GRAY)
                    .appendNewline().append(Component.text("Valable " + duration(minutes) + " ou jusqu'à l'enregistrement. Ne le partagez pas.", NamedTextColor.DARK_GRAY)))));
    }

    static String duration(long minutes) {
        if (minutes >= 60 && minutes % 60 == 0) return (minutes / 60) + " h";
        return minutes + " min";
    }

    /** Résumé des modifications reçues, lisible dans le chat et détaillé au survol. */
    static Component summary(ChangeSet cs, String lead) {
        long quests = cs.count(false);
        long npcs = cs.count(true);
        List<String> parts = new ArrayList<>();
        if (quests > 0) parts.add(quests + (quests > 1 ? " quêtes" : " quête"));
        if (npcs > 0) parts.add(npcs + " NPC");
        TreeDiff.Stat total = cs.total();

        Component hover = Component.text("Modifications envoyées par l'éditeur", NamedTextColor.GOLD, TextDecoration.BOLD);
        for (ChangeSet.Entry e : cs.entries()) {
            hover = hover.appendNewline()
                .append(Component.text(e.isNpc() ? "NPC " : "Quête ", NamedTextColor.GRAY))
                .append(Component.text(e.id(), NamedTextColor.YELLOW))
                .appendNewline()
                .append(Component.text("  +" + e.stat().added(), NamedTextColor.GREEN))
                .append(Component.text(" −" + e.stat().removed(), NamedTextColor.RED))
                .append(Component.text(" ~" + e.stat().modified(), NamedTextColor.GOLD));
            if (!e.sections().isEmpty()) {
                hover = hover.append(Component.text("  (" + String.join(", ", e.sections()) + ")", NamedTextColor.DARK_GRAY));
            }
        }
        hover = hover.appendNewline().append(Component.text("Total : +" + total.added() + " −" + total.removed() + " ~" + total.modified()
            + "  (" + total.total() + " valeur" + (total.total() > 1 ? "s" : "") + ")", NamedTextColor.WHITE));

        return prefix()
            .append(Component.text(lead, NamedTextColor.GRAY))
            .append(Component.text(String.join(" et ", parts) + ", " + total.total() + " modification" + (total.total() > 1 ? "s" : ""),
                NamedTextColor.YELLOW, TextDecoration.UNDERLINED).hoverEvent(HoverEvent.showText(hover)));
    }

    /** Un bouton cliquable ; l'action est exécutée sur le thread principal. */
    static Component button(String label, NamedTextColor color, String hover, Duration lifetime, Runnable action) {
        return Component.text("[" + label + "]", color, TextDecoration.BOLD)
            .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)))
            .clickEvent(ClickEvent.callback(audience -> {
                if (Bukkit.isPrimaryThread()) action.run();
                else Bukkit.getScheduler().runTask(SkWhy.getInstance(), action);
            }, builder -> builder.uses(ClickCallback.UNLIMITED_USES).lifetime(lifetime)));
    }

    static Component buttons(Component... buttons) {
        Component out = Component.empty();
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) out = out.append(Component.text("  "));
            out = out.append(buttons[i]);
        }
        return out;
    }
}
