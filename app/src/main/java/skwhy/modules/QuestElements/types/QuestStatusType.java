package skwhy.modules.QuestElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import skwhy.quest.model.QuestStatus;

public class QuestStatusType {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(QuestStatus.class, "queststatus")
            .name("[Quest] Quest Status Type")
            .description("The status of a quest for a player: unknown (never unlocked), available (unlocked, no progress yet), started or finished.")
            .usage("unknown, available, started, finished.")
            .user("quest ?status(es)?")
            .examples("if quest status of quest \"chasseur\" for player is finished:", "	send \"Quête terminée\" to player")
            .since("1.6.0")
            .parser(new Parser<QuestStatus>() {
                @Override
                public QuestStatus parse(String s, ParseContext context) {
                    QuestStatus status = QuestStatus.parse(s);
                    return status;
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return true;
                }

                @Override
                public String toString(QuestStatus o, int flags) {
                    return o.id();
                }

                @Override
                public String toVariableNameString(QuestStatus o) {
                    return o.id();
                }
            })
        );
    }
}
