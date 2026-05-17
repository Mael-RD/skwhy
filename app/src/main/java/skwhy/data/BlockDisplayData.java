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
        this(
            new Vec3(1f, 1f, 1f),
            new Vec3(0f, 0f, 0f),
            new Quat4(1f, 0f, 0f, 1f),
            new Quat4(1f, 0f, 0f, 1f),
            -1,
            1f,
            0f,
            128f,
            0, 
            0,
            0,
            "STONE");
    }

    public BlockDisplayData(
        Vec3 scale,
        Vec3 translation,
        Quat4 leftRotation,
        Quat4 rightRotation,
        int glowColor,
        float shadowRadius,
        float shadowStrength,
        float viewRange, 
        int billboardMode,
        int interpolationStart,
        int interpolationDuration,
        String blockData
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
        var blockState = SpigotConversionUtil.fromBukkitBlockData(
            org.bukkit.Bukkit.createBlockData(blockData != null ? blockData : "minecraft:air")
        );
        cachedDataRemove(23);
        cachedData.add(new EntityData<Integer>(23, EntityDataTypes.BLOCK_STATE, blockState.getGlobalId()));
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
            "BlockDisplay{" + "scale=(%.2f,%.2f,%.2f),translation=(%.2f,%.2f,%.2f),leftRotation=%s,rightRotation=%s," +
            "blockData=%s,glowColor=%d,shadowRadius=%.2f,shadowStrength=%.2f,viewRange=%.2f,billboardMode=%d}",
            scale.x, scale.y, scale.z, translation.x, translation.y, translation.z,
            leftRotation, rightRotation,
            blockData, glowColor, shadowRadius, shadowStrength, viewRange, billboardMode
        );
    }

    @Override
    public String toString() {
        return "block display [" +
            "block="        + blockData + ", " +
            "scale=("       + scale.x + ", " + scale.y + ", " + scale.z + "), " +
            "translation=(" + translation.x + ", " + translation.y + ", " + translation.z + "), " +
            "leftRotation=" + leftRotation + ", " +
            "rightRotation=" + rightRotation + ", " +
            "billboard="    + billboardMode + ", " +
            "shadow="       + shadowRadius + ", " +
            "range="        + viewRange +
        "]";
    }
    
    @Override
    protected DisplayData createEmptyClone() {
        BlockDisplayData clone = new BlockDisplayData();
        clone.blockData = this.blockData;
        return clone;
    }
    
}
