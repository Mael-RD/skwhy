package skwhy.modules.FakeDisplayElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import org.skriptlang.skript.lang.converter.Converters;
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


        // 2. ── ENREGISTREMENT DES CONVERTISSEURS POLYMORPHIQUES (NOUVELLE API) ──
        // On utilise la classe statique "Converters" du package org.skriptlang
        Converters.registerConverter(
            BlockDisplayData.class, 
            DisplayData.class, 
            new org.skriptlang.skript.lang.converter.Converter<BlockDisplayData, DisplayData>() {
                @Override
                public DisplayData convert(BlockDisplayData blockData) {
                    return blockData;
                }
            }
        );

        Converters.registerConverter(
            ItemDisplayData.class, 
            DisplayData.class, 
            new org.skriptlang.skript.lang.converter.Converter<ItemDisplayData, DisplayData>() {
                @Override
                public DisplayData convert(ItemDisplayData itemData) {
                    return itemData;
                }
            }
        );

        Converters.registerConverter(
            TextDisplayData.class, 
            DisplayData.class, 
            new org.skriptlang.skript.lang.converter.Converter<TextDisplayData, DisplayData>() {
                @Override
                public DisplayData convert(TextDisplayData textData) {
                    return textData;
                }
            }
        );
    }
    
    /**
     * Enregistre la classe de base DisplayData dans Skript
     */
    private static void registerDisplayDataClass() {
        Classes.registerClass(new ClassInfo<>(DisplayData.class, "displaydata")
            .name("Display Data")
            .description("Base type representing a fake display entity. " +
                "Can be a block, item, or text display. " +
                "Cannot be parsed from text — instances are created via the dedicated section syntax for each subtype.")
            .usage("Created via 'set %objects% to a new fake block/item/text display'.")
            .user("display ?datas?")
            .examples(
                "set {_display} to [a new fake item display]:",
                "    item: dirt",
                "set {_id} to entity id of {_display}",
                "set {_uuid} to entity uuid of {_display}",
                "set group scale of {_display} to vector(2, 2, 2)",
                "set group translation of {_display} to vector(0, 1, 0)"
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
            .description("A fake display entity that renders a block. " +
                "Supports all common display properties (scale, translation, rotations, glow, shadow, view range, billboard) " +
                "plus a block data string that defines which block is shown. " +
                "Fully serializable for persistent storage in Skript variables.")
            .usage("Created via 'set %objects% to a new fake block display'.")
            .user("display ?block ?datas?")
            .examples(
                "set {_display} to [a new fake block display]:",
                "    block: stone",
                "    scale: vector(1, 1, 1)",
                "    shadow: 0.5",
                "set block data of {_display} to oak_log",
                "set group scale of {_display} to vector(2, 2, 2)"
            )
            .since("1.0.0")
            
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
            .description("A fake display entity that renders an item or block item model. " +
                "Supports all common display properties (scale, translation, rotations, glow, shadow, view range, billboard) " +
                "plus an item stack identifier and a display mode (none/thirdperson/firstperson/head/gui/ground/fixed). " +
                "Also supports player head skin values via the 'head' key. " +
                "Fully serializable for persistent storage in Skript variables.")
            .usage("Created via 'set %objects% to a new fake item display'.")
            .user("display ?item ?datas?")
            .examples(
                "set {_display} to [a new fake item display]:",
                "    item: dirt",
                "    scale: vector(1, 1, 1)",
                "    mode: head",
                "set item of {_display} to diamond sword",
                "set display mode of {_display} to 5"
            )
            .since("1.0.0")
            
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
            .description("A fake display entity that renders formatted text. " +
                "Supports all common display properties (scale, translation, rotations, glow, shadow, view range, billboard) " +
                "plus text content, background color, text alignment (center/left/right), line width, and see-through mode. " +
                "Fully serializable for persistent storage in Skript variables.")
            .usage("Created via 'set %objects% to a new fake text display'.")
            .user("display ?text ?datas?")
            .examples(
                "set {_display} to [a new fake text display]:",
                "    text: \"&aHello world!\"",
                "    seethrough: true",
                "    alignment: center",
                "    background: 0",
                "    linewidth: 200",
                "set text of {_display} to \"&cUpdated!\"",
                "set see through of {_display} to false",
                "set background color of {_display} to 255"
            )
            .since("1.0.0")
            
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
