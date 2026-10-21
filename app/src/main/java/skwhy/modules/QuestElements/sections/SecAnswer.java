package skwhy.modules.QuestElements.sections;

import java.util.List;

import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("[Quest] Answer")
@Description("A clickable answer inside 'make <quest npc> say ... with answers'. Its code runs when the player clicks it. " +
    "'hover' sets a tooltip and 'when' a condition: the answer is only offered when it is true.")
@Examples({
    "make quest npc \"maire\" say \"Prêt ?\" to player with answers:",
    "\tanswer \"Oui\" hover \"On y va\":",
    "\t\tadvance quest \"bienvenue\" for player",
    "\tanswer \"Non\" when player has started quest \"bienvenue\":",
    "\t\tsend \"D'accord.\" to player"
})
@Since("1.6.0")
public class SecAnswer extends Section {

    private Expression<String> textExpr;
    private @Nullable Expression<String> hoverExpr;
    private @Nullable Condition whenCondition;
    private Trigger trigger;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr,
                        @Nullable SectionNode sectionNode, List<TriggerItem> triggerItems) {
        if (sectionNode == null) {
            Skript.error("'answer' needs a section (add ':' at the end of the line).");
            return false;
        }
        if (!SecSayWithAnswers.parsingAnswers()) {
            Skript.error("'answer' can only be used inside 'make <quest npc> say ... with answers'.");
            return false;
        }
        textExpr = (Expression<String>) exprs[0];
        hoverExpr = (Expression<String>) exprs[1];
        if (!pr.regexes.isEmpty()) {
            String condition = pr.regexes.get(0).group().trim();
            whenCondition = Condition.parse(condition, "Can't understand this condition: '" + condition + "'");
            if (whenCondition == null) return false;
        }
        trigger = loadCode(sectionNode, "quest answer", getParser().getCurrentEvents());
        return true;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        List<SecSayWithAnswers.Pending> collector = SecSayWithAnswers.collector(event);
        if (collector != null) {
            String text = textExpr.getSingle(event);
            if (text != null && (whenCondition == null || whenCondition.check(event))) {
                collector.add(new SecSayWithAnswers.Pending(text, hoverExpr == null ? null : hoverExpr.getSingle(event), trigger));
            }
        }
        // Le code de la réponse ne s'exécute qu'au clic
        return walk(event, false);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "answer " + textExpr.toString(event, debug) + (hoverExpr == null ? "" : " hover " + hoverExpr.toString(event, debug))
            + (whenCondition == null ? "" : " when " + whenCondition.toString(event, debug));
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(SecAnswer.class)
                .addPattern("answer %string% [hover %-string%] [when <.+>]")
                .build()
        );
    }
}
