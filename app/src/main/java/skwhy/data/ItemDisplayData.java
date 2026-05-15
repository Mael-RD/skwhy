package skwhy.data;


import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;


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
    this(1f, 1f, 1f, -1, 0f, 1f, 128f, 0, "STONE", 0);
}
    public ItemDisplayData(
        float scaleX,
        float scaleY,
        float scaleZ,
        int glowColor, 
        float shadowRadius,
        float shadowStrength,
        float viewRange, 
        int billboardMode,
        String itemStack,
        int displayMode
    ) {
        super();
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
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
            "scaleX=%.2f,scaleY=%.2f,scaleZ=%.2f,itemStack=%s,displayMode=%d(%s)," +
            "glowColor=%d,shadowRadius=%.2f,shadowStrength=%.2f,viewRange=%.2f," +
            "billboardMode=%d}",
            scaleX, scaleY, scaleZ, itemStack, displayMode, getDisplayModeName(),
            glowColor, shadowRadius, shadowStrength, viewRange,
            billboardMode
        );
    }
    @Override
    public String toString() {
        return "item display [" +
            "item="      + itemStack           + ", " +
            "mode="      + getDisplayModeName() + ", " +
            "scale=("    + scaleX + ", " + scaleY + ", " + scaleZ + "), " +
            "billboard=" + billboardMode       + ", " +
            "shadow="    + shadowRadius        + ", " +
            "range="     + viewRange           +
        "]";
    }
}
