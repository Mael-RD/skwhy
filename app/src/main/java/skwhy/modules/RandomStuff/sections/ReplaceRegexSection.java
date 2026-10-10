package skwhy.modules.RandomStuff.sections;

import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Since;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.classes.Changer.ChangerUtils;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.LoopSection;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Name("Replace with Regex (Section)")
@Description({
    "Runs the section once for each occurrence of the regex(es) in each of the given texts.",
    "Inside the section, 'matched text' contains the text matched by the regex and 'replaced text' "
        + "(defaults to the matched text) can be changed: at the end of each iteration, the matched zone is replaced by it.",
    "'matched group %integer%' and 'matched group named %string%' give access to the regex capture groups.",
    "When several regexes are given, they are applied one after the other on the result of the previous one.",
    "Behaves like a loop: 'continue' goes to the next occurrence and 'exit loop' stops the section "
        + "(the replacements already made are kept)."
})
@Examples({
    "set {_text} to \"I have 3 apples and 12 pears\"",
    "replace regex \"\\d+\" in {_text} with code:",
    "\tset replaced text to \"%matched text parsed as integer * 2%\"",
    "# {_text} = \"I have 6 apples and 24 pears\"",
    "",
    "replace regex \"<(\\w+)>\" in {_messages::*} with code:",
    "\tif matched group 1 is \"player\":",
    "\t\tset replaced text to name of player",
    "\telse:",
    "\t\tdelete replaced text"
})
@Since("1.4.0")
public class ReplaceRegexSection extends LoopSection {

    /** Etat d'une exécution de la section, propre à chaque event (permet les waits et l'imbrication). */
    public static final class ReplaceState {
        private final Pattern[] patterns;
        private final String[] originals;
        private final String[] texts;

        private int regexIndex = 0;
        private int textIndex = 0;
        private @Nullable Matcher matcher;
        private @Nullable String input;
        private StringBuilder output = new StringBuilder();
        private int lastEnd = 0;

        private @Nullable MatchResult match;
        private @Nullable String replaced;

        private ReplaceState(Pattern[] patterns, String[] originals) {
            this.patterns = patterns;
            this.originals = originals;
            this.texts = originals.clone();
        }

        /** Texte déjà produit pour le texte courant : partie avant le match, remplacements précédents compris. */
        public String getOutputSoFar() {
            return output.toString();
        }

        public @Nullable String getMatched() {
            return match == null ? null : match.group();
        }

        public @Nullable MatchResult getMatch() {
            return match;
        }

        public @Nullable String getReplaced() {
            return replaced;
        }

        public void setReplaced(@Nullable String replaced) {
            this.replaced = replaced;
        }

        /** Remplace la zone matchée courante par le texte de remplacement. */
        private void commit() {
            if (match == null) return;
            if (replaced != null) output.append(replaced);
            match = null;
            replaced = null;
        }

        /** Passe à l'occurrence suivante ; retourne false quand tous les textes ont été traités. */
        private boolean advance() {
            while (true) {
                if (matcher == null) {
                    if (regexIndex >= patterns.length) return false;
                    if (textIndex >= texts.length) {
                        // Regex suivante, appliquée sur le résultat de la précédente
                        regexIndex++;
                        textIndex = 0;
                        continue;
                    }
                    input = texts[textIndex];
                    matcher = patterns[regexIndex].matcher(input);
                    output = new StringBuilder();
                    lastEnd = 0;
                }

                // Java gère lui-même l'avancement après un match vide
                if (matcher.find()) {
                    output.append(input, lastEnd, matcher.start());
                    lastEnd = matcher.end();
                    match = matcher.toMatchResult();
                    replaced = match.group();
                    return true;
                }

                finishCurrentText();
            }
        }

        /** Ajoute la fin du texte courant non matchée et enregistre le résultat. */
        private void finishCurrentText() {
            if (matcher == null) return;
            output.append(input, lastEnd, input.length());
            texts[textIndex] = output.toString();
            matcher = null;
            input = null;
            textIndex++;
        }
    }

    private Expression<String> regexExpr;
    private Expression<String> targetExpr;
    private @Nullable TriggerItem actualNext;

    private final Map<Event, ReplaceState> states = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed,
                        SkriptParser.ParseResult parseResult,
                        @Nullable SectionNode sectionNode,
                        List<TriggerItem> triggerItems) {
        regexExpr = (Expression<String>) exprs[0];
        targetExpr = (Expression<String>) exprs[1];

        if (sectionNode == null) {
            Skript.error("'replace regex ... with code' nécessite une section (ajoutez ':' en fin de ligne).");
            return false;
        }

        if (!ChangerUtils.acceptsChange(targetExpr, ChangeMode.SET, String.class)) {
            Skript.error(targetExpr.toString(null, false) + " ne peut pas être modifié, le texte ne peut donc pas être remplacé.");
            return false;
        }

        // Validation des regex dès le chargement quand elles sont connues
        if (regexExpr instanceof Literal<String> literal) {
            for (String regex : literal.getArray()) {
                try {
                    Pattern.compile(regex);
                } catch (PatternSyntaxException e) {
                    Skript.error("Regex invalide : " + e.getDescription() + " (" + regex + ")");
                    return false;
                }
            }
        }

        Kleenean hadDelayBefore = getParser().getHasDelayBefore();
        loadOptionalCode(sectionNode);
        // Le dernier élément du corps revient sur la section pour passer à l'occurrence suivante
        super.setNext(this);

        // Si le corps contient un wait, il est possible qu'il n'ait pas été exécuté (aucun match)
        if (!hadDelayBefore.isTrue() && getParser().getHasDelayBefore().isTrue()) {
            getParser().setHasDelayBefore(Kleenean.UNKNOWN);
        }

        return true;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        ReplaceState state = states.get(event);

        if (state == null) {
            state = start(event);
            if (state == null) {
                debug(event, false);
                return actualNext;
            }
            states.put(event, state);
        } else {
            state.commit();
        }

        if (state.advance()) {
            currentLoopCounter.put(event, currentLoopCounter.getOrDefault(event, 0L) + 1);
            return walk(event, true);
        }

        states.remove(event);
        apply(event, state);
        exit(event);
        debug(event, false);
        return actualNext;
    }

    private @Nullable ReplaceState start(Event event) {
        String[] regexes = regexExpr.getArray(event);
        String[] originals = targetExpr.getArray(event);
        if (regexes.length == 0 || originals.length == 0) return null;

        Pattern[] patterns = new Pattern[regexes.length];
        for (int i = 0; i < regexes.length; i++) {
            try {
                patterns[i] = Pattern.compile(regexes[i]);
            } catch (PatternSyntaxException e) {
                Skript.warning("Regex invalide : " + e.getMessage());
                return null;
            }
        }
        return new ReplaceState(patterns, originals);
    }

    /** Ecrit les textes modifiés dans l'expression cible en conservant les index des listes. */
    private void apply(Event event, ReplaceState state) {
        Deque<String[]> changes = new ArrayDeque<>();
        boolean changed = false;
        for (int i = 0; i < state.originals.length; i++) {
            changes.add(new String[]{ state.originals[i], state.texts[i] });
            if (!state.originals[i].equals(state.texts[i])) changed = true;
        }
        if (!changed) return;

        targetExpr.changeInPlace(event, value -> {
            String[] change = changes.peek();
            if (change != null && change[0].equals(value)) {
                changes.poll();
                return change[1];
            }
            return value;
        });
    }

    /** Appelé par Skript lors d'un 'exit loop' / 'stop' : les remplacements déjà faits sont conservés. */
    @Override
    public void exit(Event event) {
        ReplaceState state = states.remove(event);
        if (state != null) {
            state.commit();
            state.finishCurrentText();
            apply(event, state);
        }
        super.exit(event);
    }

    public @Nullable ReplaceState getState(Event event) {
        return states.get(event);
    }

    @Override
    public ReplaceRegexSection setNext(@Nullable TriggerItem next) {
        actualNext = next;
        return this;
    }

    @Override
    public @Nullable TriggerItem getActualNext() {
        return actualNext;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "replace regex " + regexExpr.toString(event, debug)
            + " in " + targetExpr.toString(event, debug) + " with code";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(ReplaceRegexSection.class)
                .addPattern("replace [all] regex %strings% in %strings% with code")
                .build()
        );
    }
}
