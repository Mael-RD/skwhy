package skwhy.modules.FakeDisplayElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import skwhy.data.CosmetiqueData;

import java.io.StreamCorruptedException;

public class Cosmetique {
    
    // Appelé une seule fois depuis init() du module
    public static void register() {
        Classes.registerClass(new ClassInfo<>(CosmetiqueData.class, "cosmetique")
            .name("Cosmetique")
            .description("A cosmetic object that groups hat, back, and tail parts for a specific entity or player. " +
                "All parts share a common mount and scale. Cannot be parsed from text.")
            .usage("Created via 'a new cosmetique for %entity%' or 'a new cosmetique for %player% with hats %boolean% back %boolean% and tail %boolean%'.")
            .user("cosmetiques?")
            .examples(
                "set {_cosmetique} to a new cosmetique for player",
                "set {_cosmetique} to a new cosmetique for player with hats true back true and tail true",
                "set cosmetique scale of {_cosmetique} to 1.5",
                "add player to viewers of {_cosmetique}",
                "destroy cosmetique {_cosmetique}"
            )
            .since("1.0.0")
            
            .parser(new Parser<>() {

                @Override
                public String toString(CosmetiqueData data, int flags) {
                    return data.toString();
                }

                @Override
                public String toVariableNameString(CosmetiqueData data) {
                    return data.toString();
                }
            })
            .parser(new Parser<CosmetiqueData>() {
                @Override
                public CosmetiqueData parse(String s, ParseContext context) {
                    return null; 
                }

                @Override
                public boolean canParse(ParseContext context) {
                    // Tu peux mettre false si ce type ne doit jamais être parsé depuis du texte
                    return false; 
                }

                @Override
                public String toString(CosmetiqueData o, int flags) {
                    return o.toString();
                }

                @Override
                public String toVariableNameString(CosmetiqueData o) {
                    return o.toString();
                }
            })
            
            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(CosmetiqueData data) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void deserialize(CosmetiqueData o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CosmetiqueData deserialize(Fields fields) throws StreamCorruptedException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean mustSyncDeserialization() {
                    return false;
                }

                @Override
                protected boolean canBeInstantiated() {
                    return false;
                }
            })
        );
    }
}
