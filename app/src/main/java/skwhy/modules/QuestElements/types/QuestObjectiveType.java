package skwhy.modules.QuestElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import skwhy.quest.model.QuestObjective;

public class QuestObjectiveType {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(QuestObjective.class, "questobjective")
            .name("[Quest] Quest Objective Type")
            .description("One objective of a quest step. Objectives are numbered from 1 in scripts.")
            .usage("Get them with 'current objectives of <quest> for <player>'.")
            .user("quest ?objectives?")
            .examples("loop current objectives of quest \"chasseur\" for player:", "	send quest objective text of loop-value")
            .since("1.6.0")
            .parser(new Parser<QuestObjective>() {
                @Override
                public QuestObjective parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(QuestObjective o, int flags) {
                    return o.toString();
                }

                @Override
                public String toVariableNameString(QuestObjective o) {
                    return null;
                }
            })
        );
    }
}
