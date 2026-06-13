package skwhy.modules.NavigationElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import skwhy.pathfinder.Navigation;

public class FakePathFindingClass {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(Navigation.class, "navigation")
            .name("Fake Pathfinding")
            .description("A virtual pathfinding entity controlled via packets (for fake entities) or the Bukkit API (for real entities). " +
                "Computes an A* path toward a destination each tick, supporting multiple movement types: " +
                "WALK, WALK_WATER, SWIM, FLY, FLY_GROUND, CLIMB, and NONE. " +
                "When created from a real entity, movement is applied directly via teleport without requiring PacketEvents. " +
                "When created from a numeric ID, PacketEvents is required to send movement packets to viewers.")
            .usage("Created via 'a new fake pathfinding with id %number% ...' or 'a new fake pathfinding with entity %entity% ...'.")
            .user("fakes? ?pathfindings?")
            .examples(
                "# Create from a numeric ID (requires PacketEvents)",
                "set {_fake} to a new fake pathfinding with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\" speed 0.2 with players all players",
                "",
                "# Create from a real entity (works without PacketEvents)",
                "set {_fake} to a new fake pathfinding with entity target entity hitbox vector(0.6, 1.8, 0.6) location location of player type \"FLY\"",
                "",
                "set destination of {_fake} to location(100, 64, 200, world \"world\")",
                "set speed of {_fake} to 0.3",
                "set pause ticks of {_fake} to 20",
                "set pathfinding type of {_fake} to \"SWIM\"",
                "set hitbox of {_fake} to vector(0.4, 0.9, 0.4)",
                "add player to players of {_fake}"
            )
            .since("1.2.0")
            .parser(new Parser<Navigation>() {
                @Override
                public String toString(Navigation data, int flags) {
                    return data.toString();
                }

                @Override
                public String toVariableNameString(Navigation data) {
                    return data.toString();
                }
            })
            .parser(new Parser<Navigation>() {
                @Override
                public Navigation parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(Navigation o, int flags) {
                    return o.toString();
                }

                @Override
                public String toVariableNameString(Navigation o) {
                    return o.toString();
                }
            })
            .serializer(new Serializer<Navigation>() {
                @Override
                public Fields serialize(Navigation data) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void deserialize(Navigation o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Navigation deserialize(Fields fields) {
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
