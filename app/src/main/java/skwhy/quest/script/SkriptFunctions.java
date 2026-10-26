package skwhy.quest.script;

import ch.njol.skript.lang.function.Function;
import ch.njol.skript.lang.function.Functions;

/** Appel de fonctions Skript globales depuis le module (hooks configurables vers les scripts du serveur). */
@SuppressWarnings("removal")
public final class SkriptFunctions {

    private SkriptFunctions() {}

    public static boolean exists(String name) {
        return name != null && !name.isEmpty() && Functions.getGlobalFunction(name) != null;
    }

    /**
     * Appelle la fonction globale {@code name} avec les arguments donnés (un argument {@code null} laisse la valeur par
     * défaut du paramètre). Renvoie la première valeur retournée, ou {@code null}.
     */
    public static Object call(String name, Object... args) {
        if (name == null || name.isEmpty()) return null;
        Function<?> function = Functions.getGlobalFunction(name);
        if (function == null) return null;
        int count = Math.max(function.getParameters().length, args.length);
        Object[][] params = new Object[count][];
        for (int i = 0; i < count; i++) {
            Object arg = i < args.length ? args[i] : null;
            params[i] = arg == null ? new Object[0] : new Object[]{arg};
        }
        try {
            Object[] result = function.execute(params);
            return result == null || result.length == 0 ? null : result[0];
        } catch (RuntimeException e) {
            skwhy.SkWhy.getInstance().getLogger().warning("[Quest] La fonction Skript '" + name + "' a échoué : " + e);
            return null;
        }
    }
}
