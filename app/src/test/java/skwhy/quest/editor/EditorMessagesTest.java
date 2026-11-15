package skwhy.quest.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Ce que le joueur lit dans le chat : récapitulatif (survol), boutons, lien. */
class EditorMessagesTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static ChangeSet sample() {
        ChangeSet.Entry quest = new ChangeSet.Entry("quest", "bois_perdus", "v1", EditorSupportTest.tree("{}"), new TreeDiff.Stat(12, 3, 5), List.of("script", "steps"));
        ChangeSet.Entry npc = new ChangeSet.Entry("npc", "frieren", "v2", EditorSupportTest.tree("{}"), new TreeDiff.Stat(0, 1, 2), List.of("script"));
        return new ChangeSet("id", UUID.randomUUID(), "s", "quest", "bois_perdus", 0L, List.of(quest, npc));
    }

    private static Component hoverOf(Component root) {
        for (Component c : root.children()) {
            if (c.hoverEvent() != null) {
                HoverEvent<?> h = c.hoverEvent();
                assertInstanceOf(Component.class, h.value());
                return (Component) h.value();
            }
        }
        throw new AssertionError("aucun survol dans " + plain(root));
    }

    @Test
    void theSummaryNamesWhatWasTouchedAndHowMuch() {
        Component line = EditorMessages.summary(sample(), "Modifications reçues : ");
        String text = plain(line);
        assertTrue(text.contains("1 quête et 1 NPC"), text);
        assertTrue(text.contains("23 modifications"), text);
    }

    @Test
    void hoveringListsEveryQuestAndNpcWithTheirCounts() {
        String hover = plain(hoverOf(EditorMessages.summary(sample(), "x ")));
        assertTrue(hover.contains("Quête bois_perdus"), hover);
        assertTrue(hover.contains("+12 −3 ~5"), hover);
        assertTrue(hover.contains("(script, steps)"), hover);
        assertTrue(hover.contains("NPC frieren"), hover);
        assertTrue(hover.contains("+0 −1 ~2"), hover);
        assertTrue(hover.contains("Total : +12 −4 ~7"), hover);
    }

    @Test
    void pluralsAreHandled() {
        ChangeSet.Entry a = new ChangeSet.Entry("quest", "a", "v", EditorSupportTest.tree("{}"), new TreeDiff.Stat(0, 0, 1), List.of("name"));
        ChangeSet.Entry b = new ChangeSet.Entry("quest", "b", "v", EditorSupportTest.tree("{}"), new TreeDiff.Stat(0, 0, 1), List.of("name"));
        String text = plain(EditorMessages.summary(new ChangeSet("i", UUID.randomUUID(), "s", "quest", "a", 0L, List.of(a, b)), ""));
        assertTrue(text.contains("2 quêtes"), text);
        assertTrue(text.contains("2 modifications"), text);
        String one = plain(EditorMessages.summary(new ChangeSet("i", UUID.randomUUID(), "s", "quest", "a", 0L, List.of(a)), ""));
        assertTrue(one.contains("1 quête,") && one.contains("1 modification") && !one.contains("modifications"), one);
    }

    @Test
    void buttonsAreClickableAndLabelled() {
        Component button = EditorMessages.button("✔ Valider", net.kyori.adventure.text.format.NamedTextColor.GREEN, "aide", java.time.Duration.ofMinutes(1), () -> { });
        assertEquals("[✔ Valider]", plain(button));
        assertTrue(button.clickEvent() != null);
        // le rappel serveur n'existe qu'avec le vrai Paper ; ici l'API de test remplace l'action par un texte de repli
        assertTrue(button.clickEvent().action() != ClickEvent.Action.OPEN_URL);
        Component both = EditorMessages.buttons(button, button);
        assertEquals("[✔ Valider]  [✔ Valider]", plain(both));
    }

    @Test
    void theLinkOpensTheUrlAndMentionsTheDelay() {
        Component link = EditorMessages.link("la quête Zelda", "https://exemple.fr/editor/abc/", 120, false);
        String text = plain(link);
        assertTrue(text.contains("Lien de l'éditeur de la quête Zelda"), text);
        Component clickable = link.children().stream().filter(c -> c.clickEvent() != null).findFirst().orElseThrow();
        assertEquals(ClickEvent.Action.OPEN_URL, clickable.clickEvent().action());
        assertTrue(clickable.clickEvent().toString().contains("https://exemple.fr/editor/abc/"));
        assertTrue(plain((Component) clickable.hoverEvent().value()).contains("Valable 2 h"));
        assertTrue(plain(EditorMessages.link("x", "u", 120, true)).contains("Nouveau lien"));
        assertEquals("2 h", EditorMessages.duration(120));
        assertEquals("5 min", EditorMessages.duration(5));
        assertEquals("90 min", EditorMessages.duration(90));
    }
}
