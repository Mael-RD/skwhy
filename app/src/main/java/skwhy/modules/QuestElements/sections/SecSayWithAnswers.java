package skwhy.modules.QuestElements.sections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import ch.njol.skript.Skript;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.npc.DialogueService;

@Name("[Quest] Say With Answers")
@Description("Makes a quest NPC say a text to a player and offer clickable answers; each 'answer' runs its code when the player clicks it. " +
    "The section may only contain 'answer' lines. Options: 'within <n> blocks' (the answer is refused when the player moved farther than that from the NPC entity " +
    "— give the entity with 'quest npc of' bound entities, it is looked up from the NPC), 'for <timespan>' (the answers expire), " +
    "and 'inline' to put the answers on one line (one per line by default). " +
    "An answer accepts 'hover <text>' (a tooltip) and 'when <condition>' (the answer is only offered when the condition is true, checked when the message is sent). " +
    "The code of an answer runs later with the local variables the script had when the message was sent; only the last message of a player can be answered. " +
    "The text accepts the quest tags %nl%, %player%, %genre::masculine::feminine% and %variable::quest::name% (written with doubled percents in a Skript string).")
@Examples({
    "make quest npc \"maire\" say \"Veux-tu un métier ?\" to player with answers within 15 blocks for 2 minutes:",
    "\tanswer \"Oui\" hover \"Je commence\":",
    "\t\tunlock quest \"métier\" for player",
    "\tanswer \"Pas maintenant\" when player has finished quest \"bienvenue\":",
    "\t\tsend \"À bientôt.\" to player"
})
@Since("1.6.0")
public class SecSayWithAnswers extends Section {

    /** Réponse collectée pendant l'exécution à blanc du corps de la section. */
    record Pending(String text, @Nullable String hover, Trigger trigger) {}

    private static final Map<Event, List<Pending>> COLLECTORS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Vrai pendant le chargement du corps : {@code loadCode} vide la pile des sections, on ne peut donc pas la consulter. */
    private static final ThreadLocal<Integer> PARSING = ThreadLocal.withInitial(() -> 0);

    private Expression<QuestNpc> npcExpr;
    private Expression<String> textExpr;
    private Expression<Player> playerExpr;
    private @Nullable Expression<Number> distanceExpr;
    private @Nullable Expression<Timespan> timeExpr;
    private boolean inline;
    private Trigger body;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr,
                        @Nullable SectionNode sectionNode, List<TriggerItem> triggerItems) {
        if (sectionNode == null) {
            Skript.error("'make ... say ... with answers' needs a section (add ':' at the end of the line).");
            return false;
        }
        for (Node node : sectionNode) {
            String key = node.getKey() == null ? "" : node.getKey().trim();
            if (!(node instanceof SectionNode) || !key.startsWith("answer ")) {
                if (!key.isEmpty() && !key.startsWith("#")) {
                    Skript.error("Only 'answer <text>:' sections are allowed inside 'with answers', found: " + key);
                    return false;
                }
            }
        }
        npcExpr = (Expression<QuestNpc>) exprs[0];
        textExpr = (Expression<String>) exprs[1];
        playerExpr = (Expression<Player>) exprs[2];
        distanceExpr = (Expression<Number>) exprs[3];
        timeExpr = (Expression<Timespan>) exprs[4];
        inline = pr.mark == 1;
        PARSING.set(PARSING.get() + 1);
        try {
            body = loadCode(sectionNode, "quest answers", getParser().getCurrentEvents());
        } finally {
            PARSING.set(PARSING.get() - 1);
        }
        return true;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        String text = textExpr.getSingle(event);
        Player player = playerExpr.getSingle(event);
        if (npc == null || text == null || player == null) return walk(event, false);

        Object locals = Variables.copyLocalVariables(event);
        List<Pending> pending = new ArrayList<>();
        COLLECTORS.put(event, pending);
        try {
            // Exécution à blanc : chaque « answer » s'enregistre sans exécuter son code ; le corps retire les variables locales de l'événement
            body.execute(event);
        } finally {
            COLLECTORS.remove(event);
            Variables.setLocalVariables(event, locals);
        }

        List<DialogueService.Answer> answers = new ArrayList<>();
        for (Pending p : pending) {
            answers.add(new DialogueService.Answer(p.text(), p.hover(), clicker -> {
                Variables.setLocalVariables(event, locals);
                p.trigger().execute(event);
            }));
        }
        DialogueService.Options options = new DialogueService.Options();
        options.inline = inline;
        Number distance = distanceExpr == null ? null : distanceExpr.getSingle(event);
        Timespan time = timeExpr == null ? null : timeExpr.getSingle(event);
        if (distance != null) options.distance = distance.doubleValue();
        if (time != null) options.seconds = time.getAs(Timespan.TimePeriod.MILLISECOND) / 1000.0;
        options.entity = DialogueEntities.of(npc, player);
        DialogueService.say(player, npc, text, answers, options);
        return walk(event, false);
    }

    /** {@code true} quand le corps d'une section « with answers » est en cours de chargement. */
    static boolean parsingAnswers() {
        return PARSING.get() > 0;
    }

    /** Appelé par {@link SecAnswer} pendant l'exécution à blanc. */
    static @Nullable List<Pending> collector(Event event) {
        return COLLECTORS.get(event);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "make " + npcExpr.toString(event, debug) + " say " + textExpr.toString(event, debug) + " to " + playerExpr.toString(event, debug) + " with answers";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(SecSayWithAnswers.class)
                .addPattern("make %questnpc% say %string% to %player% with answers [within %-number% blocks] [for %-timespan%] [(1:inline|0:multiline)]")
                .build()
        );
    }
}
