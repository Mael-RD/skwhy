package skwhy.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import skwhy.modules.QuestElements.events.QuestFinishEvent;
import skwhy.modules.QuestElements.events.QuestObjectiveCompleteEvent;
import skwhy.modules.QuestElements.events.QuestObjectiveProgressEvent;
import skwhy.modules.QuestElements.events.QuestRewardClaimEvent;
import skwhy.modules.QuestElements.events.QuestSelectEvent;
import skwhy.modules.QuestElements.events.QuestStepChangeEvent;
import skwhy.modules.QuestElements.events.QuestUnlockEvent;
import skwhy.modules.QuestElements.events.QuestVariableChangeEvent;
import skwhy.quest.data.LegacyPlayerData;
import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestProgress;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.ObjectiveType;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestObjective;
import skwhy.quest.model.QuestStatus;
import skwhy.quest.model.QuestStep;
import skwhy.quest.npc.CompassService;
import skwhy.quest.npc.IndicatorService;
import skwhy.quest.script.Conditions;
import skwhy.quest.script.ItemResolver;
import skwhy.quest.script.QuestText;
import skwhy.quest.script.ScriptContext;
import skwhy.quest.script.ScriptEngine;

/**
 * Moteur de quêtes : débloquer, changer d'étape, faire progresser les objectifs, variables, récompenses.
 * Tous les index d'étapes et d'objectifs sont 0-based ici ; les syntaxes Skript font la conversion.
 */
public final class QuestEngine {

    public enum Op { SET, ADD, REMOVE }

    private static final String[] JOBS = {"chasseur", "mineur", "bucheron", "fermier", "pecheur", "explorateur", "contrebandier"};

    private QuestEngine() {}

    // ── Statut ──────────────────────────────────────────────────────────────

    public static QuestStatus status(OfflinePlayer player, Quest quest) {
        return QuestStore.get(player.getUniqueId()).status(quest.id());
    }

    /** Étape courante (0-based) ou {@code -1} si la quête n'est pas commencée. */
    public static int step(OfflinePlayer player, Quest quest) {
        QuestProgress p = QuestStore.get(player.getUniqueId()).quests.get(quest.id());
        return p == null ? -1 : p.step;
    }

    // ── Déblocage / étapes ──────────────────────────────────────────────────

    /** Débloque la quête et la place sur sa première étape. {@code false} si elle est déjà connue ou refusée. */
    public static boolean unlock(OfflinePlayer player, Quest quest) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        if (data.quests.containsKey(quest.id())) return false;
        QuestUnlockEvent event = new QuestUnlockEvent(player, quest);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        QuestProgress p = new QuestProgress();
        p.status = QuestStatus.AVAILABLE;
        data.quests.put(quest.id(), p);
        if (!data.categories.contains(quest.category())) data.categories.add(quest.category());
        data.markDirty();

        Player online = player.getPlayer();
        if (online != null && QuestSettings.notifications()) {
            Component title = Component.text("Nouvelle quête", colorOf(quest));
            online.showTitle(Title.title(title, QuestText.component(quest.name(), player),
                Title.Times.times(java.time.Duration.ofMillis(250), java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(250))));
            online.playSound(online.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
        }
        setStep(player, quest, 0, true);
        return true;
    }

    /** Remet la quête à zéro : le joueur la considère de nouveau comme inconnue. */
    public static void reset(OfflinePlayer player, Quest quest) {
        setStep(player, quest, -1, true);
    }

    /**
     * Place le joueur sur l'étape {@code target} (0-based, {@code -1} pour réinitialiser). Les étapes dont la condition
     * est fausse sont sautées ; une étape inexistante ou sans objectif termine la quête.
     */
    public static boolean setStep(OfflinePlayer player, Quest quest, int target, boolean silent) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        QuestProgress p = data.quests.get(quest.id());
        int past = p == null ? -1 : p.step;
        if (target == -1 && p == null) return false;
        if (target == past && target != -1 && p != null) return false;

        QuestStepChangeEvent event = new QuestStepChangeEvent(player, quest, past, target);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        if (target == -1) {
            data.quests.remove(quest.id());
            if (quest.id().equals(data.selected)) data.selected = null;
            data.markDirty();
            reindex(data);
            refresh(player);
            return true;
        }

        if (p == null) {
            p = new QuestProgress();
            data.quests.put(quest.id(), p);
        }
        ScriptContext ctx = ScriptContext.forQuest(player, quest);
        int step = target;
        QuestStep def = quest.step(step);
        while (def != null && def.condition() != null && !Conditions.eval(ctx, def.condition())) {
            step++;
            def = quest.step(step);
        }
        p.step = step;
        p.progress.clear();
        data.markDirty();

        boolean finished = def == null || def.objectives().isEmpty();
        Player online = player.getPlayer();
        if (finished) {
            p.status = QuestStatus.FINISHED;
            reindex(data);
            if (online != null && !silent && QuestSettings.notifications()) {
                notifyPlayer(online, "Quête terminée !", quest, Sound.ENTITY_PLAYER_LEVELUP);
            }
            Bukkit.getPluginManager().callEvent(new QuestFinishEvent(player, quest));
            refresh(player);
            recheckSpecials(player, "quest");
            return true;
        }

        // « available » : débloquée mais encore sans progression ; « started » dès la première progression ou étape suivante
        p.status = target == 0 && past == -1 ? QuestStatus.AVAILABLE : QuestStatus.STARTED;
        reindex(data);
        if (online != null && !silent && QuestSettings.notifications() && past != -1) {
            notifyPlayer(online, "Quête mise à jour !", quest, Sound.BLOCK_AMETHYST_BLOCK_PLACE);
        }
        refresh(player);
        // Les objectifs spéciaux déjà satisfaits se valident immédiatement
        checkSpecials(player, quest, null);
        return true;
    }

    /** Passe à l'étape suivante (champ {@code next} de l'étape courante). */
    public static boolean advance(OfflinePlayer player, Quest quest) {
        QuestProgress p = QuestStore.get(player.getUniqueId()).quests.get(quest.id());
        if (p == null) return unlock(player, quest);
        QuestStep def = quest.step(p.step);
        if (def == null) return false;
        return setStep(player, quest, def.next(), false);
    }

    // ── Objectifs ───────────────────────────────────────────────────────────

    /** Progression de l'objectif de l'étape courante (nombre accompli, plafonné au total demandé). */
    public static int progress(OfflinePlayer player, Quest quest, int objective) {
        QuestProgress p = QuestStore.get(player.getUniqueId()).quests.get(quest.id());
        if (p == null) return 0;
        QuestStep def = quest.step(p.step);
        if (def == null || objective < 0 || objective >= def.objectives().size()) return 0;
        return p.count(objective, def.objectives().get(objective).count());
    }

    /**
     * Fait progresser un objectif de l'étape {@code step} (qui doit être l'étape courante du joueur).
     *
     * @return {@code true} si la progression a été modifiée
     */
    public static boolean progress(OfflinePlayer player, Quest quest, int step, int objective, int value, Op op) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        QuestProgress p = data.quests.get(quest.id());
        if (p == null || p.step != step) return false;
        QuestStep def = quest.step(step);
        if (def == null || objective < 0 || objective >= def.objectives().size()) return false;
        QuestObjective o = def.objectives().get(objective);
        int max = o.count();
        boolean done = p.isDone(objective);
        int current = p.count(objective, max);

        int target;
        switch (op) {
            case ADD -> {
                if (done) return false;
                target = current + value;
            }
            case REMOVE -> target = current - value;
            default -> target = value;
        }
        target = Math.max(0, target);
        if (target == current && !(done && target < max)) return false;

        if (p.status == QuestStatus.AVAILABLE) p.status = QuestStatus.STARTED;
        QuestObjectiveProgressEvent event = new QuestObjectiveProgressEvent(player, quest, o, current, Math.min(target, max));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        data.markDirty();

        if (target >= max) {
            p.progress.put(objective, Boolean.TRUE);
            Bukkit.getPluginManager().callEvent(new QuestObjectiveCompleteEvent(player, quest, o));
            boolean all = true;
            for (QuestObjective other : def.objectives()) {
                if (!p.isDone(other.index())) { all = false; break; }
            }
            if (all) {
                setStep(player, quest, def.next(), false);
            } else {
                Player online = player.getPlayer();
                if (online != null && QuestSettings.notifications()) {
                    notifyPlayer(online, "Quête avancée !", quest, Sound.BLOCK_AMETHYST_BLOCK_PLACE);
                }
                refresh(player);
            }
        } else {
            p.progress.put(objective, target);
            refresh(player);
        }
        return true;
    }

    /** Termine l'objectif de l'étape courante. */
    public static boolean complete(OfflinePlayer player, Quest quest, int objective) {
        QuestProgress p = QuestStore.get(player.getUniqueId()).quests.get(quest.id());
        if (p == null) return false;
        QuestStep def = quest.step(p.step);
        if (def == null || objective < 0 || objective >= def.objectives().size()) return false;
        return progress(player, quest, p.step, objective, def.objectives().get(objective).count(), Op.SET);
    }

    // ── Écouteurs d'objectifs ───────────────────────────────────────────────

    /** Reconstruit l'index des objectifs actifs (étape courante de chaque quête commencée). */
    public static void reindex(PlayerQuestData data) {
        PlayerQuestData.ActiveIndex index = new PlayerQuestData.ActiveIndex();
        for (Map.Entry<String, QuestProgress> e : data.quests.entrySet()) {
            QuestProgress p = e.getValue();
            if (p.status != QuestStatus.STARTED && p.status != QuestStatus.AVAILABLE) continue;
            Quest quest = QuestRegistry.quest(e.getKey());
            if (quest == null) continue;
            QuestStep def = quest.step(p.step);
            if (def == null) continue;
            for (QuestObjective o : def.objectives()) {
                switch (o.type()) {
                    case ACTION -> index.actions.computeIfAbsent(o.key(), k -> new ArrayList<>()).add(o);
                    case ITEM -> index.items.add(o);
                    case SPECIAL -> index.specials.computeIfAbsent(o.special(), k -> new ArrayList<>()).add(o);
                    default -> { }
                }
            }
        }
        data.index = index;
    }

    /** Une action vient d'avoir lieu (casser un bloc, tuer un mob...) : fait avancer les objectifs {@code action} concernés. */
    public static void triggerAction(OfflinePlayer player, String key, int amount) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        List<QuestObjective> list = data.index.actions.get(key);
        if (list == null || list.isEmpty()) return;
        for (QuestObjective o : new ArrayList<>(list)) {
            QuestProgress p = data.quests.get(o.quest().id());
            if (p == null || p.step != o.step() || p.isDone(o.index())) continue;
            progress(player, o.quest(), o.step(), o.index(), amount, Op.ADD);
        }
    }

    /** Recompte les items des objectifs {@code item} du joueur (l'inventaire vient de changer). */
    public static void recheckItems(Player player) {
        PlayerQuestData data = QuestStore.getIfLoaded(player.getUniqueId());
        if (data == null || data.index.items.isEmpty()) return;
        for (QuestObjective o : new ArrayList<>(data.index.items)) {
            QuestProgress p = data.quests.get(o.quest().id());
            if (p == null || p.step != o.step()) continue;
            int count = ItemResolver.count(player, o.key());
            int current = p.count(o.index(), o.count());
            if (count == current || (p.isDone(o.index()) && count >= o.count())) continue;
            progress(player, o.quest(), o.step(), o.index(), count, Op.SET);
        }
    }

    /** Revérifie les objectifs spéciaux d'un type ({@code jobs}, {@code rank}, {@code advancement}, {@code variable}, {@code quest}). */
    public static void recheckSpecials(OfflinePlayer player, String type) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        List<QuestObjective> list = type == null ? allSpecials(data) : data.index.specials.get(type);
        if (list == null || list.isEmpty()) return;
        for (QuestObjective o : new ArrayList<>(list)) {
            QuestProgress p = data.quests.get(o.quest().id());
            if (p == null || p.step != o.step()) continue;
            checkSpecial(player, o);
        }
    }

    private static List<QuestObjective> allSpecials(PlayerQuestData data) {
        List<QuestObjective> out = new ArrayList<>();
        data.index.specials.values().forEach(out::addAll);
        return out;
    }

    private static void checkSpecials(OfflinePlayer player, Quest quest, String type) {
        QuestProgress p = QuestStore.get(player.getUniqueId()).quests.get(quest.id());
        if (p == null) return;
        QuestStep def = quest.step(p.step);
        if (def == null) return;
        int step = p.step;
        for (QuestObjective o : new ArrayList<>(def.objectives())) {
            if (o.type() != ObjectiveType.SPECIAL) continue;
            if (type != null && !type.equals(o.special())) continue;
            checkSpecial(player, o);
            QuestProgress now = QuestStore.get(player.getUniqueId()).quests.get(quest.id());
            if (now == null || now.step != step) return; // l'étape a changé : le reste est obsolète
        }
    }

    private static void checkSpecial(OfflinePlayer player, QuestObjective o) {
        UUID uuid = player.getUniqueId();
        Map<String, Object> d = o.data();
        Quest quest = o.quest();
        int value = -1;
        switch (o.special()) {
            case "jobs" -> {
                boolean hasCount = d.containsKey("count");
                String job = Tree.str(d, "jobs");
                if (hasCount && job != null) {
                    if (job.equals("sum")) {
                        int sum = 0;
                        for (String j : JOBS) sum += jobLevel(uuid, j);
                        value = sum;
                    } else {
                        value = jobLevel(uuid, job);
                    }
                } else if (hasCount) {
                    int max = 0;
                    for (String j : JOBS) max = Math.max(max, jobLevel(uuid, j));
                    value = max;
                } else if (job != null) {
                    value = jobActive(uuid, job) ? 1 : 0;
                } else {
                    value = 0;
                    for (String j : JOBS) if (jobActive(uuid, j)) { value = 1; break; }
                }
            }
            case "quest" -> {
                List<String> list = Tree.strings(d, "list");
                boolean all = !list.isEmpty();
                for (String id : list) {
                    if (QuestStore.get(uuid).status(id) != QuestStatus.FINISHED) { all = false; break; }
                }
                value = all ? 1 : 0;
            }
            case "variable" -> value = (int) Conditions.variableNumber(player, quest.id(), Tree.str(d, "variable", ""));
            case "advancement" -> {
                Player online = player.getPlayer();
                if (online == null) return;
                NamespacedKey key = NamespacedKey.fromString(Tree.str(d, "advancement", ""));
                Advancement adv = key == null ? null : Bukkit.getAdvancement(key);
                value = adv != null && online.getAdvancementProgress(adv).isDone() ? 1 : 0;
            }
            case "rank" -> {
                if (player.getPlayer() == null) return;
                int target = Math.max(1, Tree.integer(d, "rank", 1));
                value = Conditions.rank(player) >= target ? 1 : 0;
            }
            default -> { return; }
        }
        if (value < 0) return;
        QuestProgress p = QuestStore.get(uuid).quests.get(quest.id());
        if (p == null || p.isDone(o.index())) return;
        if (value == p.count(o.index(), o.count()) && value < o.count()) return;
        progress(player, quest, o.step(), o.index(), value, Op.SET);
    }

    private static int jobLevel(UUID uuid, String job) {
        Object v = LegacyPlayerData.path(uuid, "jobs;" + job + ";level");
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static boolean jobActive(UUID uuid, String job) {
        return Tree.toBool(LegacyPlayerData.path(uuid, "jobs;" + job + ";actif"), false);
    }

    // ── Variables ───────────────────────────────────────────────────────────

    /** Valeur d'une variable de quête ou {@code null}. */
    public static Object variable(OfflinePlayer player, String questId, String name) {
        QuestProgress p = QuestStore.get(player.getUniqueId()).quests.get(questId);
        return p == null ? null : p.variables.get(name);
    }

    public static boolean setVariable(OfflinePlayer player, Quest quest, String name, Object value, Op op) {
        if (!quest.hasVariable(name)) return false;
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        QuestProgress p = data.quests.get(quest.id());
        if (p == null) {
            if (op == Op.SET && value == null) return false;
            p = new QuestProgress();
            data.quests.put(quest.id(), p);
        }
        Object old = p.variables.get(name);
        Object result = value;
        if (op != Op.SET) {
            double base = old instanceof Number n ? n.doubleValue() : 0;
            double delta = value instanceof Number n ? n.doubleValue() : 0;
            result = normalize(op == Op.ADD ? base + delta : base - delta);
        } else {
            result = normalize(value);
        }
        if (result == null ? old == null : result.equals(old)) return false;

        QuestVariableChangeEvent event = new QuestVariableChangeEvent(player, quest, name, old, result);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        if (result == null) p.variables.remove(name); else p.variables.put(name, result);
        data.markDirty();
        recheckSpecials(player, "variable");
        return true;
    }

    public static boolean deleteVariable(OfflinePlayer player, Quest quest, String name) {
        return setVariable(player, quest, name, null, Op.SET);
    }

    /** Entier si la valeur est entière, sinon décimal ; texte et booléens inchangés. */
    public static Object normalize(Object value) {
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE) return (int) d;
            return d;
        }
        return value;
    }

    public static String format(Object value) {
        if (value instanceof Double d && d == Math.rint(d)) return String.valueOf(d.longValue());
        return String.valueOf(value);
    }

    // ── Récompense / sélection ──────────────────────────────────────────────

    public static boolean rewardClaimed(OfflinePlayer player, Quest quest) {
        QuestProgress p = QuestStore.get(player.getUniqueId()).quests.get(quest.id());
        return p != null && p.rewardClaimed;
    }

    public static void setRewardClaimed(OfflinePlayer player, Quest quest, boolean claimed) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        QuestProgress p = data.quests.get(quest.id());
        if (p == null) return;
        p.rewardClaimed = claimed;
        data.markDirty();
    }

    /** Donne la récompense d'une quête terminée (une seule fois). */
    public static boolean claimReward(OfflinePlayer player, Quest quest) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        QuestProgress p = data.quests.get(quest.id());
        if (p == null || p.status != QuestStatus.FINISHED || p.rewardClaimed) return false;
        if (!quest.script().has("reward")) return false;
        QuestRewardClaimEvent event = new QuestRewardClaimEvent(player, quest);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        p.rewardClaimed = true;
        data.markDirty();
        ScriptEngine.run(ScriptContext.forQuest(player, quest), "reward", null);
        return true;
    }

    public static Quest selected(OfflinePlayer player) {
        return QuestRegistry.quest(QuestStore.get(player.getUniqueId()).selected);
    }

    public static void select(OfflinePlayer player, Quest quest) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        Quest past = QuestRegistry.quest(data.selected);
        if (past == quest) return;
        data.selected = quest == null ? null : quest.id();
        data.markDirty();
        Bukkit.getPluginManager().callEvent(new QuestSelectEvent(player, quest, past));
        refresh(player);
    }

    // ── Notifications / rafraîchissements ───────────────────────────────────

    private static net.kyori.adventure.text.format.TextColor colorOf(Quest quest) {
        return switch (quest.type()) {
            case "secondaire" -> net.kyori.adventure.text.format.TextColor.color(0x44e1eb);
            case "contrée" -> net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE;
            default -> net.kyori.adventure.text.format.TextColor.color(0xff8800);
        };
    }

    private static void notifyPlayer(Player online, String text, Quest quest, Sound sound) {
        online.sendActionBar(Component.text(text, colorOf(quest)).append(Component.text(" ")).append(QuestText.component(quest.name(), online)));
        online.playSound(online.getLocation(), sound, 1f, 1f);
    }

    /** Met à jour tout ce qui dépend de la progression : boussole et marqueurs de NPC. */
    public static void refresh(OfflinePlayer player) {
        CompassService.invalidate(player.getUniqueId());
        IndicatorService.invalidate(player.getUniqueId());
    }
}
