package skwhy.modules.LongNavigationElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import skwhy.chunkpath.longnav.LongNavigation;

public class LongNavigationSkriptType {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(LongNavigation.class, "longnavigation")
            .name("[Long Navigation] Type")
            .description("A long-distance, cost-rule aware pathfinding session — the long-distance counterpart " +
                "to the 'navigation' type. Computes a coarse chunk-gate route between a start and an end " +
                "location (using registered chunk path records and the world's path cost rules), then walks " +
                "the real terrain toward that route waypoint by waypoint, recalculating short segments as " +
                "needed instead of precomputing the whole distant path at once. " +
                "Like a normal navigation, it can be created from a numeric ID (virtual entity, requires " +
                "PacketEvents to send movement packets to viewers) or a real entity (moved via teleport).")
            .usage("Created via 'a new long fake navigation with id %number% ...' or 'a new long navigation with entity %entity% ...'.")
            .user("long ?fakes? ?navigations?")
            .examples(
                "set {_longnav} to a new long navigation with entity target entity speed 0.25",
                "set start location of long navigation {_longnav} to location of player",
                "set end location of long navigation {_longnav} to location(500, 64, 500, world \"world\")",
                "set pause ticks of long navigation {_longnav} to 0",
                "send {_longnav} # full debug dump: status, start/end/current position, gate route, wait state..."
            )
            .since("1.5.0")
            .parser(new Parser<LongNavigation>() {
                @Override
                public LongNavigation parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(LongNavigation o, int flags) {
                    return o.toString();
                }

                @Override
                public String toVariableNameString(LongNavigation o) {
                    return null;
                }
            })
            .serializer(new Serializer<LongNavigation>() {
                @Override
                public Fields serialize(LongNavigation data) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void deserialize(LongNavigation o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public LongNavigation deserialize(Fields fields) {
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
