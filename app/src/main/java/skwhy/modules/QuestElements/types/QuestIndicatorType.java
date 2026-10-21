package skwhy.modules.QuestElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import skwhy.quest.model.QuestIndicator;

public class QuestIndicatorType {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(QuestIndicator.class, "questindicator")
            .name("[Quest] Quest Indicator Type")
            .description("The marker shown above a quest NPC for a player: new, main, secondary, region, info or none.")
            .usage("new, main, secondary, region, info, none.")
            .user("quest ?indicators?")
            .examples("show quest indicator new above quest npc \"maire\" to player")
            .since("1.6.0")
            .parser(new Parser<QuestIndicator>() {
                @Override
                public QuestIndicator parse(String s, ParseContext context) {
                    return QuestIndicator.parse(s);
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return true;
                }

                @Override
                public String toString(QuestIndicator o, int flags) {
                    return o.id();
                }

                @Override
                public String toVariableNameString(QuestIndicator o) {
                    return o.id();
                }
            })
        );
    }
}
