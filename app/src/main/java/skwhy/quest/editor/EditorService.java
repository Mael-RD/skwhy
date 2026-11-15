package skwhy.quest.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.sun.net.httpserver.HttpContext;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import skwhy.SkWhy;
import skwhy.customitem.CustomItemDef;
import skwhy.customitem.CustomItemManager;
import skwhy.quest.QuestRegistry;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;
import skwhy.request.HttpHost;

/**
 * Éditeur web des quêtes et des NPC. Un script donne un lien à un joueur ({@link #sendLink}) ; le navigateur modifie les
 * définitions et les envoie d'un coup ; le joueur les valide ou les annule en jeu (survol = récapitulatif), puis peut
 * renouveler le lien. Un joueur absent à ce moment retrouve les modifications à sa reconnexion.
 *
 * <p>Ce système ne dépend que du serveur HTTP partagé ({@link HttpHost}) et de lecture/écriture du registre des quêtes :
 * il a ses propres clefs et ne passe pas par l'API REST.
 */
public final class EditorService {

    static final String PATH = "/editor";

    private static final EditorSessions SESSIONS = new EditorSessions();
    private static final EditorData DATA = new EditorData(new RegistrySource());
    /** Modifications reçues dont le joueur (en ligne) n'a pas encore répondu. */
    private static final Map<String, ChangeSet> WAITING = new ConcurrentHashMap<>();

    private static boolean started;
    private static HttpContext context;
    private static BukkitTask purgeTask;
    private static EditorListener listener;

    private EditorService() {}

    private static Logger log() {
        return SkWhy.getInstance().getLogger();
    }

    static EditorSessions sessions() { return SESSIONS; }
    static EditorData data() { return DATA; }

    public static boolean isStarted() { return started; }

    // ── Cycle de vie ────────────────────────────────────────────────────────

    public static synchronized void start() {
        if (started || !EditorSettings.enabled()) return;
        if (!HttpHost.acquire("editor")) {
            log().warning("[QuestEditor] Serveur HTTP indisponible : l'éditeur web est désactivé.");
            return;
        }
        context = HttpHost.createContext(PATH, new EditorHttp());
        SkWhy plugin = SkWhy.getInstance();
        listener = new EditorListener();
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        purgeTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, SESSIONS::purge, 1200L, 1200L);
        started = true;
        log().info("[QuestEditor] Éditeur web disponible sur " + EditorSettings.baseUrl() + PATH + "/<clef>/");
    }

    public static synchronized void stop() {
        if (!started) return;
        started = false;
        for (ChangeSet cs : List.copyOf(WAITING.values())) persist(cs);
        WAITING.clear();
        if (purgeTask != null) purgeTask.cancel();
        purgeTask = null;
        if (listener != null) org.bukkit.event.HandlerList.unregisterAll(listener);
        listener = null;
        HttpHost.removeContext(context);
        context = null;
        SESSIONS.clear();
        DATA.clear();
        HttpHost.release("editor");
    }

    // ── Liens ───────────────────────────────────────────────────────────────

    /**
     * Génère une nouvelle clef pour la quête ou le NPC {@code id} et envoie le lien au joueur.
     *
     * @param kind {@code quest} ou {@code npc}
     * @return {@code false} si l'éditeur est indisponible ou si la cible n'existe pas
     */
    public static boolean sendLink(Player player, String kind, String id) {
        return sendLink(player, kind, id, null);
    }

    /**
     * Comme {@link #sendLink(Player, String, String)}, mais la page s'ouvre directement sur la fenêtre du dialogue {@code dialogue}
     * (identifiant de l'action de type dialogue dans le script de la quête ou du NPC ; fragment {@code #dialogue=...} de l'adresse).
     *
     * @return {@code false} si l'éditeur est indisponible, si la cible n'existe pas ou si elle n'a pas ce dialogue
     */
    public static boolean sendLink(Player player, String kind, String id, String dialogue) {
        if (!started || (!isMenu(kind) && DATA.raw(kind, id) == null)) return false;
        if (dialogue != null && !hasDialogue(kind, id, dialogue)) return false;
        sendNewLink(player.getUniqueId(), player.getName(), kind, id, null, dialogue);
        return true;
    }

    /** Vrai si le script de la quête / du NPC contient une action de type dialogue portant cet identifiant. */
    public static boolean hasDialogue(String kind, String id, String dialogue) {
        java.util.Map<String, Object> raw = isMenu(kind) ? null : DATA.raw(kind, id);
        if (raw == null || !(raw.get("script") instanceof java.util.Map<?, ?> script)) return false;
        return script.get(dialogue) instanceof java.util.Map<?, ?> node && "dialogue".equals(node.get("action"));
    }

    private static void sendNewLink(UUID uuid, String name, String kind, String id, EditorSession replaced) {
        sendNewLink(uuid, name, kind, id, replaced, null);
    }

    private static void sendNewLink(UUID uuid, String name, String kind, String id, EditorSession replaced, String dialogue) {
        EditorSessions.Issued issued = SESSIONS.issue(kind, id, uuid, name, EditorSettings.keyTtlMillis());
        if (replaced != null) replaced.renewedKey = issued.key();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        String url = url(issued.key()) + (dialogue == null ? "" : "#dialogue=" + java.net.URLEncoder.encode(dialogue, java.nio.charset.StandardCharsets.UTF_8));
        player.sendMessage(EditorMessages.link(displayName(kind, id) + (dialogue == null ? "" : " (dialogue)"), url, EditorSettings.keyTtlMillis() / 60_000L, replaced != null));
    }

    /**
     * Génère une clef pour un joueur (connecté ou non) et renvoie l'adresse, sans rien envoyer : pour la console.
     *
     * @return l'adresse, ou {@code null} si l'éditeur est indisponible ou la cible introuvable
     */
    public static String createLink(String kind, String id, UUID player, String playerName) {
        return createLink(kind, id, player, playerName, null);
    }

    /** Idem, avec ouverture directe sur la fenêtre d'un dialogue ({@code null} : pas de dialogue). */
    public static String createLink(String kind, String id, UUID player, String playerName, String dialogue) {
        if (!started || (!isMenu(kind) && DATA.raw(kind, id) == null)) return null;
        if (dialogue != null && !hasDialogue(kind, id, dialogue)) return null;
        return url(SESSIONS.issue(kind, id, player, playerName, EditorSettings.keyTtlMillis()).key())
            + (dialogue == null ? "" : "#dialogue=" + java.net.URLEncoder.encode(dialogue, java.nio.charset.StandardCharsets.UTF_8));
    }

    static String url(String key) {
        return EditorSettings.baseUrl() + PATH + "/" + key + "/";
    }

    /** Un lien « menu » n'ouvre ni quête ni NPC : le navigateur propose la liste. */
    static boolean isMenu(String kind) {
        return kind == null || kind.isEmpty();
    }

    private static String displayName(String kind, String id) {
        if (isMenu(kind)) return "quêtes et NPC";
        if ("npc".equals(kind)) {
            QuestNpc npc = QuestRegistry.npc(id);
            return "NPC " + EditorData.plain(npc == null ? id : npc.displayName());
        }
        Quest quest = QuestRegistry.quest(id);
        return "la quête " + EditorData.plain(quest == null ? id : quest.name());
    }

    // ── Envoi des modifications (thread HTTP) ───────────────────────────────

    /** Une quête ou un NPC reçu du navigateur. */
    record Incoming(String kind, String id, String base, Map<String, Object> tree) {}

    record Response(int status, Map<String, Object> body) {
        static Response error(int status, String message) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", false);
            m.put("error", message);
            return new Response(status, m);
        }
    }

    @SuppressWarnings("unchecked")
    static Response submit(EditorSession session, Object parsed) {
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("entries") instanceof List<?> list)) return Response.error(400, "Requête invalide.");
        boolean force = Boolean.TRUE.equals(root.get("force"));
        List<Incoming> incoming = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m) || !(m.get("tree") instanceof Map<?, ?> tree)) return Response.error(400, "Entrée invalide.");
            String kind = String.valueOf(m.get("kind"));
            String id = String.valueOf(m.get("id"));
            if (!kind.equals("quest") && !kind.equals("npc")) return Response.error(400, "Type inconnu : " + kind);
            if (DATA.raw(kind, id) == null) return Response.error(400, "Introuvable : " + id);
            incoming.add(new Incoming(kind, id, String.valueOf(m.get("base")), (Map<String, Object>) tree));
        }
        return submit(session, incoming, force);
    }

    static synchronized Response submit(EditorSession session, List<Incoming> incoming, boolean force) {
        if (session.state != EditorSession.State.OPEN) return Response.error(409, "Les modifications ont déjà été envoyées.");
        List<ChangeSet.Entry> entries = new ArrayList<>();
        List<Object> conflicts = new ArrayList<>();
        for (Incoming in : incoming) {
            try {
                if (in.kind().equals("npc")) QuestNpc.parse(in.id(), in.tree());
                else Quest.parse(in.id(), in.tree());
            } catch (RuntimeException e) {
                return Response.error(400, "Définition invalide pour " + in.id() + " : " + e.getMessage());
            }
            Map<String, Object> current = DATA.raw(in.kind(), in.id());
            TreeDiff.Stat stat = TreeDiff.diff(current, in.tree());
            if (stat.total() == 0) continue;
            if (!force && !EditorJson.version(current).equals(in.base())) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("kind", in.kind());
                c.put("id", in.id());
                conflicts.add(c);
                continue;
            }
            entries.add(new ChangeSet.Entry(in.kind(), in.id(), EditorJson.version(current), in.tree(), stat, TreeDiff.changedSections(current, in.tree())));
        }
        if (!conflicts.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("error", "conflict");
            body.put("conflicts", conflicts);
            return new Response(409, body);
        }
        if (entries.isEmpty()) return Response.error(400, "Aucune modification à enregistrer.");

        ChangeSet cs = new ChangeSet(UUID.randomUUID().toString(), session.player, session.id, session.kind, session.targetId,
            SESSIONS.now(), entries);
        session.changes = cs;
        session.state = EditorSession.State.SUBMITTED;
        session.expiresAt = SESSIONS.now() + EditorSettings.keyTtlMillis();
        Bukkit.getScheduler().runTask(SkWhy.getInstance(), () -> deliver(cs));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("online", Bukkit.getPlayer(session.player) != null);
        body.put("entries", entrySummaries(cs));
        return new Response(200, body);
    }

    static List<Object> entrySummaries(ChangeSet cs) {
        List<Object> out = new ArrayList<>();
        for (ChangeSet.Entry e : cs.entries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", e.kind());
            m.put("id", e.id());
            m.put("added", e.stat().added());
            m.put("removed", e.stat().removed());
            m.put("modified", e.stat().modified());
            m.put("sections", e.sections());
            out.add(m);
        }
        return out;
    }

    /** État de la session pour le navigateur. */
    static Map<String, Object> status(EditorSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", s.kind);
        m.put("id", s.targetId);
        m.put("player", s.playerName);
        m.put("state", s.state.name().toLowerCase());
        m.put("expiresAt", s.expiresAt);
        m.put("now", SESSIONS.now());
        m.put("online", Bukkit.getPlayer(s.player) != null);
        m.put("renewedKey", s.renewedKey);
        ChangeSet cs = s.changes;
        if (cs != null) m.put("entries", entrySummaries(cs));
        return m;
    }

    // ── Réponse du joueur (thread principal) ────────────────────────────────

    private static void deliver(ChangeSet cs) {
        Player player = Bukkit.getPlayer(cs.player);
        if (player == null) {
            persist(cs);
            return;
        }
        WAITING.put(cs.id, cs);
        prompt(cs, player, "Modifications reçues depuis l'éditeur : ");
    }

    private static void prompt(ChangeSet cs, Player player, String lead) {
        Duration lifetime = Duration.ofHours(12);
        Component line = EditorMessages.summary(cs, lead);
        Component buttons = EditorMessages.buttons(
            EditorMessages.button("✔ Valider", NamedTextColor.GREEN, "Écrire les modifications dans les fichiers", lifetime,
                () -> validate(cs, player, false)),
            EditorMessages.button("✘ Annuler", NamedTextColor.RED, "Jeter les modifications", lifetime,
                () -> cancel(cs, player)));
        player.sendMessage(Component.newline().append(line).appendNewline().append(Component.text("   ")).append(buttons));
    }

    private static void validate(ChangeSet cs, CommandSender player, boolean force) {
        if (player instanceof Player p && !p.getUniqueId().equals(cs.player)) return;
        if (!cs.answered.compareAndSet(false, true)) {
            player.sendMessage(EditorMessages.info("Ces modifications ont déjà été traitées."));
            return;
        }
        EditorApplier.Result result = EditorApplier.apply(cs, DATA, force);
        if (!result.conflicts().isEmpty()) {
            cs.answered.set(false);
            StringBuilder names = new StringBuilder();
            for (ChangeSet.Entry e : result.conflicts()) names.append(names.isEmpty() ? "" : ", ").append(e.id());
            Duration lifetime = Duration.ofHours(12);
            player.sendMessage(Component.newline().append(EditorMessages.error("Conflit : " + names
                + " a été modifié depuis l'ouverture de l'éditeur. Écraser la version actuelle ?"))
                .appendNewline().append(Component.text("   ")).append(EditorMessages.buttons(
                    EditorMessages.button("Écraser", NamedTextColor.GOLD, "Remplacer la version actuelle par celle de l'éditeur", lifetime,
                        () -> validate(cs, player, true)),
                    EditorMessages.button("✘ Annuler", NamedTextColor.RED, "Jeter les modifications", lifetime,
                        () -> cancel(cs, player)))));
            return;
        }
        forget(cs);
        if (!result.failed().isEmpty()) {
            player.sendMessage(EditorMessages.error("Écriture impossible pour " + result.failed().size() + " fichier(s), voir la console."));
        }
        player.sendMessage(EditorMessages.prefix().append(Component.text(result.applied().size() + " fichier(s) enregistré(s) et rechargé(s).", NamedTextColor.GREEN)));
        finish(cs, EditorSession.State.APPLIED);
        askRenew(cs, player);
    }

    private static void cancel(ChangeSet cs, CommandSender player) {
        if (player instanceof Player p && !p.getUniqueId().equals(cs.player)) return;
        if (!cs.answered.compareAndSet(false, true)) {
            player.sendMessage(EditorMessages.info("Ces modifications ont déjà été traitées."));
            return;
        }
        forget(cs);
        player.sendMessage(EditorMessages.info("Modifications annulées, rien n'a été écrit."));
        finish(cs, EditorSession.State.CANCELLED);
        askRenew(cs, player);
    }

    private static void forget(ChangeSet cs) {
        WAITING.remove(cs.id);
        deletePending(cs);
    }

    /** La clef du navigateur reste valable le temps de la renouveler, puis expire. */
    private static void finish(ChangeSet cs, EditorSession.State state) {
        EditorSession session = SESSIONS.findById(cs.sessionId);
        if (session == null) return;
        session.state = state;
        session.expiresAt = SESSIONS.now() + EditorSettings.renewMillis();
    }

    private static void askRenew(ChangeSet cs, CommandSender who) {
        if (!(who instanceof Player player)) return; // la console n'a pas de lien à renouveler
        Duration lifetime = Duration.ofMillis(EditorSettings.renewMillis());
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        Component line = EditorMessages.prefix().append(Component.text("Renouveler le lien de l'éditeur ?", NamedTextColor.GRAY));
        Component buttons = EditorMessages.buttons(
            EditorMessages.button("↻ Renouveler", NamedTextColor.GREEN, "Générer un nouveau lien (l'ancien est remplacé)", lifetime, () -> {
                if (!done.compareAndSet(false, true)) return;
                renew(cs, player);
            }),
            EditorMessages.button("Ne pas renouveler", NamedTextColor.GRAY, "Fermer le lien de l'éditeur", lifetime, () -> {
                if (!done.compareAndSet(false, true)) return;
                EditorSession old = SESSIONS.findById(cs.sessionId);
                SESSIONS.invalidate(old);
                player.sendMessage(EditorMessages.info("Lien de l'éditeur fermé."));
            }));
        player.sendMessage(line.appendNewline().append(Component.text("   ")).append(buttons));
    }

    private static void renew(ChangeSet cs, Player player) {
        if (!isMenu(cs.targetKind) && DATA.raw(cs.targetKind, cs.targetId) == null) {
            player.sendMessage(EditorMessages.error("Cette quête ou ce NPC n'existe plus."));
            return;
        }
        sendNewLink(cs.player, player.getName(), cs.targetKind, cs.targetId, SESSIONS.findById(cs.sessionId));
    }

    // ── Joueur absent ───────────────────────────────────────────────────────

    private static File pendingFile(ChangeSet cs) {
        return new File(new File(EditorSettings.pendingFolder(), cs.player.toString()), cs.id + ".json");
    }

    static void persist(ChangeSet cs) {
        File file = pendingFile(cs);
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.writeString(file.toPath(), cs.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log().warning("[QuestEditor] Impossible d'enregistrer les modifications en attente : " + e.getMessage());
        }
    }

    private static void deletePending(ChangeSet cs) {
        File file = pendingFile(cs);
        if (file.delete()) {
            String[] left = file.getParentFile().list();
            if (left != null && left.length == 0) file.getParentFile().delete();
        }
    }

    /** Modifications que le joueur doit encore valider (fichiers laissés pendant son absence). */
    static void promptPending(Player player) {
        File[] files = new File(EditorSettings.pendingFolder(), player.getUniqueId().toString()).listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;
        for (File file : files) {
            ChangeSet cs;
            try {
                cs = ChangeSet.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                log().warning("[QuestEditor] Fichier en attente illisible : " + file.getPath());
                continue;
            }
            if (WAITING.containsKey(cs.id)) continue;
            WAITING.put(cs.id, cs);
            prompt(cs, player, "Des modifications de l'éditeur attendent votre réponse : ");
        }
    }

    /** Le joueur se déconnecte sans avoir répondu : les modifications partent dans un fichier, il les retrouvera. */
    static void playerQuit(UUID uuid) {
        for (ChangeSet cs : List.copyOf(WAITING.values())) {
            if (!cs.player.equals(uuid) || cs.answered.get()) continue;
            persist(cs);
            WAITING.remove(cs.id);
        }
    }

    // ── Administration (console) ────────────────────────────────────────────

    /** Modifications en attente d'une réponse : celles des joueurs connectés et celles des fichiers. */
    public static List<ChangeSet> pending() {
        Map<String, ChangeSet> all = new LinkedHashMap<>();
        File[] players = EditorSettings.pendingFolder().listFiles(File::isDirectory);
        if (players != null) {
            for (File dir : players) {
                File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
                if (files == null) continue;
                for (File f : files) {
                    try {
                        ChangeSet cs = ChangeSet.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8));
                        all.put(cs.id, cs);
                    } catch (IOException | RuntimeException ignored) {
                        // fichier illisible : ignoré
                    }
                }
            }
        }
        all.putAll(WAITING);
        return new ArrayList<>(all.values());
    }

    /** Valide ({@code apply}) ou annule des modifications en attente à la place du joueur. @return {@code false} si l'identifiant est inconnu */
    public static boolean answer(String id, boolean apply, boolean force, CommandSender by) {
        ChangeSet found = null;
        for (ChangeSet c : pending()) {
            if (c.id.startsWith(id)) { found = c; break; }
        }
        if (found == null) return false;
        ChangeSet target = WAITING.getOrDefault(found.id, found);
        if (apply) validate(target, by, force);
        else cancel(target, by);
        return true;
    }

    static Collection<ChangeSet> waiting() {
        return WAITING.values();
    }

    // ── Source des définitions ──────────────────────────────────────────────

    private static final class RegistrySource implements EditorData.Source {
        @Override public Collection<Quest> quests() { return QuestRegistry.quests(); }
        @Override public Collection<QuestNpc> npcs() { return QuestRegistry.npcs(); }
        @Override public Quest quest(String id) { return QuestRegistry.quest(id); }
        @Override public QuestNpc npc(String id) { return QuestRegistry.npc(id); }
        @Override public Collection<CustomItemDef> customItems() {
            CustomItemManager manager = CustomItemManager.get();
            if (manager == null) return List.of();
            List<CustomItemDef> out = new ArrayList<>();
            for (String id : manager.registry().ids()) {
                CustomItemDef def = manager.registry().get(id);
                if (def != null) out.add(def);
            }
            return out;
        }
    }
}
