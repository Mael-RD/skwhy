package skwhy.quest.npc;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import skwhy.SkWhy;
import skwhy.util.TextComponents;

/**
 * Apparence de la boussole, lue dans {@code quests.compass_display.*} de la config. Chaque texte est converti avec
 * {@link TextComponents#fromSkript} (balises {@code <#ddddcc>}, {@code <green>}, {@code &a}...) ; les valeurs par défaut
 * reprennent la boussole de l'ancien moteur Skript (lettres en italique gras mathématique, espace après chaque repère).
 * Les symboles d'objectif ({@code objective}, {@code up}, {@code down}) prennent la couleur de la palette sauf s'ils en définissent une.
 */
public final class CompassStyle {

    static final String[] DEFAULT_COLORS = {"#ffaa00", "#00ff00", "#ff0000", "#00ffff", "#ffff00", "#8800ff", "#ffbbcc", "#3333ff",
        "#777777", "#886543", "#333333", "#ff6600", "#00aa88", "#aa00ff", "#aaff00", "#ff0088", "#0088ff", "#996600", "#00ff99", "#cc0000", "#4444aa"};
    /** Ordre des lettres : une par octant, en partant du sud (yaw 0) dans le sens des yaw croissants. */
    static final String[] LETTER_KEYS = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
    private static final String[] DEFAULT_LETTERS = {"<#ddddcc>𝙎", "<#ddddcc>𝙎𝙊", "<#ddddcc>𝙊", "<#ddddcc>𝙉𝙊", "<#ddddcc>𝙉",
        "<#ddddcc>𝙉𝙀", "<#ddddcc>𝙀", "<#ddddcc>𝙎𝙀"};

    public final Key font;
    public final int halfWindow;
    public final long refreshTicks;
    public final boolean smoothScroll;
    public final double pitchMargin;
    public final BossBar.Color barColor;
    public final BossBar.Color zoneBarColor;
    public final BossBar.Overlay overlay;
    public final Component[] letters = new Component[LETTER_KEYS.length];
    public final Component first;
    public final Component quarter;
    public final Component half;
    public final Component plain;
    public final Component objective;
    public final Component up;
    public final Component down;
    public final Component zone;
    public final TextColor[] colors;

    private CompassStyle(ConfigurationSection c) {
        String fontKey = c.getString("font", "");
        Key parsed = null;
        if (fontKey != null && !fontKey.isBlank()) {
            try {
                parsed = Key.key(fontKey.trim());
            } catch (RuntimeException ignored) {
                SkWhy.getInstance().getLogger().warning("quests.compass_display.font invalide : " + fontKey);
            }
        }
        font = parsed;
        halfWindow = Math.max(1, Math.min(40, c.getInt("half_window", 12)));
        refreshTicks = Math.max(1, c.getLong("refresh_ticks", 2));
        smoothScroll = c.getBoolean("smooth_scroll", true);
        pitchMargin = c.getDouble("pitch_margin", 10);
        barColor = enumOf(BossBar.Color.class, c.getString("bar_color"), BossBar.Color.BLUE);
        zoneBarColor = enumOf(BossBar.Color.class, c.getString("zone_bar_color"), BossBar.Color.GREEN);
        overlay = enumOf(BossBar.Overlay.class, c.getString("overlay"), BossBar.Overlay.NOTCHED_6);
        ConfigurationSection l = c.getConfigurationSection("letters");
        for (int i = 0; i < LETTER_KEYS.length; i++) {
            letters[i] = text(l == null ? null : l.getString(LETTER_KEYS[i]), DEFAULT_LETTERS[i]);
        }
        ConfigurationSection cells = c.getConfigurationSection("cells");
        first = text(cells, "first", " <#ddddcc>· ");
        quarter = text(cells, "quarter", "<#ddddcc>• ");
        half = text(cells, "half", "<#ddddcc>◈ ");
        plain = text(cells, "plain", "<#ddddcc>· ");
        ConfigurationSection o = c.getConfigurationSection("objective");
        objective = text(o, "level", "❖ ");
        up = text(o, "up", "⮝ ");
        down = text(o, "down", "⮟ ");
        zone = text(o, "zone", "&a◯");
        List<String> list = c.getStringList("colors");
        if (list.isEmpty()) list = List.of(DEFAULT_COLORS);
        List<TextColor> out = new ArrayList<>();
        for (String s : list) {
            TextColor color = TextColor.fromHexString(s.trim());
            if (color != null) out.add(color);
        }
        if (out.isEmpty()) for (String s : DEFAULT_COLORS) out.add(TextColor.fromHexString(s));
        colors = out.toArray(new TextColor[0]);
    }

    private static Component text(ConfigurationSection section, String key, String def) {
        return text(section == null ? null : section.getString(key), def);
    }

    private static Component text(String value, String def) {
        return TextComponents.fromSkript(value == null ? def : value);
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, E def) {
        if (name == null) return def;
        try {
            return Enum.valueOf(type, name.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            SkWhy.getInstance().getLogger().warning("quests.compass_display : valeur inconnue '" + name + "' pour " + type.getSimpleName());
            return def;
        }
    }

    public TextColor color(int index) {
        return colors[Math.floorMod(index, colors.length)];
    }

    public static CompassStyle load() {
        FileConfiguration config = SkWhy.getInstance().getConfig();
        ConfigurationSection section = config.getConfigurationSection("quests.compass_display");
        return new CompassStyle(section == null ? new MemoryConfiguration() : section);
    }
}
