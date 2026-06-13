package skwhy.modules.VoiceElements.expressions;

import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.RequiredPlugins;
import ch.njol.skript.doc.Since;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.voice.VoiceListener;
import skwhy.voice.StreamingSpeechSession;
import skwhy.voice.TextNormalizer;

import skwhy.modules.VoiceModule;


import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

/**
 * Expression : voice rules of %player%
 *
 * Retourne la liste des phrases/mots surveillés (sans [unk]).
 * Supporte : get, set, add, remove, delete/clear
 *
 * ── Syntaxe Skript ────────────────────────────────────────────────────────────
 *
 *  GET
 *    set {_rules::*} to voice rules of player
 *    loop voice rules of player:
 *        send loop-value to player
 *
 *  SET  (remplace toute la liste + recharge la session)
 *    set voice rules of player to "attaque", "soin", "retraite"
 *
 *  ADD  (ajoute un ou plusieurs éléments + recharge)
 *    add "bouclier" to voice rules of player
 *    add "sprint", "saut" to voice rules of player
 *
 *  REMOVE  (retire un ou plusieurs éléments + recharge)
 *    remove "soin" from voice rules of player
 *
 *  DELETE / CLEAR  (vide la liste + arrête la reconnaissance)
 *    clear voice rules of player
 *    delete voice rules of player
 *
 * ── Note sur add/remove ───────────────────────────────────────────────────────
 *  Vosk n'accepte pas de modification incrémentale de grammaire.
 *  Add et remove rechargent donc la session complète avec la nouvelle liste.
 *  C'est documenté dans les commentaires ci-dessous et acceptable selon les specs.
 */
@Name("Voice Rules")
@Description("Gets, sets, adds or removes voice recognition rules (phrases) for a specific player.")
@Examples({
    "set voice rules of player to \"hello\", \"help\" and \"stop\"",
    "add \"jump\" to voice rules of player",
    "clear voice rules of player"
})
@Since("1.1.0")
@RequiredPlugins({"SimpleVoiceChat"})
public class VoiceRules extends SimpleExpression<String> {

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(VoiceRules.class, String.class)
                .addPattern("voice rules of %player%")
                .addPattern("%player%'[s] voice rules")
                .build()
        );
    }

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, ParseResult parseResult) {
        playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Override
    protected @Nullable String[] get(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return null;

        StreamingSpeechSession session =
                VoiceModule.getVoiceListener().getSession(player);
        if (session == null) return null;

        // getRawPhrases() retourne les phrases normalisées sans [unk]
        List<String> phrases = session.getRawPhrases();
        return phrases.toArray(new String[0]);
    }

    @Override
    public boolean isSingle() {
        return false; // liste
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    // ── CHANGERS ──────────────────────────────────────────────────────────────

    @Override
    public Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET    -> new Class[]{ String[].class };  // set ... to "a", "b", "c"
            case ADD    -> new Class[]{ String[].class };  // add "x" to ...
            case REMOVE -> new Class[]{ String[].class };  // remove "x" from ...
            case DELETE,
                 RESET,
                 REMOVE_ALL  -> new Class[0];                   // clear / delete (pas de delta)
            default     -> null;
        };
    }

    @Override
    public void change(Event event, Object[] delta, ChangeMode mode) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return;

        VoiceListener listener = VoiceModule.getVoiceListener();
        
        switch (mode) {

            case SET -> {
                // Remplacer toute la liste et recharger la session
                List<String> rules = toSimpleRules(delta);
                try {
                    listener.startListening(player, rules);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            case ADD -> {
                // Récupérer la liste actuelle, ajouter, recharger
                // (add/remove recharge la session complète — voir doc classe)
                StreamingSpeechSession session = listener.getSession(player);
                List<String> existing = (session != null)
                        ? new ArrayList<>(session.getRawPhrases())
                        : new ArrayList<>();

                for (Object o : delta) {
                    if (o instanceof String s && !s.isBlank()) {
                        String normalized = TextNormalizer.normalize(s);
                        if (!existing.contains(normalized)) existing.add(normalized);
                    }
                }

                List<String> rules = phraseListToRules(existing);
                try {
                    listener.startListening(player, rules);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            case REMOVE -> {
                StreamingSpeechSession session = listener.getSession(player);
                if (session == null) return;

                List<String> existing = new ArrayList<>(session.getRawPhrases());
                for (Object o : delta) {
                    if (o instanceof String s) {
                        existing.remove(TextNormalizer.normalize(s));
                    }
                }

                if (existing.isEmpty()) {
                    // Plus rien à écouter → arrêter la reconnaissance
                    listener.stopListening(player);
                } else {
                    try {
                        listener.startListening(player, phraseListToRules(existing));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            case DELETE, RESET, REMOVE_ALL -> {
                // clear / delete → arrête la reconnaissance complètement
                listener.stopListening(player);
            }
        }
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    /** Convertit un tableau Object[] de delta Skript en TriggerRules SIMPLE */
    private List<String> toSimpleRules(Object[] delta) {
        List<String> rules = new ArrayList<>();
        if (delta == null) return rules;
        for (Object o : delta) {
            if (o instanceof String s && !s.isBlank()) {
                rules.add(TextNormalizer.normalize(s));
            }
        }
        return rules;
    }

    /** Convertit une liste de phrases normalisées en TriggerRules SIMPLE */
    private List<String> phraseListToRules(List<String> phrases) {
        return new ArrayList<>(phrases);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "voice rules of " + playerExpr.toString(event, debug);
    }
}