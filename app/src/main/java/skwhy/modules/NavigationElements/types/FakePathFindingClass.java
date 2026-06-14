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
            .name("[Navigation] Type")
            .description("A virtual pathfinding entity controlled via packets (for fake entities) or the Bukkit API (for real entities). " +
                "Computes an A* path toward a destination each tick, supporting multiple movement types: " +
                "WALK, WALK_WATER, SWIM, FLY, FLY_GROUND, CLIMB, and NONE. " +
                "When created from a real entity, movement is applied directly via teleport without requiring PacketEvents. " +
                "When created from a numeric ID, PacketEvents is required to send movement packets to viewers.")
            .usage("Created via 'a new fake navigation with id %number% ...' or 'a new navigation with entity %entity% ...'.")
            .user("fakes? ?pathfindings?")
            .examples(
                "# Create from a numeric ID (requires PacketEvents)",
                "set {_navigation} to a new fake navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\" speed 0.2 with players all players",
                "",
                "# Create from a real entity (works without PacketEvents)",
                "set {_navigation} to a new navigation with entity target entity type \"FLY\"",
                "",
                "set destination of navigation {_navigation} to location(100, 64, 200, world \"world\")",
                "set speed of navigation {_navigation} to 0.3",
                "set pause ticks of navigation {_navigation} to 20",
                "set pathfinding type of navigation {_navigation} to \"SWIM\"",
                "set hitbox of navigation {_navigation} to vector(0.4, 0.9, 0.4)",
                "add player to viewers of navigation {_navigation}"
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
