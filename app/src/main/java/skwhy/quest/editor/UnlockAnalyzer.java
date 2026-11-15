package skwhy.quest.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import skwhy.quest.Tree;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.model.ScriptNode;
import skwhy.quest.model.ScriptSet;

/**
 * Trouve les NPC qui font avancer une quête, sans joueur : pour chaque action {@code quest} d'un script de NPC, on regarde
 * si elle est <b>accessible</b> en supposant la quête à telle étape. Les conditions qui comparent l'étape de cette quête
 * à une constante sont évaluées ; toutes les autres (niveau, variable, inventaire...) sont « inconnues » et ne bloquent pas.
 * Une action est accessible si aucune condition sur le chemin qui y mène n'est certainement fausse.
 */
public final class UnlockAnalyzer {

    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 20000;

    private UnlockAnalyzer() {}

    public enum Tri {
        TRUE, FALSE, UNKNOWN;

        Tri not() { return this == TRUE ? FALSE : this == FALSE ? TRUE : UNKNOWN; }
    }

    /**
     * Un NPC qui agit sur une quête.
     *
     * @param mode      {@code new} (débloque la quête), {@code step} (place sur une étape) ou {@code substep} (avance un objectif)
     * @param toStep    étape visée (0-based) ; 0 pour {@code new}
     * @param substep   objectif visé (0-based) pour {@code substep}, sinon {@code -1}
     * @param fromSteps étapes de la quête depuis lesquelles l'action est accessible ({@code -1} = quête pas commencée) ;
     *                  {@code null} = depuis n'importe laquelle
     */
    public record Unlocker(String npcId, String node, String mode, int toStep, int substep, List<Integer> fromSteps) {}

    /** Hypothèse d'évaluation : la quête {@code questId} est à l'étape {@code step} ({@code -1} : pas commencée). */
    public record Assumption(String questId, int step) {}

    public static List<Unlocker> unlockers(Quest quest, Collection<QuestNpc> npcs) {
        List<Unlocker> out = new ArrayList<>();
        int stepCount = quest.steps().size();
        for (QuestNpc npc : npcs) {
            List<ScriptNode> candidates = new ArrayList<>();
            for (ScriptNode node : npc.script().all().values()) {
                if (!"quest".equals(node.action()) || !quest.id().equals(node.str("quest"))) continue;
                String mode = node.str("mode", "");
                if (mode.equals("new") || mode.equals("step") || mode.equals("substep")) candidates.add(node);
            }
            if (candidates.isEmpty()) continue;

            List<Set<String>> reach = new ArrayList<>();
            for (int s = -1; s < stepCount; s++) reach.add(reachable(npc.script(), new Assumption(quest.id(), s)));

            for (ScriptNode node : candidates) {
                List<Integer> from = new ArrayList<>();
                for (int s = -1; s < stepCount; s++) if (reach.get(s + 1).contains(node.id())) from.add(s);
                if (from.isEmpty()) continue;
                String mode = node.str("mode", "");
                int toStep = mode.equals("new") ? 0 : node.integer("step", 0);
                int substep = mode.equals("substep") ? node.integer("substep", 0) : -1;
                boolean any = from.size() == stepCount + 1;
                out.add(new Unlocker(npc.id(), node.id(), mode, toStep, substep, any ? null : from));
            }
        }
        out.sort(java.util.Comparator.comparing(Unlocker::npcId).thenComparing(Unlocker::node));
        return out;
    }

    // ── Accessibilité ───────────────────────────────────────────────────────

    /** Ids des actions atteignables depuis {@code main} sous cette hypothèse. */
    public static Set<String> reachable(ScriptSet scripts, Assumption assumption) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add("main");
        while (!queue.isEmpty() && seen.size() < MAX_NODES) {
            String id = queue.pollFirst();
            if (!seen.add(id)) continue;
            ScriptNode node = scripts.get(id);
            if (node == null) continue;
            switch (node.action()) {
                case "list" -> {
                    boolean single = "single".equals(node.str("mode", "list"));
                    for (String child : Tree.strings(node.data(), "liste")) {
                        ScriptNode c = scripts.get(child);
                        if (c == null) continue;
                        String guard = c.guard();
                        Tri t = guard == null ? Tri.TRUE : evaluate(scripts, guard, assumption, 0);
                        if (t == Tri.FALSE) continue;
                        queue.add(child);
                        if (single && t == Tri.TRUE) break;
                    }
                }
                case "dialogue" -> {
                    for (Object d : Tree.map(node.data(), "dialogues").values()) dialogue(scripts, Tree.map(d), assumption, queue);
                }
                case "move" -> {
                    queue.addAll(Tree.strings(node.data(), "on_stop"));
                    queue.addAll(Tree.strings(node.data(), "on_continue"));
                }
                default -> { }
            }
        }
        seen.removeIf(id -> scripts.get(id) == null);
        return seen;
    }

    /** Un dialogue joue le premier texte dont la condition est vraie : ceux d'après un texte sûr sont inaccessibles. */
    private static void dialogue(ScriptSet scripts, Map<String, Object> dialogue, Assumption a, Deque<String> queue) {
        for (Map<String, Object> text : Tree.maps(dialogue, "texts")) {
            Tri t = evaluate(scripts, Tree.str(text, "condition", "true"), a, 0);
            if (t == Tri.FALSE) continue;
            String action = Tree.str(text, "action");
            if (action != null) queue.add(action);
            for (Map<String, Object> answer : Tree.maps(text, "answers")) {
                if (evaluate(scripts, Tree.str(answer, "condition", "true"), a, 0) == Tri.FALSE) continue;
                String ans = Tree.str(answer, "action");
                if (ans != null && !ans.equals("<none>")) queue.add(ans);
                String other = Tree.str(answer, "target_dialogue");
                if (other != null) queue.add(other);
            }
            if (t == Tri.TRUE) return;
        }
    }

    // ── Conditions ──────────────────────────────────────────────────────────

    public static Tri evaluate(ScriptSet scripts, String id, Assumption a, int depth) {
        if (id == null || id.equals("true")) return Tri.TRUE;
        if (id.equals("false")) return Tri.FALSE;
        if (depth > MAX_DEPTH) return Tri.UNKNOWN;
        ScriptNode node = scripts.get(id);
        if (node == null || !node.isCondition()) return Tri.UNKNOWN;
        boolean reverse = node.bool("reverse", false);
        Tri result;
        switch (node.conditionType()) {
            case "list" -> {
                boolean all = "all".equals(node.str("mode", "single"));
                List<String> list = Tree.strings(node.data(), "liste");
                if (!all && list.isEmpty()) {
                    result = Tri.FALSE;
                } else {
                    result = all ? Tri.TRUE : Tri.FALSE;
                    for (String child : list) {
                        Tri t = evaluate(scripts, child, a, depth + 1);
                        if (all) {
                            if (t == Tri.FALSE) { result = Tri.FALSE; break; }
                            if (t == Tri.UNKNOWN) result = Tri.UNKNOWN;
                        } else {
                            if (t == Tri.TRUE) { result = Tri.TRUE; break; }
                            if (t == Tri.UNKNOWN) result = Tri.UNKNOWN;
                        }
                    }
                }
            }
            case "compare" -> result = compare(node, a);
            case "between" -> result = between(node, a);
            default -> result = Tri.UNKNOWN;
        }
        return reverse ? result.not() : result;
    }

    private static Tri compare(ScriptNode node, Assumption a) {
        Object x = known(Tree.map(node.data(), "value1"), a);
        Object y = known(Tree.map(node.data(), "value2"), a);
        if (x == null || y == null) return Tri.UNKNOWN;
        String op = node.str("operator", "equal");
        if (op.equals("equal")) return bool(equal(x, y));
        double p = number(x);
        double q = number(y);
        if (Double.isNaN(p) || Double.isNaN(q)) return Tri.UNKNOWN;
        return switch (op) {
            case "superior_strict" -> bool(p > q);
            case "inferior_strict" -> bool(p < q);
            case "superior" -> bool(p >= q);
            case "inferior" -> bool(p <= q);
            default -> Tri.UNKNOWN;
        };
    }

    private static Tri between(ScriptNode node, Assumption a) {
        Object lo = known(Tree.map(node.data(), "value1"), a);
        Object v = known(Tree.map(node.data(), "value2"), a);
        Object hi = known(Tree.map(node.data(), "value3"), a);
        if (lo == null || v == null || hi == null) return Tri.UNKNOWN;
        double x = number(lo);
        double y = number(v);
        double z = number(hi);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) return Tri.UNKNOWN;
        return bool(y >= x && y <= z);
    }

    /** Valeur d'une opérande si on la connaît d'avance (constante, ou étape de la quête supposée), sinon {@code null}. */
    private static Object known(Map<String, Object> operand, Assumption a) {
        String type = Tree.str(operand, "type", "value");
        if (type.equals("value")) {
            Object v = operand.get("value");
            return v instanceof Byte b ? (Object) (int) b : v;
        }
        if (type.equals("quest") && a.questId().equals(Tree.str(operand, "quest", ""))
            && !operand.containsKey("variable") && !operand.containsKey("substep")) {
            return a.step();
        }
        return null;
    }

    private static Tri bool(boolean b) {
        return b ? Tri.TRUE : Tri.FALSE;
    }

    private static boolean equal(Object x, Object y) {
        if (x instanceof String || y instanceof String) {
            if (x instanceof Number || y instanceof Number || x instanceof Boolean || y instanceof Boolean) {
                return number(x) == number(y) && !Double.isNaN(number(x));
            }
            return String.valueOf(x).equals(String.valueOf(y));
        }
        return number(x) == number(y);
    }

    private static double number(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { return Double.NaN; }
        }
        return 0;
    }
}
