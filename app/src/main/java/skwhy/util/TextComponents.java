package skwhy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Converts the text Skript gives us to an Adventure component.
 * <p>
 * Skript turns its color tags ({@code <red>}, {@code <#ff8800>}, {@code <bold>}...) into legacy section-sign codes
 * (hex colors use the {@code §x§r§r§g§g§b§b} form), so those must be understood; the old {@code &a} codes keep working.
 */
public final class TextComponents {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();

    private TextComponents() {}

    public static Component fromSkript(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return SERIALIZER.deserialize(text.replace('§', '&'));
    }
}
