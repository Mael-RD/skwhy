package skwhy.modules.QuestElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import skwhy.quest.model.QuestNpc;

public class QuestNpcType {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(QuestNpc.class, "questnpc")
            .name("[Quest] Quest NPC Type")
            .description("The definition of a quest NPC (a file in SkWhy's quest-npcs folder, or a legacy .nbt file). One NPC can exist in several places of the world (its positions 1, 2, 3...); a player sees one of them.")
            .usage("Get one with 'quest npc \"id\"' or 'quest npc of <entity>'.")
            .user("quest ?npcs?")
            .examples("set {_npc} to quest npc \"maire\"", "move quest npc {_npc} to position 2 for player")
            .since("1.6.0")
            .parser(new Parser<QuestNpc>() {
                @Override
                public QuestNpc parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(QuestNpc o, int flags) {
                    return o.toString();
                }

                @Override
                public String toVariableNameString(QuestNpc o) {
                    return "questnpc:" + o.id();
                }
            })
        );
    }
}
