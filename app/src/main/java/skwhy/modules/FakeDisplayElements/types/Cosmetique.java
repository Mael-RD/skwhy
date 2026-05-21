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
            .description("Un cosmetique pouvant être contrôlé avec une position et une monture communes.")
            .usage("créé via 'new cosmetique'")
            .user("cosmetiques?")
            .examples(
                "set {_group} to new display group",
                "add {_display} to {_group}",
                "update {_group} with x = 100, y = 64"
            )
            .since("1.0.0")
            
            .parser(new Parser<>() {

                @Override
                public String toString(CosmetiqueData data, int flags) {
                    return this.toString();
                }

                @Override
                public String toVariableNameString(CosmetiqueData data) {
                    return "cosmetique:" + data.serialize();
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
                    return "cosmetique:" + o.toString();
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
