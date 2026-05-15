package skwhy.modules.FakeDisplayElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import skwhy.data.DisplayGroupData;
import org.jetbrains.annotations.Nullable;

import java.io.StreamCorruptedException;

public class DisplayGroup {
    
    // Appelé une seule fois depuis init() du module
    public static void register() {
        Classes.registerClass(new ClassInfo<>(DisplayGroupData.class, "displaygroup")
            .name("Display Group")
            .description("Un groupe de displays pouvant être contrôlées ensemble avec une position et une monture communes.")
            .usage("créé via 'new display group'")
            .examples(
                "set {_group} to new display group",
                "add {_display} to {_group}",
                "update {_group} with x = 100, y = 64"
            )
            .since("1.0.0")
            
            .parser(new Parser<>() {
                @Override
                public @Nullable DisplayGroupData parse(String s, ParseContext context) {
                    if (s.equalsIgnoreCase("display group")) {
                        return new DisplayGroupData();
                    }
                    return null;
                }

                @Override
                public String toString(DisplayGroupData data, int flags) {
                    return "display group [world=" + data.getWorld() 
                        + ", pos=(" + data.getX() + "," + data.getY() + "," + data.getZ() + ")"
                        + ", displays=" + data.getDisplays().size() + "]";
                }

                @Override
                public String toVariableNameString(DisplayGroupData data) {
                    return "displaygroup:" + data.getDisplays().size();
                }
            })
            
            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(DisplayGroupData data) {
                    Fields fields = new Fields();
                    fields.putObject("world", data.getWorld());
                    fields.putPrimitive("x", data.getX());
                    fields.putPrimitive("y", data.getY());
                    fields.putPrimitive("z", data.getZ());
                    fields.putPrimitive("yaw", data.getYaw());
                    fields.putPrimitive("pitch", data.getPitch());
                    fields.putPrimitive("displayCount", data.getDisplays().size());
                    return fields;
                }

                @Override
                public void deserialize(DisplayGroupData o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public DisplayGroupData deserialize(Fields fields) throws StreamCorruptedException {
                    try {
                        DisplayGroupData data = new DisplayGroupData();
                        data.setWorld((String) fields.getObject("world"));
                        data.setX(fields.getPrimitive("x", double.class));
                        data.setY(fields.getPrimitive("y", double.class));
                        data.setZ(fields.getPrimitive("z", double.class));
                        data.setYaw(fields.getPrimitive("yaw", float.class));
                        data.setPitch(fields.getPrimitive("pitch", float.class));
                        return data;
                    } catch (Exception e) {
                        throw new StreamCorruptedException("Impossible de désérialiser DisplayGroupData : " + e.getMessage());
                    }
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
