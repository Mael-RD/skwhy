package skwhy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.skriptlang.skript.bukkit.text.TextComponentParser;

/**
 * Converts the text Skript gives us to an Adventure component with the same MiniMessage-based parser Skript uses for
 * {@code send}, item names, etc. (color tags {@code <red>}, {@code <#ff8800>}, {@code <bold>}, legacy {@code &a} codes,
 * links...), so formatting is applied automatically without the script having to write {@code formatted}.
 */
public final class TextComponents {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();

    private TextComponents() {}

    public static Component fromSkript(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        try {
            return TextComponentParser.instance().parse(text);
        } catch (RuntimeException | LinkageError e) {
            // Skript without its text parser: legacy codes only
            return LEGACY.deserialize(text.replace('§', '&'));
        }
    }
}
