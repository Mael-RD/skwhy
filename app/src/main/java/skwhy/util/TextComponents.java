package skwhy.util;

import ch.njol.skript.util.chat.ChatMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Converts the text Skript gives us to an Adventure component, with exactly the same chat-style filter Skript applies
 * to {@code send}, item names, etc.: color tags ({@code <red>}, {@code <#ff8800>}, {@code <bold>}, {@code <reset>}...),
 * the legacy {@code &a} / {@code §a} codes, links, and so on.
 */
public final class TextComponents {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();

    private TextComponents() {}

    @SuppressWarnings("removal")
    public static Component fromSkript(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        try {
            // Skript's own parser (ChatMessages) -> JSON -> Adventure
            return GsonComponentSerializer.gson().deserialize(ChatMessages.toJson(text));
        } catch (RuntimeException | LinkageError e) {
            return LEGACY.deserialize(text.replace('§', '&'));
        }
    }
}
