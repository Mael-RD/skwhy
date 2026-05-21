package skwhy.modules.FakeDisplayElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import skwhy.data.Tail.TailNode;

import org.jetbrains.annotations.Nullable;
import java.io.StreamCorruptedException;

public class TailPart {

    // Appelé une seule fois depuis init() du module principal
    public static void register() {
        Classes.registerClass(new ClassInfo<>(TailNode.class, "tailpart")
            .name("Tail Part")
            .description("Représente un segment (nœud) individuel d'une queue animée (Tail).")
            .usage("Obtenu via des expressions spécifiques de gestion des queues.")
            .user("tail ?parts?")
            .examples(
                "set {_root} to root part of {_tail}",
                "set {_parent} to parent of tailpart {_part}"
            )
            .since("1.0.0")

            .parser(new Parser<TailNode>() {
                @Override
                public @Nullable TailNode parse(String s, ParseContext context) {
                    return null; // Pas de parsing textuel / pas de syntaxe littérale
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(TailNode node, int flags) {
                    return "TailPart[Depth: " + node.depth + ", DisplayGroup: " + node.display.toString() + "]";
                }

                @Override
                public String toVariableNameString(TailNode node) {
                    // Identifiant unique temporaire pour les variables locales de script
                    return "tailpart:" + node.depth + ":" + node.hashCode();
                }
            })

            .serializer(new Serializer<TailNode>() {
                @Override
                public Fields serialize(TailNode node) {
                    Fields fields = new Fields();
                    // On extrait les informations structurelles et de textures actuelles du nœud
                    fields.putPrimitive("depth", node.depth);
                    fields.putObject("displayGroup", node.display);
                    return fields;
                }

                @Override
                public void deserialize(TailNode o, Fields f) {
                    throw new UnsupportedOperationException("La désérialisation directe de TailNode n'est pas supportée.");
                }

                @Override
                public @Nullable TailNode deserialize(Fields fields) throws StreamCorruptedException {
                    // Conformément à ta demande (pas de stockage persistant requis dans les fichiers/variables),
                    // le type n'a pas besoin de reconstruire un arbre physique complet à partir de rien.
                    return null; 
                }

                @Override
                public boolean mustSyncDeserialization() {
                    return false;
                }

                @Override
                protected boolean canBeInstantiated() {
                    return false; // Type non instanciable directement via Skript
                }
            })
        );
    }
}