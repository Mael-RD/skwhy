package skwhy.data;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe pour stocker les données d'une TextDisplay entity.
 * Stocke le texte et tous ses paramètres de formatage en tant que chaînes.
 */
public class TextDisplayData extends DisplayData {
    
    private String text;
    private int backgroundColor;
    private int textAlignment; // 0=center, 1=left, 2=right
    private int lineWidth; // Largeur de ligne maximale (0=illimité)
    private boolean seeThrough;

    public TextDisplayData() {
        this(
            new Vec3(1f, 1f, 1f),
            new Vec3(0f, 0f, 0f),
            new Quat4(0f, 0f, 0f, 1f),
            new Quat4(0f, 0f, 0f, 1f),
            -1,
            0f,
            1f,
            128f,
            0,
            0,
            0,
            "Text",
            0xFFFFFF,
            0,
            100,
            false
            );
    }

    public TextDisplayData(
        Vec3 scale, Vec3 translation, Quat4 leftRotation, Quat4 rightRotation,
        int glowColor, float shadowRadius, float shadowStrength, float viewRange, int billboardMode,
        int interpolationStart, int interpolationDuration,
        String text, int backgroundColor, int textAlignment, int lineWidth, boolean seeThrough
    ) {
        super();
        this.scale = scale;
        this.translation = translation;
        this.leftRotation = leftRotation;
        this.rightRotation = rightRotation;
        this.glowColor = glowColor;
        this.shadowRadius = shadowRadius;
        this.shadowStrength = shadowStrength;
        this.viewRange = viewRange;
        this.billboardMode = billboardMode;
        this.interpolationStart = interpolationStart;
        this.interpolationDuration = interpolationDuration;
        this.text = (text != null) ? text : "Text";
        this.textAlignment = textAlignment;
        this.lineWidth = lineWidth;
        this.seeThrough = seeThrough;
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityTypes.TEXT_DISPLAY;
    }

    @Override
    protected List<EntityData<?>> buildSpecificMetadata() {
        List<EntityData<?>> data = new ArrayList<>();

        // Index 23 – texte (Adventure Component, supporte les codes &)
        net.kyori.adventure.text.Component component =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand()
                .deserialize(text != null ? text : "");
        data.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, component));

        // Index 24 – largeur de ligne
        data.add(new EntityData<>(24, EntityDataTypes.INT, lineWidth));

        // Index 25 – couleur de fond (ARGB)
        // On force l'alpha à 0xFF pour un fond opaque
        int argbBackground = 0xFF000000 | (backgroundColor & 0xFFFFFF);
        data.add(new EntityData<>(25, EntityDataTypes.INT, argbBackground));

        // Index 26 – opacité du texte (-1 = 0xFF = complètement opaque)
        data.add(new EntityData<>(26, EntityDataTypes.BYTE, (byte) -1));

        // Index 27 – flags de style (byte)
        // bit 0 (0x01) : text shadow
        // bit 1 (0x02) : see through
        // bit 2 (0x04) : default background
        // bits 3-4     : alignment (0=center, 1=left, 2=right)
        byte styleFlags = 0;
        if (seeThrough)   styleFlags |= 0x02;
        styleFlags |= (byte) ((textAlignment & 0x03) << 3);
        data.add(new EntityData<>(27, EntityDataTypes.BYTE, styleFlags));

        return data;
    }

    /**
     * Définit le texte à afficher.
     * Supporte les codes couleur Minecraft: &0-&9, &a-&f, &l, &o, &n, &m, &k, &r
     */
    public void setText(String text) {
        this.text = text;
        cachedDataRemove(23);
        net.kyori.adventure.text.Component component =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand()
                .deserialize(text != null ? text : "");
        cachedData.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, component));
    }
    
    /**
     * Récupère le texte.
     */
    public String getText() {
        return text;
    }
    
    /**
     * Définit la couleur de fond (format RGB).
     */
    public void setBackgroundColor(int rgb) {
        this.backgroundColor = rgb;
        cachedDataRemove(25);
        int argbBackground = 0xFF000000 | (backgroundColor & 0xFFFFFF);
        cachedData.add(new EntityData<>(25, EntityDataTypes.INT, argbBackground));
    }
    
    /**
     * Récupère la couleur de fond.
     */
    public int getBackgroundColor() {
        return backgroundColor;
    }
    
    /**
     * Définit l'alignement du texte.
     * 0=center, 1=left, 2=right
     */
    public void setTextAlignment(int alignment) {
        this.textAlignment = alignment;
        cachedDataRemove(27);
        byte styleFlags = 0;
        if (seeThrough)   styleFlags |= 0x02;
        styleFlags |= (byte) ((textAlignment & 0x03) << 3);
        cachedData.add(new EntityData<>(27, EntityDataTypes.BYTE, styleFlags));
    }
    
    /**
     * Récupère l'alignement du texte.
     */
    public int getTextAlignment() {
        return textAlignment;
    }
    
    public String getTextAlignmentName() {
        return switch(textAlignment) {
            case 1 -> "left";
            case 2 -> "right";
            default -> "center";
        };
    }
    
    /**
     * Définit la largeur de ligne maximale.
     * 0 = illimité
     */
    public void setLineWidth(int width) {
        this.lineWidth = width;
        cachedDataRemove(24);
        cachedData.add(new EntityData<>(24, EntityDataTypes.INT, lineWidth));
    }
    
    /**
     * Récupère la largeur de ligne.
     */
    public int getLineWidth() {
        return lineWidth;
    }
    
    /**
     * Active/désactive la transparence du texte.
     */
    public void setSeeThrough(boolean seeThrough) {
        this.seeThrough = seeThrough;
        cachedDataRemove(27);
        byte styleFlags = 0;
        if (seeThrough)   styleFlags |= 0x02;
        styleFlags |= (byte) ((textAlignment & 0x03) << 3);
        cachedData.add(new EntityData<>(27, EntityDataTypes.BYTE, styleFlags));
    }
    
    /**
     * Vérifie si le texte est transparent.
     */
    public boolean isSeeThrough() {
        return seeThrough;
    }
    
    @Override
    public String getDisplayType() {
        return "TextDisplay";
    }
    
    @Override
    public String serialize() {
        return String.format(
            "TextDisplay{" +
            "scale=(%.2f,%.2f,%.2f),translation=(%.2f,%.2f,%.2f),leftRotation=%s,rightRotation=%s," +
            "text='%s',backgroundColor=0x%06X,alignment=%d(%s),lineWidth=%d," +
            "seeThrough=%b,glowColor=0x%06X,shadowRadius=%.2f,shadowStrength=%.2f,viewRange=%.2f,billboardMode=%d}",
            scale.x, scale.y, scale.z, translation.x, translation.y, translation.z,
            leftRotation, rightRotation,
            text, backgroundColor, textAlignment, getTextAlignmentName(), lineWidth,
            seeThrough, glowColor, shadowRadius, shadowStrength, viewRange, billboardMode
        );
    }
    @Override
    public String toString() {
        return "text display [" +
            "text='"      + text + "', " +
            "bg=#"        + String.format("%06X", backgroundColor) + ", " +
            "align="      + getTextAlignmentName() + ", " +
            "through="    + seeThrough + ", " +
            "width="      + lineWidth + ", " +
            "scale=("     + scale.x + ", " + scale.y + ", " + scale.z + "), " +
            "translation=(" + translation.x + ", " + translation.y + ", " + translation.z + "), " +            "leftRotation=" + leftRotation + ", " +
            "rightRotation=" + rightRotation + ", " +            "billboard="  + billboardMode + ", " +
            "range="      + viewRange +
        "]";
    }
}
