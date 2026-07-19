package skwhy.modules.EveryBlockChange;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import skwhy.modules.RandomStuffModule;

import java.lang.instrument.Instrumentation;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;
import org.skriptlang.skript.addon.SkriptAddon;

import ch.njol.skript.lang.util.SimpleEvent;

/**
 * Point d'entrée unique pour installer l'interception de LevelChunk#setBlockState.
 * Toute la logique de chargement passe par load(), qui délègue ensuite
 * aux méthodes privées correspondantes.
 */
public class BlockChangeEventLoader {

    private static boolean loaded = false;

    @SuppressWarnings("null")
    public static synchronized void load(RandomStuffModule modul, SkriptAddon addon) {
        if (loaded) return;

        Instrumentation instrumentation = attachAgent();
        Class<?> levelChunkClass = resolveLevelChunkClass();
        instrumentSetBlockState(instrumentation, levelChunkClass);
        
        
        // ── 1. Enregistrement de l'event Skript ──
        SyntaxRegistry syntaxRegistry = modul.moduleRegistry(addon);

        syntaxRegistry.register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(SimpleEvent.class, "Any Block Change")
                .addEvent(BlockChangeEvent.class)
                .addPattern("any block change")
                .addDescription("Fires when any block in the world changes.")
                .addExamples(
                    "on any block change:",
                    "\tset {_block} to event-block",
                    "\tsend \"Block changed at %{_block}%!\""
                )
                .addSince("1.3.4")
                .build()
        );

        // ── 2. Valeurs accessibles dans l'event ──
        EventValueRegistry valueRegistry = addon.registry(EventValueRegistry.class);

        // Bloc AVANT le changement
        valueRegistry.register(EventValue.builder(BlockChangeEvent.class, BlockData.class)
            .getter(BlockChangeEvent::getPreviousData)
            .time(EventValue.Time.PAST)
            .build());

        // Bloc APRÈS le changement
        valueRegistry.register(EventValue.builder(BlockChangeEvent.class, BlockData.class)
            .getter(BlockChangeEvent::getNewData)
            .time(EventValue.Time.FUTURE)
            .build());

        // Location du bloc (présent)
        valueRegistry.register(EventValue.builder(BlockChangeEvent.class, Location.class)
            .getter(BlockChangeEvent::getLocation)
            .time(EventValue.Time.NOW)
            .build());




        loaded = true;
    }

    private static Instrumentation attachAgent() {
        return ByteBuddyAgent.install();
    }

    private static Class<?> resolveLevelChunkClass() {
        try {
            // Mappings Mojang (Paper/Purpur 1.20.5+)
            return Class.forName("net.minecraft.world.level.chunk.LevelChunk");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "Impossible de résoudre LevelChunk. Version de serveur non supportée par BlockChangeEventLoader.", e);
        }
    }

    private static void instrumentSetBlockState(Instrumentation instrumentation, Class<?> levelChunkClass) {
        new ByteBuddy()
            .redefine(levelChunkClass)
            .visit(Advice.to(SetBlockStateAdvice.class)
                .on(ElementMatchers.named("setBlockState")))
            .make()
            .load(levelChunkClass.getClassLoader(),
                  ClassReloadingStrategy.fromInstalledAgent());
    }
}