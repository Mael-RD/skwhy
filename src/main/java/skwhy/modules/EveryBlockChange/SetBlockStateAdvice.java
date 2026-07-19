package skwhy.modules.EveryBlockChange;

import net.bytebuddy.asm.Advice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
public class SetBlockStateAdvice {

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.This LevelChunk chunk,
            @Advice.Argument(0) BlockPos pos,
            @Advice.Argument(1) BlockState newState,
            // Argument(2) supprimé — son type varie selon la version (boolean ou int)
            @Advice.Return BlockState oldState) {

        if (oldState == null) return;
        if (oldState == newState) return;

        BlockChangeDispatcher.dispatch(chunk, pos, oldState, newState);
    }
}