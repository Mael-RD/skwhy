package skwhy.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * Ajoute à une config YAML (texte brut) les clés présentes dans la config par défaut mais absentes,
 * sans toucher au reste du fichier (valeurs, commentaires et ordre de l'utilisateur conservés).
 * <p>
 * Une clé manquante est insérée avec ses commentaires précédents et, si c'est un bloc, tout son sous-arbre
 * (une seule fois : ses enfants ne sont pas traités à part). Position : juste après le frère précédent déjà
 * présent (sous-bloc compris), sinon juste avant le frère suivant présent, sinon en premier enfant du parent
 * (ou fin de fichier pour une clé racine). L'indentation s'adapte à celle du fichier existant.
 */
public final class ConfigMerger {

    private ConfigMerger() {}

    public record Result(List<String> lines, List<String> addedKeys) {}

    /** Ligne portant une clé YAML. */
    private static final class Node {
        String name;
        String path;
        int indent;
        int line;     // index de la ligne de la clé
        int trivia;   // premier index des commentaires/lignes vides qui précèdent la clé
        int end;      // index après la dernière ligne de contenu du sous-arbre
        Node parent;
        final List<Node> children = new ArrayList<>();
    }

    public static Result merge(List<String> current, List<String> defaults) {
        List<String> result = new ArrayList<>(current);
        List<String> added = new ArrayList<>();
        Set<Node> skipped = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Node d : parse(defaults).all) {
            if (d.parent != null && skipped.contains(d.parent)) {
                skipped.add(d);
                continue;
            }
            Parsed cur = parse(result);
            if (cur.byPath.containsKey(d.path)) continue;

            skipped.add(d);
            added.add(d.path);
            insert(result, cur, defaults, d);
        }
        return new Result(result, added);
    }

    private static void insert(List<String> result, Parsed cur, List<String> defaults, Node d) {
        List<Node> siblings = d.parent == null ? parse(defaults).roots : siblingsOf(defaults, d);
        int idx = indexByPath(siblings, d.path);

        Node pred = null;
        for (int i = idx - 1; i >= 0 && pred == null; i--) pred = cur.byPath.get(siblings.get(i).path);
        Node succ = null;
        for (int i = idx + 1; i < siblings.size() && succ == null; i++) succ = cur.byPath.get(siblings.get(i).path);
        Node curParent = d.parent == null ? null : cur.byPath.get(d.parent.path);

        int at;
        if (pred != null) at = pred.end;
        else if (succ != null) at = succ.trivia;
        else if (curParent != null) at = curParent.line + 1;
        else at = result.size();

        int target;
        if (pred != null) target = pred.indent;
        else if (succ != null) target = succ.indent;
        else if (curParent != null) target = curParent.indent + (d.indent - d.parent.indent);
        else target = 0;
        int delta = target - d.indent;

        List<String> block = new ArrayList<>();
        boolean droppedComment = false;
        for (String line : defaults.subList(d.trivia, d.end)) {
            // commentaire déjà présent dans le fichier (clé supprimée à la main mais commentaire gardé) : pas de doublon
            if (line.strip().startsWith("#") && result.stream().anyMatch(r -> r.strip().equals(line.strip()))) {
                droppedComment = true;
                continue;
            }
            block.add(reindent(line, delta));
        }
        if (droppedComment) block.removeIf(String::isBlank);
        // pas de ligne vide en double à côté d'une ligne vide déjà présente
        while (!block.isEmpty() && block.get(0).isBlank()
                && ((at > 0 && result.get(at - 1).isBlank()) || (at < result.size() && result.get(at).isBlank()))) {
            block.remove(0);
        }
        result.addAll(at, block);
    }

    private static List<Node> siblingsOf(List<String> defaults, Node d) {
        for (Node n : parse(defaults).all) {
            if (n.path.equals(d.parent.path)) return n.children;
        }
        return List.of(d);
    }

    private static int indexByPath(List<Node> nodes, String path) {
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).path.equals(path)) return i;
        return -1;
    }

    private static String reindent(String line, int delta) {
        if (delta == 0 || line.isBlank()) return line;
        if (delta > 0) return " ".repeat(delta) + line;
        int strip = 0;
        while (strip < -delta && strip < line.length() && line.charAt(strip) == ' ') strip++;
        return line.substring(strip);
    }

    private static final class Parsed {
        final List<Node> all = new ArrayList<>();
        final List<Node> roots = new ArrayList<>();
        final Map<String, Node> byPath = new HashMap<>();
    }

    private static boolean isTrivia(String line) {
        String t = line.strip();
        return t.isEmpty() || t.startsWith("#");
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    /** Nom de la clé si la ligne est de la forme {@code clé:} / {@code clé: valeur}, sinon null (élément de liste, texte...). */
    private static String keyName(String line) {
        String t = line.strip();
        if (t.isEmpty() || t.startsWith("#") || t.equals("-") || t.startsWith("- ")) return null;
        String key;
        if (t.startsWith("\"") || t.startsWith("'")) {
            char q = t.charAt(0);
            int close = t.indexOf(q, 1);
            if (close < 0) return null;
            key = t.substring(1, close);
            String rest = t.substring(close + 1).stripLeading();
            if (!rest.startsWith(":")) return null;
        } else {
            int colon = -1;
            for (int i = 0; i < t.length(); i++) {
                if (t.charAt(i) == ':' && (i + 1 == t.length() || t.charAt(i + 1) == ' ')) { colon = i; break; }
            }
            if (colon <= 0) return null;
            key = t.substring(0, colon).strip();
        }
        return key;
    }

    private static Parsed parse(List<String> lines) {
        Parsed p = new Parsed();
        List<Node> stack = new ArrayList<>();
        int lastContent = -1; // dernière ligne de contenu rencontrée (clé ou autre)

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isTrivia(line)) continue;

            String name = keyName(line);
            int indent = indentOf(line);
            if (name != null) {
                while (!stack.isEmpty() && stack.get(stack.size() - 1).indent >= indent) stack.remove(stack.size() - 1);
                Node n = new Node();
                n.name = name;
                n.indent = indent;
                n.line = i;
                n.trivia = lastContent + 1;
                n.parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                n.path = n.parent == null ? name : n.parent.path + "." + name;
                if (n.parent == null) p.roots.add(n); else n.parent.children.add(n);
                p.all.add(n);
                p.byPath.putIfAbsent(n.path, n);
                stack.add(n);
            }
            lastContent = i;
        }

        for (Node n : p.all) n.end = subtreeEnd(lines, n);
        return p;
    }

    /** Index après la dernière ligne de contenu appartenant à {@code n} (commentaires finaux exclus). */
    private static int subtreeEnd(List<String> lines, Node n) {
        int last = n.line;
        for (int j = n.line + 1; j < lines.size(); j++) {
            String l = lines.get(j);
            if (isTrivia(l)) continue;
            int ind = indentOf(l);
            boolean listAtSameLevel = ind == n.indent && (l.strip().equals("-") || l.strip().startsWith("- "));
            if (ind > n.indent || listAtSameLevel) last = j; else break;
        }
        return last + 1;
    }
}
