package skwhy.modules.CustomEntityElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import skwhy.customentity.CustomEntity;

public class CustomEntityType {

    public static void register() {
        Classes.registerClass(new ClassInfo<>(CustomEntity.class, "customentity")
            .name("[Custom Entity] Custom Entity Type")
            .description("An instance of a custom entity: either real (an interaction entity of the server whose NBT only holds the id of its definition) "
                + "or fake (nothing on the server, only packets). Its look (entity type, name, equipment, scale...) comes from the definition "
                + "registered in the custom-entities folder of the plugin and is sent by packet to the nearby players.")
            .usage("Get one with 'new real custom entity', 'new fake custom entity' or 'custom entity of <interaction>'.")
            .user("custom ?entit(y|ies)")
            .examples("set {_pet} to a new fake custom entity \"example_wolf\" at location of player",
                "set {_real} to custom entity of target entity")
            .since("1.6.0")
            .parser(new Parser<CustomEntity>() {
                @Override
                public CustomEntity parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(CustomEntity o, int flags) {
                    return o.toString();
                }

                @Override
                public String toVariableNameString(CustomEntity o) {
                    return "customentity:" + o.visualUuid();
                }
            })
        );
    }
}
