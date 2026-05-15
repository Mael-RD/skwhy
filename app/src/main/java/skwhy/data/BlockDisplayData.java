package skwhy.data;


import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe pour stocker les données d'une BlockDisplay entity.
 * Stocke les données du bloc en tant que chaîne (Material+blockstate).
 */
public class BlockDisplayData extends DisplayData {
    
    private String blockData; // Format: "MATERIAL[state1=value1,state2=value2,...]"
    
    public BlockDisplayData() {
        this(1f, 1f, 1f, -1, 0f, 1f, 128f, 0, "STONE");
    }

    public BlockDisplayData(
        float scaleX,
        float scaleY,
        float scaleZ,
        int glowColor,
        float shadowRadius,
        float shadowStrength,
        float viewRange, 
        int billboardMode,
        String blockData
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
        this.blockData = (blockData != null) ? blockData : "STONE";
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityTypes.BLOCK_DISPLAY;
    }

    @Override
    protected List<EntityData<?>> buildSpecificMetadata() {
        List<EntityData<?>> data = new ArrayList<>();
        var blockState = SpigotConversionUtil.fromBukkitBlockData(
            org.bukkit.Bukkit.createBlockData(blockData != null ? blockData : "minecraft:air")
        );
        data.add(new EntityData<Integer>(23, EntityDataTypes.BLOCK_STATE, blockState.getGlobalId()));
        return data;
    }

    public void setBlockData(String blockData) {
        this.blockData = blockData;
    }
    
    /**
     * Récupère les données du bloc.
     */
    public String getBlockData() {
        return blockData;
    }
    
    @Override
    public String getDisplayType() {
        return "BlockDisplay";
    }

    @Override
    public String serialize() {
        return String.format(
            "BlockDisplay{" + "scaleX=%.2f,scaleY=%.2f,scaleZ=%.2f,blockData=%s,glowColor=%d," +
            "shadowRadius=%.2f,shadowStrength=%.2f,viewRange=%.2f,billboardMode=%d}",
            scaleX, scaleY, scaleZ, blockData, glowColor,
            shadowRadius, shadowStrength, viewRange, billboardMode
        );
    }

    @Override
    public String toString() {
        return "block display [" +
            "block="     + blockData       + ", " +
            "scale=("    + scaleX          + ", " + scaleY + ", " + scaleZ + "), " +
            "billboard=" + billboardMode   + ", " +
            "shadow="    + shadowRadius    + ", " +
            "range="     + viewRange       +
        "]";
    }
}
