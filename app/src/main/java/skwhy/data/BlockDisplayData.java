package skwhy.data;


import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
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
        this("STONE");
    }

    public BlockDisplayData(String blockData) {
        this(new Vector3f(1f, 1f, 1f), blockData);
    }

    public BlockDisplayData(Vector3f scale, String blockData) {
        this(scale, new Vector3f(0f, 0f, 0f), blockData);
    }

    public BlockDisplayData(Vector3f scale, Vector3f translation, String blockData) {
        this(scale, translation, new Quaternion4f(0f, 0f, 0f, 1f), blockData);
    }

    public BlockDisplayData(Vector3f scale, Vector3f translation, Quaternion4f leftRotation, String blockData) {
        this(scale, translation, leftRotation, new Quaternion4f(0f, 0f, 0f, 1f), blockData);
    }

    public BlockDisplayData(Vector3f scale, Vector3f translation, Quaternion4f leftRotation, Quaternion4f rightRotation, String blockData) {
        this(scale, translation, leftRotation, rightRotation, -1, 0f, 1f, 128f, 0, blockData);
    }

    public BlockDisplayData(
        Vector3f scale,
        Vector3f translation,
        Quaternion4f leftRotation,
        Quaternion4f rightRotation,
        int glowColor,
        float shadowRadius,
        float shadowStrength,
        float viewRange, 
        int billboardMode,
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
        markDirty();
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
}
