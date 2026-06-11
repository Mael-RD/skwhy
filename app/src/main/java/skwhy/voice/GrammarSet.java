package skwhy.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.google.gson.Gson;

import java.util.*;

/**
 * Représente une grammaire Vosk restreinte.
 *
 * Avec cette grammaire, Vosk n'émet QUE ces tokens au niveau du décodeur
 * acoustique. Tout phonème hors-liste est transcrit "[unk]" ou ignoré.
 * Cela rend la reconnaissance ~10× plus rapide et élimine les faux positifs.
 *
 * "[unk]" doit TOUJOURS être inclus — sans lui Vosk peut planter sur l'audio
 * hors-vocabulaire ou ralentir en tentant de forcer un token de la liste.
 *
 * Construction :
 *   GrammarSet g = GrammarSet.fromTriggers(rules);   // phase trigger
 *   GrammarSet g = GrammarSet.fromPayloads(rules);   // phase payload
 *   String json  = g.getJson();                       // → passer à Recognizer(model, sr, json)
 */
public class GrammarSet {

    private static final String UNK = "[unk]";

    private final List<String> entries; // mots/phrases normalisés, sans doublons
    private final String       json;    // JSON mis en cache

    private GrammarSet(Set<String> words) {
        List<String> list = new ArrayList<>(words);
        list.add(UNK); // toujours présent
        this.entries = Collections.unmodifiableList(list);
        this.json    = buildJson(list);
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    /**
     * Grammaire contenant uniquement les triggers des règles actives.
     * Utilisée en phase TRIGGER_LISTEN.
     */
    public static GrammarSet fromTriggers(List<TriggerRule> rules) {
        Set<String> words = new LinkedHashSet<>();
        for (TriggerRule r : rules) {
            // Pour un trigger multi-mots ("ouvre la porte"), Vosk reconnaît la séquence
            // comme un seul token si on l'ajoute tel quel.
            words.add(r.trigger);
        }
        return new GrammarSet(words);
    }

    /**
     * Grammaire contenant uniquement les payloads des règles armées.
     * Utilisée en phase PAYLOAD_LISTEN.
     */
    public static GrammarSet fromPayloads(List<TriggerRule> rules) {
        Set<String> words = new LinkedHashSet<>();
        for (TriggerRule r : rules) {
            if (r.payload != null) words.add(r.payload);
        }
        return new GrammarSet(words);
    }

    /**
     * Grammaire custom à partir d'une liste de chaînes brutes.
     * Normalise chaque entrée avant de l'ajouter.
     */
    public static GrammarSet fromRaw(Collection<String> raw) {
        Set<String> words = new LinkedHashSet<>();
        for (String s : raw) {
            String n = TextNormalizer.normalize(s);
            if (!n.isEmpty()) words.add(n);
        }
        return new GrammarSet(words);
    }

    // ── Accesseurs ────────────────────────────────────────────────────────────

    /** JSON à passer au constructeur Recognizer de Vosk */
    public String getJson() { return json; }

    public List<String> getEntries() { return entries; }

    public boolean isEmpty() {
        // isEmpty = ne contient que "[unk]"
        return entries.size() <= 1;
    }

    // ── Construction du JSON ──────────────────────────────────────────────────

    private static String buildJson(List<String> entries) {
        JsonArray arr = new JsonArray();
        for (String e : entries) arr.add(new JsonPrimitive(e));
        return new Gson().toJson(arr);
        // Exemple : ["attaque","soin","bouclier","[unk]"]
    }

    @Override
    public String toString() { return "GrammarSet" + entries; }
}
