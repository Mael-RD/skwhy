package skwhy.modules.QuestElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import skwhy.quest.model.Quest;

public class QuestType {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(Quest.class, "quest")
            .name("[Quest] Quest Type")
            .description("A quest definition loaded from a quest file (YAML in SkWhy's quests folder, or a legacy .nbt file). It holds the steps, objectives, variables and script of the quest, not the progress of a player: the progress is read with 'quest status of ... for ...', 'quest step of ... for ...' and the other quest expressions.")
            .usage("Get one with 'quest \"id\"'.")
            .user("quests?")
            .examples("set {_q} to quest \"chasseur\"", "send quest name of {_q}")
            .since("1.6.0")
            .parser(new Parser<Quest>() {
                @Override
                public Quest parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(Quest o, int flags) {
                    return o.toString();
                }

                @Override
                public String toVariableNameString(Quest o) {
                    return "quest:" + o.id();
                }
            })
        );
    }
}
