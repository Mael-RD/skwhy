package skwhy.modules.QuestElements.sections;

import java.util.Map;

import ch.njol.skript.variables.Variables;
import skwhy.modules.QuestElements.events.QuestScriptEvent;

/** Variables locales disponibles dans les sections {@code register quest action} et {@code register quest condition}. */
final class SectionLocals {

    private SectionLocals() {}

    /** Définit {@code {_player}}, {@code {_npc}}, {@code {_entity}} et {@code {_args::*}} pour l'événement. */
    static void set(QuestScriptEvent event) {
        Variables.setVariable("player", event.getPlayer() != null ? event.getPlayer() : event.getOfflinePlayer(), event, true);
        if (event.getNpc() != null) Variables.setVariable("npc", event.getNpc(), event, true);
        if (event.getEntity() != null) Variables.setVariable("entity", event.getEntity(), event, true);
        for (Map.Entry<String, Object> e : event.getArguments().entrySet()) {
            Object value = e.getValue();
            if (value instanceof Integer || value instanceof Short || value instanceof Byte) value = ((Number) value).longValue();
            else if (value instanceof Float f) value = f.doubleValue();
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                Variables.setVariable("args::" + e.getKey(), value, event, true);
            }
        }
    }
}
