package skwhy.modules.FakeDisplayElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import skwhy.data.DisplayData;
import skwhy.data.BlockDisplayData;
import skwhy.data.ItemDisplayData;
import skwhy.data.TextDisplayData;
import skwhy.data.Vec3;
import skwhy.data.Quat4;

import org.jetbrains.annotations.Nullable;

import java.io.StreamCorruptedException;

public class Display {
    
    // Appelé une seule fois depuis init() du module
    public static void register() {
        // Enregistrement de la classe de base DisplayData
        registerDisplayDataClass();
        
        // Enregistrement de BlockDisplayData
        registerBlockDisplayData();
        
        // Enregistrement de ItemDisplayData
        registerItemDisplayData();
        
        // Enregistrement de TextDisplayData
        registerTextDisplayData();
    }
    
    /**
     * Enregistre la classe de base DisplayData dans Skript
     */
    private static void registerDisplayDataClass() {
        Classes.registerClass(new ClassInfo<>(DisplayData.class, "displaydata")
            .name("Display Data")
            .description("Représente les données d'une display entity Minecraft.")
            .usage("obtenu via les expressions de skwhy")
            .examples(
                "set {_data} to new block display",
                "update {_data} with x = 100, y = 64"
            )
            .since("1.0.0")
            
            .parser(new Parser<>() {
                @Override
                public @Nullable DisplayData parse(String s, ParseContext context) {
                    return null; // Pas de syntaxe littérale
                }

                @Override
                public String toString(DisplayData data, int flags) {
                    return data.getDisplayType();
                }

                @Override
                public String toVariableNameString(DisplayData data) {
                    return "displaydata:" + data.getDisplayType() + ":" + data.serialize();
                }
            })
            
            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(DisplayData data) {
                    Fields fields = new Fields();
                    fields.putObject("type", data.getDisplayType());
                    fields.putObject("data", data.serialize());
                    return fields;
                }

                @Override
                public void deserialize(DisplayData o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public DisplayData deserialize(Fields fields) throws StreamCorruptedException {
                    try {
                        String type = (String) fields.getObject("type");
                        // String data = (String) fields.getObject("data");
                        
                        return switch(type) {
                            case "BlockDisplay" -> new BlockDisplayData();
                            case "ItemDisplay" -> new ItemDisplayData();
                            case "TextDisplay" -> new TextDisplayData();
                            default -> null;
                        };
                    } catch (Exception e) {
                        throw new StreamCorruptedException("Impossible de désérialiser DisplayData : " + e.getMessage());
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
    
    /**
     * Enregistre BlockDisplayData dans Skript
     */
    private static void registerBlockDisplayData() {
        Classes.registerClass(new ClassInfo<>(BlockDisplayData.class, "blockdisplay")
            .name("Block Display")
            .description("Une display entity qui affiche un bloc.")
            .usage("créée via 'new block display'")
            .examples(
                "set {_display} to new block display",
                "set block data of {_display} to \"OAK_LOG[axis=y]\""
            )
            .since("1.0.0")
            
            .parser(new Parser<>() {
                @Override
                public @Nullable BlockDisplayData parse(String s, ParseContext context) {
                    if (s.equalsIgnoreCase("block display")) {
                        return new BlockDisplayData();
                    }
                    return null;
                }

                @Override
                public String toString(BlockDisplayData data, int flags) {
                    return "block display";
                }

                @Override
                public String toVariableNameString(BlockDisplayData data) {
                    return "blockdisplay:" + data.serialize();
                }
            })
            
            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(BlockDisplayData data) {
                    Fields fields = new Fields();
                    fields.putObject("scale", data.getScale());
                    fields.putObject("translation", data.getTranslation());
                    fields.putObject("leftRotation", data.getLeftRotation());
                    fields.putObject("rightRotation", data.getRightRotation());
                    fields.putObject("blockData", data.getBlockData());
                    fields.putPrimitive("glowColor", data.getGlowColor());
                    fields.putPrimitive("shadowRadius", data.getShadowRadius());
                    fields.putPrimitive("shadowStrength", data.getShadowStrength());
                    fields.putPrimitive("viewRange", data.getViewRange());
                    fields.putPrimitive("billboardMode", data.getBillboardMode());
                    return fields;
                }

                @Override
                public void deserialize(BlockDisplayData o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public BlockDisplayData deserialize(Fields fields) throws StreamCorruptedException {
                    try {
                        BlockDisplayData data = new BlockDisplayData();
                        data.setScale(fields.getObject("scale", Vec3.class));
                        data.setTranslation(fields.getObject("translation", Vec3.class));
                        data.setLeftRotation(fields.getObject("leftRotation", Quat4.class));
                        data.setRightRotation(fields.getObject("rightRotation", Quat4.class));
                        data.setBlockData((String) fields.getObject("blockData"));
                        data.setGlowColor(fields.getPrimitive("glowColor", int.class));
                        data.setShadowRadius(fields.getPrimitive("shadowRadius", float.class));
                        data.setShadowStrength(fields.getPrimitive("shadowStrength", float.class));
                        data.setViewRange(fields.getPrimitive("viewRange", float.class));
                        data.setBillboardMode(fields.getPrimitive("billboardMode", int.class));
                        return data;
                    } catch (Exception e) {
                        throw new StreamCorruptedException("Impossible de désérialiser BlockDisplayData : " + e.getMessage());
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
            
            .parser(new Parser<BlockDisplayData>() {
                @Override
                public @Nullable BlockDisplayData parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public String toString(BlockDisplayData data, int flags) {
                    return data.toString();
                }

                @Override
                public String toVariableNameString(BlockDisplayData data) {
                    return "blockdisplay:" + data.serialize();
                }
            })
        );
    }
    
    /**
     * Enregistre ItemDisplayData dans Skript
     */
    private static void registerItemDisplayData() {
        Classes.registerClass(new ClassInfo<>(ItemDisplayData.class, "itemdisplay")
            .name("Item Display")
            .description("Une display entity qui affiche un item.")
            .usage("créée via 'new item display'")
            .examples(
                "set {_display} to new item display",
                "set item stack of {_display} to \"DIAMOND_SWORD\""
            )
            .since("1.0.0")
            
            .parser(new Parser<>() {
                @Override
                public @Nullable ItemDisplayData parse(String s, ParseContext context) {
                    if (s.equalsIgnoreCase("item display")) {
                        return new ItemDisplayData();
                    }
                    return null;
                }

                @Override
                public String toString(ItemDisplayData data, int flags) {
                    return "item display [item=" + data.getItemStack() + ", mode=" + data.getDisplayModeName() + "]";
                }

                @Override
                public String toVariableNameString(ItemDisplayData data) {
                    return "itemdisplay:" + data.serialize();
                }
            })
            
            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(ItemDisplayData data) {
                    Fields fields = new Fields();
                    fields.putObject("scale", data.getScale());
                    fields.putObject("translation", data.getTranslation());
                    fields.putObject("leftRotation", data.getLeftRotation());
                    fields.putObject("rightRotation", data.getRightRotation());
                    fields.putObject("itemStack", data.getItemStack());
                    fields.putPrimitive("displayMode", data.getDisplayMode());
                    fields.putPrimitive("glowColor", data.getGlowColor());
                    fields.putPrimitive("shadowRadius", data.getShadowRadius());
                    fields.putPrimitive("shadowStrength", data.getShadowStrength());
                    fields.putPrimitive("viewRange", data.getViewRange());
                    fields.putPrimitive("billboardMode", data.getBillboardMode());
                    return fields;
                }

                @Override
                public void deserialize(ItemDisplayData o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ItemDisplayData deserialize(Fields fields) throws StreamCorruptedException {
                    try {
                        ItemDisplayData data = new ItemDisplayData();
                        data.setScale(fields.getObject("scale", Vec3.class));
                        data.setTranslation(fields.getObject("translation", Vec3.class));
                        data.setLeftRotation(fields.getObject("leftRotation", Quat4.class));
                        data.setRightRotation(fields.getObject("rightRotation", Quat4.class));
                        data.setItemStack((String) fields.getObject("itemStack"));
                        data.setDisplayMode(fields.getPrimitive("displayMode", int.class));
                        data.setGlowColor(fields.getPrimitive("glowColor", int.class));
                        data.setShadowRadius(fields.getPrimitive("shadowRadius", float.class));
                        data.setShadowStrength(fields.getPrimitive("shadowStrength", float.class));
                        data.setViewRange(fields.getPrimitive("viewRange", float.class));
                        data.setBillboardMode(fields.getPrimitive("billboardMode", int.class));
                        return data;
                    } catch (Exception e) {
                        throw new StreamCorruptedException("Impossible de désérialiser ItemDisplayData : " + e.getMessage());
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
            
            .parser(new Parser<ItemDisplayData>() {
                @Override
                public @Nullable ItemDisplayData parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public String toString(ItemDisplayData data, int flags) {
                    return data.toString();
                }

                @Override
                public String toVariableNameString(ItemDisplayData data) {
                    return "itemdisplay:" + data.serialize();
                }
            })
        );
    }
    
    /**
     * Enregistre TextDisplayData dans Skript
     */
    private static void registerTextDisplayData() {
        Classes.registerClass(new ClassInfo<>(TextDisplayData.class, "textdisplay")
            .name("Text Display")
            .description("Une display entity qui affiche du texte.")
            .usage("créée via 'new text display'")
            .examples(
                "set {_display} to new text display",
                "set text of {_display} to \"Hello World\""
            )
            .since("1.0.0")
            
            .parser(new Parser<>() {
                @Override
                public @Nullable TextDisplayData parse(String s, ParseContext context) {
                    if (s.equalsIgnoreCase("text display")) {
                        return new TextDisplayData();
                    }
                    return null;
                }

                @Override
                public String toString(TextDisplayData data, int flags) {
                    return "text display [text='" + data.getText() + "']";
                }

                @Override
                public String toVariableNameString(TextDisplayData data) {
                    return "textdisplay:" + data.serialize();
                }
            })
            
            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(TextDisplayData data) {
                    Fields fields = new Fields();
                    fields.putObject("scale", data.getScale());
                    fields.putObject("translation", data.getTranslation());
                    fields.putObject("leftRotation", data.getLeftRotation());
                    fields.putObject("rightRotation", data.getRightRotation());
                    fields.putObject("text", data.getText());
                    fields.putPrimitive("bgColor", data.getBackgroundColor());
                    fields.putPrimitive("alignment", data.getTextAlignment());
                    fields.putPrimitive("lineWidth", data.getLineWidth());
                    fields.putPrimitive("seeThrough", data.isSeeThrough());
                    fields.putPrimitive("glowColor", data.getGlowColor());
                    fields.putPrimitive("shadowRadius", data.getShadowRadius());
                    fields.putPrimitive("shadowStrength", data.getShadowStrength());
                    fields.putPrimitive("viewRange", data.getViewRange());
                    fields.putPrimitive("billboardMode", data.getBillboardMode());
                    return fields;
                }

                @Override
                public void deserialize(TextDisplayData o, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public TextDisplayData deserialize(Fields fields) throws StreamCorruptedException {
                    try {
                        TextDisplayData data = new TextDisplayData();
                        data.setScale(fields.getObject("scale", Vec3.class));
                        data.setTranslation(fields.getObject("translation", Vec3.class));
                        data.setLeftRotation(fields.getObject("leftRotation", Quat4.class));
                        data.setRightRotation(fields.getObject("rightRotation", Quat4.class));
                        data.setText((String) fields.getObject("text"));
                        data.setBackgroundColor(fields.getPrimitive("bgColor", int.class));
                        data.setTextAlignment(fields.getPrimitive("alignment", int.class));
                        data.setLineWidth(fields.getPrimitive("lineWidth", int.class));
                        data.setSeeThrough(fields.getPrimitive("seeThrough", boolean.class));
                        data.setGlowColor(fields.getPrimitive("glowColor", int.class));
                        data.setShadowRadius(fields.getPrimitive("shadowRadius", float.class));
                        data.setShadowStrength(fields.getPrimitive("shadowStrength", float.class));
                        data.setViewRange(fields.getPrimitive("viewRange", float.class));
                        data.setBillboardMode(fields.getPrimitive("billboardMode", int.class));
                        return data;
                    } catch (Exception e) {
                        throw new StreamCorruptedException("Impossible de désérialiser TextDisplayData : " + e.getMessage());
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
            
            .parser(new Parser<TextDisplayData>() {
                @Override
                public @Nullable TextDisplayData parse(String s, ParseContext context) {
                    return null;
                }

                @Override
                public String toString(TextDisplayData data, int flags) {
                    return data.toString();
                }

                @Override
                public String toVariableNameString(TextDisplayData data) {
                    return "textdisplay:" + data.serialize();
                }
            })
        );
    }
}
