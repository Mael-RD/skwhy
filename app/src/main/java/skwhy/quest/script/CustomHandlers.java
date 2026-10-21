package skwhy.quest.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import skwhy.quest.model.ScriptNode;

/**
 * Types d'actions et de conditions ajoutés par les scripts Skript ({@code register quest action} et
 * {@code register quest condition}). Ils s'utilisent ensuite dans les fichiers de quêtes comme les types intégrés :
 * {@code action: <nom>} pour une action, {@code condition: <nom>} pour une condition.
 */
public final class CustomHandlers {

    /** Exécute une action ; {@code done} doit être appelé une fois l'action terminée (immédiatement ou plus tard). */
    @FunctionalInterface
    public interface ActionHandler {
        void run(ScriptContext ctx, ScriptNode node, Runnable done);
    }

    @FunctionalInterface
    public interface ConditionHandler {
        boolean test(ScriptContext ctx, ScriptNode node);
    }

    private static final Map<String, ActionHandler> ACTIONS = new ConcurrentHashMap<>();
    private static final Map<String, ConditionHandler> CONDITIONS = new ConcurrentHashMap<>();

    private CustomHandlers() {}

    public static void registerAction(String name, ActionHandler handler) {
        ACTIONS.put(name.toLowerCase(), handler);
    }

    public static void registerCondition(String name, ConditionHandler handler) {
        CONDITIONS.put(name.toLowerCase(), handler);
    }

    public static boolean hasAction(String name) {
        return name != null && ACTIONS.containsKey(name.toLowerCase());
    }

    public static boolean hasCondition(String name) {
        return name != null && CONDITIONS.containsKey(name.toLowerCase());
    }

    public static ActionHandler action(String name) {
        return name == null ? null : ACTIONS.get(name.toLowerCase());
    }

    /** Condition inline : le nom seul sert d'identifiant ({@code condition: owns_ring} sur une action). */
    public static boolean condition(String name, ScriptContext ctx) {
        ConditionHandler handler = CONDITIONS.get(name.toLowerCase());
        return handler != null && handler.test(ctx, new ScriptNode(name, Map.of()));
    }

    public static boolean condition(String name, ScriptContext ctx, ScriptNode node) {
        ConditionHandler handler = CONDITIONS.get(name.toLowerCase());
        return handler != null && handler.test(ctx, node);
    }

    public static void clear() {
        ACTIONS.clear();
        CONDITIONS.clear();
    }
}
