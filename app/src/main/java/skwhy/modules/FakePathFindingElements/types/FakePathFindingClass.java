package skwhy.modules.FakePathFindingElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import skwhy.data.FakePathFinding;

public class FakePathFindingClass {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(FakePathFinding.class, "fakepathfinding")
            .name("FakePathFinding")
            .description("Un fake pathfinding géré par packet pour des entités de pathfinding virtuelles.")
            .usage("créé via 'new fake pathfinding'")
            .user("fakepathfindings?")
            .examples(
                "set {_fp} to new fake pathfinding with id 123 hitbox vector(0.6, 1.8, 0.6) location player location type \"WALK\""
            )
            .since("1.0.0")
            .parser(new Parser<FakePathFinding>() {
                @Override
                public String toString(FakePathFinding data, int flags) {
                    return data.toString();
                }

                @Override
                public String toVariableNameString(FakePathFinding data) {
                    return data.toString();
                }
            })
            .parser(new Parser<FakePathFinding>() {
                @Override
                public FakePathFinding parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(FakePathFinding o, int flags) {
                    return o.toString();
                }

                @Override
                public String toVariableNameString(FakePathFinding o) {
                    return o.toString();
                }
            })
            .serializer(new Serializer<FakePathFinding>() {
                @Override
                public Fields serialize(FakePathFinding data) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void deserialize(FakePathFinding o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public FakePathFinding deserialize(Fields fields) {
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
