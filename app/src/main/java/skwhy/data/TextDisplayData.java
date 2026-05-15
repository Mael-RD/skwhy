package skwhy.data;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe pour stocker les données d'une TextDisplay entity.
 * Stocke le texte et tous ses paramètres de formatage en tant que chaînes.
 */
public class TextDisplayData extends DisplayData {
    
    private String text; // Texte à afficher (supporte les couleurs et formatage Minecraft)
    private int textColor; // RGB couleur du texte
    private int backgroundColor; // RGB couleur de fond
    private boolean hasOutline;
    private int textAlignment; // 0=center, 1=left, 2=right
    private int lineWidth; // Largeur de ligne maximale (0=illimité)
    private boolean seeThrough;
    private boolean defaultBackground;

    public TextDisplayData() {
        this("Text");
    }

    public TextDisplayData(String text) {
        this(new Vector3f(1f, 1f, 1f), text);
    }

    public TextDisplayData(Vector3f scale, String text) {
        this(scale, new Vector3f(0f, 0f, 0f), text);
    }

    public TextDisplayData(Vector3f scale, Vector3f translation, String text) {
        this(scale, translation, new Quaternion4f(0f, 0f, 0f, 1f), text);
    }

    public TextDisplayData(Vector3f scale, Vector3f translation, Quaternion4f leftRotation, String text) {
        this(scale, translation, leftRotation, new Quaternion4f(0f, 0f, 0f, 1f), text);
    }

    public TextDisplayData(Vector3f scale, Vector3f translation, Quaternion4f leftRotation, Quaternion4f rightRotation, String text) {
        this(scale, translation, leftRotation, rightRotation, -1, 0f, 1f, 128f, 0, text, 0xFFFFFF, 0x000000, false, 0, 0, false, true);
    }

    public TextDisplayData(
        Vector3f scale, Vector3f translation, Quaternion4f leftRotation, Quaternion4f rightRotation,
        int glowColor, 
        float shadowRadius, float shadowStrength, 
        float viewRange, 
        int billboardMode, String text, int textColor, 
        int backgroundColor, boolean hasOutline, int textAlignment, 
        int lineWidth, boolean seeThrough, boolean defaultBackground
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
        this.text = (text != null) ? text : "Text";
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.hasOutline = hasOutline;
        this.textAlignment = textAlignment;
        this.lineWidth = lineWidth;
        this.seeThrough = seeThrough;
        this.defaultBackground = defaultBackground;
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityTypes.TEXT_DISPLAY;
    }

    @Override
    protected List<EntityData<?>> buildSpecificMetadata() {
        List<EntityData<?>> data = new ArrayList<>();
        
        return data;
    }

    /**
     * Définit le texte à afficher.
     * Supporte les codes couleur Minecraft: &0-&9, &a-&f, &l, &o, &n, &m, &k, &r
     */
    public void setText(String text) {
        this.text = text;
        markDirty();
    }
    
    /**
     * Récupère le texte.
     */
    public String getText() {
        return text;
    }
    
    /**
     * Définit la couleur du texte (format RGB).
     */
    public void setTextColor(int rgb) {
        this.textColor = rgb;
        markDirty();
    }
    
    /**
     * Récupère la couleur du texte.
     */
    public int getTextColor() {
        return textColor;
    }
    
    /**
     * Définit la couleur de fond (format RGB).
     */
    public void setBackgroundColor(int rgb) {
        this.backgroundColor = rgb;
        markDirty();
    }
    
    /**
     * Récupère la couleur de fond.
     */
    public int getBackgroundColor() {
        return backgroundColor;
    }
    
    /**
     * Active/désactive le contour du texte.
     */
    public void setOutline(boolean outline) {
        this.hasOutline = outline;
        markDirty();
    }
    
    /**
     * Vérifie si le texte a un contour.
     */
    public boolean hasOutline() {
        return hasOutline;
    }
    
    /**
     * Définit l'alignement du texte.
     * 0=center, 1=left, 2=right
     */
    public void setTextAlignment(int alignment) {
        this.textAlignment = alignment;
        markDirty();
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
        markDirty();
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
        markDirty();
    }
    
    /**
     * Vérifie si le texte est transparent.
     */
    public boolean isSeeThrough() {
        return seeThrough;
    }
    
    /**
     * Active/désactive le fond par défaut.
     */
    public void setDefaultBackground(boolean defaultBackground) {
        this.defaultBackground = defaultBackground;
        markDirty();
    }
    
    /**
     * Vérifie si le fond par défaut est activé.
     */
    public boolean hasDefaultBackground() {
        return defaultBackground;
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
            "text='%s',textColor=0x%06X,backgroundColor=0x%06X,outline=%b,alignment=%d(%s),lineWidth=%d," +
            "seeThrough=%b,defaultBackground=%b,glowColor=0x%06X,shadowRadius=%.2f,shadowStrength=%.2f,viewRange=%.2f,billboardMode=%d}",
            scale.x, scale.y, scale.z, translation.x, translation.y, translation.z,
            leftRotation, rightRotation,
            text, textColor, backgroundColor, hasOutline, textAlignment, getTextAlignmentName(), lineWidth,
            seeThrough, defaultBackground, glowColor, shadowRadius, shadowStrength, viewRange, billboardMode
        );
    }
    @Override
    public String toString() {
        return "text display [" +
            "text='"      + text + "', " +
            "color=#"     + String.format("%06X", textColor) + ", " +
            "bg=#"        + String.format("%06X", backgroundColor) + ", " +
            "align="      + getTextAlignmentName() + ", " +
            "outline="    + hasOutline + ", " +
            "through="    + seeThrough + ", " +
            "width="      + lineWidth + ", " +
            "scale=("     + scale.x + ", " + scale.y + ", " + scale.z + "), " +
            "translation=(" + translation.x + ", " + translation.y + ", " + translation.z + "), " +            "leftRotation=" + leftRotation + ", " +
            "rightRotation=" + rightRotation + ", " +            "billboard="  + billboardMode + ", " +
            "range="      + viewRange +
        "]";
    }
}
