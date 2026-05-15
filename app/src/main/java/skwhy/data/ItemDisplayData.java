package skwhy.data;


import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe pour stocker les données d'une ItemDisplay entity.
 * Stocke l'itemstack et son mode d'affichage en tant que chaîne.
 */
public class ItemDisplayData extends DisplayData {
    
    private String itemStack; // Format: "MATERIAL" ou "MATERIAL{nbt_data}"
    private int displayMode; // 0=none, 1=thirdperson_lefthand, 2=thirdperson_righthand, 3=firstperson_lefthand, 4=firstperson_righthand, 5=head, 6=gui, 7=ground, 8=fixed

    public ItemDisplayData() {
        this("STONE");
    }

    public ItemDisplayData(String itemStack) {
        this(new Vector3f(1f, 1f, 1f), itemStack);
    }

    public ItemDisplayData(Vector3f scale, String itemStack) {
        this(scale, new Vector3f(0f, 0f, 0f), itemStack);
    }

    public ItemDisplayData(Vector3f scale, Vector3f translation, String itemStack) {
        this(scale, translation, new Quaternion4f(0f, 0f, 0f, 1f), itemStack);
    }

    public ItemDisplayData(Vector3f scale, Vector3f translation, Quaternion4f leftRotation, String itemStack) {
        this(scale, translation, leftRotation, new Quaternion4f(0f, 0f, 0f, 1f), itemStack);
    }

    public ItemDisplayData(Vector3f scale, Vector3f translation, Quaternion4f leftRotation, Quaternion4f rightRotation, String itemStack) {
        this(scale, translation, leftRotation, rightRotation, -1, 0f, 1f, 128f, 0, itemStack, 0);
    }

    public ItemDisplayData(
        Vector3f scale,
        Vector3f translation,
        Quaternion4f leftRotation,
        Quaternion4f rightRotation,
        int glowColor, 
        float shadowRadius,
        float shadowStrength,
        float viewRange, 
        int billboardMode,
        String itemStack,
        int displayMode
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
        this.itemStack = (itemStack != null) ? itemStack : "STONE";
        this.displayMode = displayMode;
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityTypes.ITEM_DISPLAY;
    }

    @Override
    protected List<EntityData<?>> buildSpecificMetadata() {
        List<EntityData<?>> data = new ArrayList<>();
        
        return data;
    }

    /**
     * Définit l'itemstack.
     * Exemple: "DIAMOND_SWORD" ou "DIAMOND_SWORD{Enchantments:[{id:sharpness,lvl:5}]}"
     */
    public void setItemStack(String itemStack) {
        this.itemStack = itemStack;
        markDirty();
    }
    
    /**
     * Récupère l'itemstack.
     */
    public String getItemStack() {
        return itemStack;
    }
    
    /**
     * Définit le mode d'affichage.
     * 0=none, 1=thirdperson_lefthand, 2=thirdperson_righthand, 3=firstperson_lefthand,
     * 4=firstperson_righthand, 5=head, 6=gui, 7=ground, 8=fixed
     */
    public void setDisplayMode(int mode) {
        this.displayMode = mode;
        markDirty();
    }
    
    /**
     * Récupère le mode d'affichage.
     */
    public int getDisplayMode() {
        return displayMode;
    }
    
    public String getDisplayModeName() {
        return switch(displayMode) {
            case 1 -> "thirdperson_lefthand";
            case 2 -> "thirdperson_righthand";
            case 3 -> "firstperson_lefthand";
            case 4 -> "firstperson_righthand";
            case 5 -> "head";
            case 6 -> "gui";
            case 7 -> "ground";
            case 8 -> "fixed";
            default -> "none";
        };
    }
    
    @Override
    public String getDisplayType() {
        return "ItemDisplay";
    }
    
    @Override
    public String serialize() {
        return String.format(
            "ItemDisplay{" +
            "scale=(%.2f,%.2f,%.2f),translation=(%.2f,%.2f,%.2f),leftRotation=%s,rightRotation=%s," +
            "itemStack=%s,displayMode=%d(%s),glowColor=%d,shadowRadius=%.2f,shadowStrength=%.2f,viewRange=%.2f,billboardMode=%d}",
            scale.x, scale.y, scale.z, translation.x, translation.y, translation.z,
            leftRotation, rightRotation,
            itemStack, displayMode, getDisplayModeName(),
            glowColor, shadowRadius, shadowStrength, viewRange, billboardMode
        );
    }
    @Override
    public String toString() {
        return "item display [" +
            "item="        + itemStack + ", " +
            "mode="        + getDisplayModeName() + ", " +
            "scale=("      + scale.x + ", " + scale.y + ", " + scale.z + "), " +
            "translation=" + "(" + translation.x + ", " + translation.y + ", " + translation.z + "), " +
            "leftRotation=" + leftRotation + ", " +
            "rightRotation=" + rightRotation + ", " +
            "billboard="   + billboardMode + ", " +
            "shadow="      + shadowRadius + ", " +
            "range="       + viewRange +
        "]";
    }
}
