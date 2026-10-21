package skwhy.quest.script;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import ch.njol.skript.variables.Variables;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import skwhy.SkWhy;
import skwhy.modules.QuestElements.events.QuestDialogueStartEvent;
import skwhy.quest.QuestEngine;
import skwhy.quest.QuestRegistry;
import skwhy.quest.QuestSettings;
import skwhy.quest.Tree;
import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.model.ScriptNode;
import skwhy.quest.npc.DialogueService;
import skwhy.quest.npc.NpcMover;
import skwhy.quest.npc.TradeService;

/**
 * Interpréteur des scripts de quêtes et de NPC. Les actions sont des nœuds identifiés par un id ; {@code list}
 * enchaîne d'autres nœuds, {@code dialogue} affiche des textes à réponses, les autres agissent sur le joueur.
 */
public final class ScriptEngine {

    private static final int MAX_STEPS = 2000;

    private ScriptEngine() {}

    // ── Exécution ───────────────────────────────────────────────────────────

    /**
     * Exécute l'action {@code actionId} (puis tout ce qu'elle enchaîne). Les attentes ({@code wait}, déplacements de
     * NPC) n'bloquent pas le serveur : la suite reprend plus tard.
     *
     * @param done appelé quand tout est terminé (peut être {@code null})
     */
    public static void run(ScriptContext ctx, String actionId, Runnable done) {
        Deque<String> queue = new ArrayDeque<>();
        queue.add(actionId);
        new Run(ctx, queue, done).proceed();
    }

    private static final class Run {
        private final ScriptContext ctx;
        private final Deque<String> queue;
        private final Runnable done;
        private int steps;

        Run(ScriptContext ctx, Deque<String> queue, Runnable done) {
            this.ctx = ctx;
            this.queue = queue;
            this.done = done;
        }

        void proceed() {
            while (!queue.isEmpty()) {
                if (++steps > MAX_STEPS) {
                    SkWhy.getInstance().getLogger().warning("[Quest] Sécurité anti-boucle infinie dans un script de "
                        + (ctx.npc() != null ? ctx.npc() : ctx.quest()));
                    return;
                }
                String id = queue.pollFirst();
                ScriptNode node = ctx.scripts().get(id);
                if (node == null) continue;
                if (execute(node)) return; // l'action reprendra elle-même la suite
            }
            if (done != null) done.run();
        }

        /** @return {@code true} si l'action est asynchrone (elle rappellera {@link #proceed()}) */
        private boolean execute(ScriptNode node) {
            String type = node.action();
            Player player = ctx.online();
            switch (type) {
                case "list" -> expand(node);
                case "dialogue" -> dialogue(ctx, node.id(), true);
                case "quest" -> quest(node);
                case "function" -> {
                    String function = node.str("function");
                    if (function != null) SkriptFunctions.call(function, ctx.player().getPlayer(), ctx.entity());
                }
                case "variable" -> variable(node);
                case "experience" -> experience(node);
                case "wait" -> {
                    long ticks = Math.max(0, node.integer("time", 0));
                    Bukkit.getScheduler().runTaskLater(SkWhy.getInstance(), this::proceed, ticks);
                    return true;
                }
                case "give" -> give(node);
                case "damage" -> damage(node);
                case "trade" -> {
                    if (player != null && ctx.npc() != null) {
                        Map<String, Object> trade = Tree.map(ctx.npc().trades(), node.str("trade", ""));
                        TradeService.open(player, trade, ctx);
                    }
                }
                case "money" -> money(node);
                case "move" -> {
                    return move(node);
                }
                case "command" -> command(node);
                case "sound" -> {
                    if (player != null) {
                        player.playSound(player.getLocation(), node.str("sound", "ui.button.click"),
                            (float) node.dbl("volume", 1), (float) node.dbl("pitch", 1));
                    }
                }
                case "teleport" -> teleport(node);
                case "title" -> {
                    if (player != null) {
                        player.showTitle(Title.title(QuestText.component(node.str("title", ""), player),
                            QuestText.component(node.str("subtitle", ""), player)));
                    }
                }
                case "message" -> {
                    if (player != null) player.sendMessage(QuestText.component(node.str("text", ""), player));
                }
                default -> {
                    CustomHandlers.ActionHandler handler = CustomHandlers.action(type);
                    if (handler != null) {
                        handler.run(ctx, node, this::proceed);
                        return true;
                    }
                }
            }
            return false;
        }

        private void expand(ScriptNode node) {
            boolean single = "single".equals(node.str("mode", "list"));
            List<String> picked = new ArrayList<>();
            for (String child : Tree.strings(node.data(), "liste")) {
                ScriptNode c = ctx.scripts().get(child);
                if (c == null) continue;
                String guard = c.guard();
                if (guard == null || Conditions.eval(ctx, guard)) {
                    picked.add(child);
                    if (single) break;
                }
            }
            for (int i = picked.size() - 1; i >= 0; i--) queue.addFirst(picked.get(i));
        }

        private void quest(ScriptNode node) {
            String mode = node.str("mode");
            Quest quest = QuestRegistry.quest(node.str("quest"));
            if (mode == null || quest == null) return;
            OfflinePlayer player = ctx.player();
            switch (mode) {
                case "new" -> QuestEngine.unlock(player, quest);
                case "step" -> {
                    if (node.has("step")) QuestEngine.setStep(player, quest, node.integer("step", 0), false);
                }
                case "substep" -> {
                    if (node.has("step") && node.has("substep")) {
                        QuestEngine.progress(player, quest, node.integer("step", 0), node.integer("substep", 0),
                            node.integer("value", 1), op(node.str("operator", "add")));
                    }
                }
                case "variable" -> {
                    String variable = node.str("variable");
                    if (variable == null) return;
                    Object value = node.data().get("value");
                    if ("%%now%%".equals(value)) value = System.currentTimeMillis() / 1000L;
                    if (value instanceof Byte b) value = (int) b;
                    if (value != null) QuestEngine.setVariable(player, quest, variable, value, op(node.str("operator", "set")));
                }
                default -> { }
            }
        }

        private void variable(ScriptNode node) {
            String name = node.str("variable");
            if (name == null || !node.has("value")) return;
            name = name.replace("{p}", ctx.player().getUniqueId().toString());
            Object value = skriptValue(node.data().get("value"));
            String operator = node.str("operator", "set");
            Object old = Variables.getVariable(name, null, false);
            switch (operator) {
                case "add" -> Variables.setVariable(name, add(old, value, 1), null, false);
                case "remove" -> Variables.setVariable(name, add(old, value, -1), null, false);
                default -> Variables.setVariable(name, value, null, false);
            }
        }

        private void experience(ScriptNode node) {
            Player player = ctx.online();
            if (player == null || !node.has("value")) return;
            boolean points = node.bool("mode", false);
            int value = (int) Math.round(node.dbl("value", 0));
            String operator = node.str("operator", "add");
            if (points) {
                int total = player.getTotalExperience();
                int target = switch (operator) {
                    case "remove" -> Math.max(0, total - value);
                    case "set" -> Math.max(0, value);
                    default -> total + value;
                };
                player.setExp(0);
                player.setLevel(0);
                player.setTotalExperience(0);
                player.giveExp(target);
            } else {
                int level = switch (operator) {
                    case "remove" -> Math.max(0, player.getLevel() - value);
                    case "set" -> Math.max(0, value);
                    default -> player.getLevel() + value;
                };
                player.setLevel(level);
            }
        }

        private void give(ScriptNode node) {
            Player player = ctx.online();
            if (player == null) return;
            for (Map<String, Object> entry : Tree.maps(node.data(), "items")) {
                ItemStack stack = ItemResolver.read(entry, player);
                if (stack == null) continue;
                Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
                for (ItemStack rest : left.values()) player.getWorld().dropItem(player.getLocation(), rest);
            }
        }

        private void damage(ScriptNode node) {
            Player player = ctx.online();
            if (player == null) return;
            double value = node.dbl("value", 0);
            if (value <= 0) return;
            DamageSource source = DamageSource.builder(node.bool("mode", false) ? DamageType.OUT_OF_WORLD : DamageType.MOB_ATTACK).build();
            player.damage(value, source);
        }

        private void money(ScriptNode node) {
            Player player = ctx.online();
            OfflinePlayer target = ctx.player();
            String name = QuestSettings.moneyVariable().replace("{player}", String.valueOf(target.getName()));
            double value = node.dbl("value", 0);
            Object old = Variables.getVariable(name, null, false);
            double current = old instanceof Number n ? n.doubleValue() : 0;
            switch (node.str("operator", "add")) {
                case "add" -> {
                    Variables.setVariable(name, current + value, null, false);
                    if (player != null) player.sendMessage(Component.text("Vous avez reçu " + (long) value + " ₪ !", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                }
                case "remove" -> {
                    Variables.setVariable(name, Math.max(0, current - value), null, false);
                    if (player != null) player.sendMessage(Component.text("Vous avez perdu " + (long) value + " ₪ !", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
                case "set" -> Variables.setVariable(name, value, null, false);
                default -> { }
            }
        }

        private boolean move(ScriptNode node) {
            Player player = ctx.online();
            String npcId = node.str("npc");
            QuestNpc npc = npcId == null ? ctx.npc() : QuestRegistry.npc(npcId);
            if (player == null || npc == null || !node.has("position")) return false;
            NpcMover.Mode mode = NpcMover.Mode.parse(node.str("mode", "instant"));
            List<Object> raw = Tree.list(node.data(), "path");
            double[] path = new double[raw.size()];
            for (int i = 0; i < path.length; i++) path[i] = raw.get(i) instanceof Number n ? n.doubleValue() : 0;
            NpcMover.Options options = new NpcMover.Options();
            options.speed = node.integer("speed", 2);
            options.onStop = Tree.strings(node.data(), "on_stop");
            options.onContinue = Tree.strings(node.data(), "on_continue");
            options.path = path;
            boolean async = NpcMover.start(player, npc, node.integer("position", 1), mode, options, this::proceed);
            return async;
        }

        private void command(ScriptNode node) {
            String command = node.str("command");
            if (command == null) return;
            command = command.replace("{player}", String.valueOf(ctx.player().getName()));
            if ("player".equals(node.str("as", "console")) && ctx.online() != null) ctx.online().performCommand(command);
            else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }

        private void teleport(ScriptNode node) {
            Player player = ctx.online();
            World world = Bukkit.getWorld(node.str("world", ""));
            if (player == null || world == null) return;
            player.teleport(new Location(world, node.dbl("x", 0), node.dbl("y", 0), node.dbl("z", 0),
                (float) node.dbl("yaw", 0), (float) node.dbl("pitch", 0)));
        }
    }

    static QuestEngine.Op op(String operator) {
        return switch (operator) {
            case "add" -> QuestEngine.Op.ADD;
            case "remove" -> QuestEngine.Op.REMOVE;
            default -> QuestEngine.Op.SET;
        };
    }

    /** Les nombres de Skript sont des {@code Long} ou des {@code Double}. */
    private static Object skriptValue(Object value) {
        if (value instanceof Byte b) return (long) b;
        if (value instanceof Integer || value instanceof Short) return ((Number) value).longValue();
        if (value instanceof Float f) return f.doubleValue();
        return value;
    }

    private static Object add(Object old, Object delta, int sign) {
        if (delta instanceof Number d) {
            double base = old instanceof Number n ? n.doubleValue() : 0;
            double result = base + sign * d.doubleValue();
            return result == Math.rint(result) ? (Object) (long) result : (Object) result;
        }
        return delta;
    }

    // ── Dialogues ───────────────────────────────────────────────────────────

    /**
     * Affiche le dialogue courant de l'action {@code actionId} pour le joueur.
     *
     * @param onStart {@code true} quand le joueur vient de cliquer sur le NPC (le texte de reprise peut alors servir)
     */
    @SuppressWarnings("unchecked")
    public static void dialogue(ScriptContext ctx, String actionId, boolean onStart) {
        Player player = ctx.online();
        ScriptNode node = ctx.scripts().get(actionId);
        if (player == null || node == null) return;
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        Map<String, Object> dialogues = Tree.map(node.data(), "dialogues");

        String current = data.dialogues.get(actionId);
        boolean resume = onStart;
        if (current == null || !dialogues.containsKey(current)) {
            current = "start";
            resume = false;
        } else if (resume) {
            String start = Tree.str(Tree.map(dialogues, current), "on_start", current);
            if (!start.equals(current)) {
                current = start;
                resume = false;
            }
        }
        data.dialogues.put(actionId, current);
        data.markDirty();

        final String dialogueName = current;
        Map<String, Object> dialogue = Tree.map(dialogues, dialogueName);
        for (Map<String, Object> text : Tree.maps(dialogue, "texts")) {
            String condition = Tree.str(text, "condition", "true");
            if (!Conditions.eval(ctx, condition)) continue;

            String message = resume && text.containsKey("text_reprise") ? Tree.str(text, "text_reprise") : Tree.str(text, "text");
            if (message != null) {
                if (ctx.npc() != null) {
                    QuestDialogueStartEvent event = new QuestDialogueStartEvent(player, ctx.npc(), dialogueName);
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled()) return;
                }
                QuestNpc speaker = ctx.npc();
                if (text.containsKey("npc")) {
                    QuestNpc other = QuestRegistry.npc(Tree.str(text, "npc"));
                    if (other != null) speaker = other;
                }
                if (speaker == null) speaker = fallbackSpeaker();

                if (text.containsKey("next")) {
                    String next = Tree.str(text, "next");
                    long ticks = Math.round(Tree.dbl(text, "time", 0) * 20);
                    DialogueService.say(player, speaker, message, null, null);
                    Bukkit.getScheduler().runTaskLater(SkWhy.getInstance(), () -> {
                        PlayerQuestData d = QuestStore.get(player.getUniqueId());
                        if (dialogueName.equals(d.dialogues.get(actionId)) && player.isOnline()) {
                            d.dialogues.put(actionId, next);
                            d.markDirty();
                            dialogue(ctx, actionId, false);
                        }
                    }, ticks);
                } else {
                    List<DialogueService.Answer> answers = new ArrayList<>();
                    for (Map<String, Object> answer : Tree.maps(text, "answers")) {
                        if (!Conditions.eval(ctx, Tree.str(answer, "condition", "true"))) continue;
                        answers.add(new DialogueService.Answer(Tree.str(answer, "text", ""), Tree.str(answer, "hover"),
                            clicker -> answerClicked(ctx, actionId, answer)));
                    }
                    DialogueService.Options options = new DialogueService.Options();
                    options.distance = Tree.dbl(text, "distance", -1);
                    options.seconds = Tree.dbl(text, "time", -1);
                    options.inline = Tree.integer(text, "answer_mode", 1) == 2;
                    options.entity = ctx.entity();
                    DialogueService.say(player, speaker, message, answers, options);
                }
            }
            if (text.containsKey("action")) run(ctx, Tree.str(text, "action"), null);
            return;
        }
    }

    private static QuestNpc fallbackSpeaker() {
        return QuestNpc.parse("???", new java.util.LinkedHashMap<>());
    }

    private static void answerClicked(ScriptContext ctx, String actionId, Map<String, Object> answer) {
        Player player = ctx.online();
        if (player == null) return;
        String target = Tree.str(answer, "target");
        String targetAction = Tree.str(answer, "target_dialogue", actionId);
        if (target != null && !target.equals("<none>")) {
            PlayerQuestData data = QuestStore.get(player.getUniqueId());
            data.dialogues.put(targetAction, target);
            data.markDirty();
            dialogue(ctx, targetAction, false);
        }
        String action = Tree.str(answer, "action");
        if (action != null && !action.equals("<none>")) run(ctx, action, null);
    }

    // ── Marqueurs ───────────────────────────────────────────────────────────

    /**
     * Indicateur (index legacy 0 à 4) que l'action {@code main} du NPC affiche pour ce joueur, ou {@code -1}.
     * Parcourt les listes en évaluant leurs conditions, sans rien exécuter.
     */
    public static int point(ScriptContext ctx) {
        Deque<String> queue = new ArrayDeque<>();
        queue.add("main");
        int steps = 0;
        while (!queue.isEmpty() && steps++ < MAX_STEPS) {
            ScriptNode node = ctx.scripts().get(queue.pollFirst());
            if (node == null) continue;
            if (node.has("indicator") && !"list".equals(node.action())) return node.integer("indicator", -1);
            if ("list".equals(node.action())) {
                boolean single = "single".equals(node.str("mode", "list"));
                List<String> picked = new ArrayList<>();
                for (String child : Tree.strings(node.data(), "liste")) {
                    ScriptNode c = ctx.scripts().get(child);
                    if (c == null) continue;
                    String guard = c.guard();
                    if (guard == null || Conditions.eval(ctx, guard)) {
                        picked.add(child);
                        if (single) break;
                    }
                }
                for (int i = picked.size() - 1; i >= 0; i--) queue.addFirst(picked.get(i));
            }
        }
        return -1;
    }
}
