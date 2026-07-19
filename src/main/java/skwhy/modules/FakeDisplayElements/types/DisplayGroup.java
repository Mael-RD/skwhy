package skwhy.modules.FakeDisplayElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import skwhy.data.DisplayGroupData;
import skwhy.data.DisplayData;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.List;

public class DisplayGroup {
    
    // Appelé une seule fois depuis init() du module
    public static void register() {
        Classes.registerClass(new ClassInfo<>(DisplayGroupData.class, "displaygroup")
            .name("Display Group")
            .description("A group of fake display entities (item, block, or text displays) that share a common location or attached entity, " +
                "and are sent to specific players via packets. Supports transformation, rotation, mirroring, mounting, and viewer management. " +
                "Can be serialized and restored across reloads when attached to a location.")
            .usage("Created via 'a new fake display group at %location/entity%', optionally with displays and players.")
            .user("display ?groups?")
            .examples(
                "set {_display} to [a new fake item display]:",
                "    item: dirt",
                "set {_group} to a new fake display group at player from {_display} with all players",
                "set group scale of {_group} to 2",
                "set group location of {_group} to location of player",
                "add player to viewers of {_group}",
                "mirror {_group} on axes xyz",
                "destroy display group {_group}"
            )
            .since("1.0.0")
            
            .parser(new Parser<>() {

                @Override
                public String toString(DisplayGroupData data, int flags) {
                    return this.toString();
                }

                @Override
                public String toVariableNameString(DisplayGroupData data) {
                    return "displaygroup:" + data.serialize();
                }
            })
            .parser(new Parser<DisplayGroupData>() {
                @Override
                public DisplayGroupData parse(String s, ParseContext context) {
                    return null; 
                }

                @Override
                public boolean canParse(ParseContext context) {
                    // Tu peux mettre false si ce type ne doit jamais être parsé depuis du texte
                    return false; 
                }

                @Override
                public String toString(DisplayGroupData o, int flags) {
                    return o.toString();
                }

                @Override
                public String toVariableNameString(DisplayGroupData o) {
                    return "displaygroup:" + o.toString();
                }
            })
            
            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(DisplayGroupData data) {
                    Fields fields = new Fields();

                    // 1. Sérialisation des displays
                    List<DisplayData> displays = data.getDisplays();
                    fields.putPrimitive("displayCount", displays.size());
                    for (int i = 0; i < displays.size(); i++) {
                        fields.putObject("display_" + i, displays.get(i));
                    }

                    // 2. Sérialisation de la position et de l'entité attachée
                    // On utilise getLocation() pour avoir la location actuelle (statique ou dynamique)
                    fields.putObject("location", data.getLocation()); 
                    fields.putObject("attachedEntity", data.getAttachedEntity());

                    return fields;
                }

                @Override
                public void deserialize(DisplayGroupData o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                    public DisplayGroupData deserialize(Fields fields) throws StreamCorruptedException {
                        try {
                            // 1. Récupération de la liste des displays
                            List<DisplayData> displays = new ArrayList<>();
                            int displayCount = fields.getPrimitive("displayCount", int.class);
                            for (int i = 0; i < displayCount; i++) {
                                DisplayData display = (DisplayData) fields.getObject("display_" + i);
                                if (display != null) {
                                    displays.add(display);
                                }
                            }

                            // 2. Récupération des données de position
                            Location loc = (Location) fields.getObject("location");
                            Entity entity = (Entity) fields.getObject("attachedEntity");

                            // 3. Choix du constructeur selon les données présentes (Priorité à l'entité)
                            if (entity != null) {
                                if (displays.isEmpty()) {
                                    return new DisplayGroupData(entity);
                                } else {
                                    return new DisplayGroupData(displays, entity);
                                }
                            } else if (loc != null) {
                                if (displays.isEmpty()) {
                                    return new DisplayGroupData(loc);
                                } else {
                                    return new DisplayGroupData(displays, loc);
                                }
                            }

                            // Fallback de sécurité si aucune position n'a été trouvée
                            throw new StreamCorruptedException("Aucune location ou entité trouvée pour le DisplayGroupData");

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
