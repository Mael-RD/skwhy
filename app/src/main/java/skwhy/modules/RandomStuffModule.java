package skwhy.modules;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

import com.github.retrooper.packetevents.PacketEvents;

import ch.njol.skript.lang.util.SimpleEvent;
import skwhy.SkWhy;
import skwhy.modules.RandomStuff.expressions.*;
import skwhy.modules.RandomStuff.effects.*;
import skwhy.modules.RandomStuff.events.Votes;

public class RandomStuffModule implements AddonModule {

    @Override
    public String name() {
        return "RandomStuffModule";
    }

    @Override
    public boolean canLoad(SkriptAddon addon) {
        
        if (PacketEvents.getAPI() == null) {
            return false;
        }
        return true;
    }

    // init() : types en premier, avant toute syntaxe
    @Override
    public void init(SkriptAddon addon) {
    }

    // load() : sections, effets, conditions, expressions
    @Override
    public void load(SkriptAddon addon) {
        BodyYaw.register(addon);
        EntityId.register(addon);
        EntityTrackers.register(addon);
        FutureDirection.register(addon);
        GenerateId.register(addon);
        SplitRegex.register(addon);
        TrackedEntities.register(addon);

        SecretHide.register(addon);
        SecretReveal.register(addon);
        SecretDestroy.register(addon);

        loadVoteModule(addon);
    }

    @SuppressWarnings("null")
    private void loadVoteModule(SkriptAddon addon) {
        boolean hasVotifier = Bukkit.getPluginManager().getPlugin("Votifier") != null
                || Bukkit.getPluginManager().getPlugin("NuVotifier") != null;

        if (!hasVotifier) {
            SkWhy.getInstance().getLogger().info(
                "Votifier/NuVotifier not found — skipping vote event syntax."
            );
            return;
        }

        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onVotifierVote(com.vexsoftware.votifier.model.VotifierEvent event) {
                Bukkit.getPluginManager().callEvent(new Votes(event.getVote()));
            }
        }, SkWhy.getInstance());
        
        SyntaxRegistry syntaxRegistry = this.moduleRegistry(addon);
        syntaxRegistry.register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(SimpleEvent.class, "Vote Event")
                .addEvent(Votes.class)
                .addPatterns("[on] vote")
                .addDescription("Fires when a player votes for the server on a voting site.")
                .addExamples(
                    "on vote:",
                    "\tset {_username} to event-vote username",
                    "\tset {_service} to event-vote service",
                    "\tset {_address} to event-vote address",
                    "\tset {_timestamp} to event-vote timestamp"
                )
                .addSince("1.3.3")
                .build()
        );

        EventValueRegistry reg = addon.registry(EventValueRegistry.class);

        // event-string (username du voteur)
        reg.register(EventValue.builder(Votes.class, String.class)
            .patterns("vote username", "voter")
            .getter(Votes::getUsername)
            .time(EventValue.Time.NOW)
            .build());

        // event-string (nom du service de vote, ex: "minecraft-server-list.com")
        reg.register(EventValue.builder(Votes.class, String.class)
            .patterns("vote service", "voting service")
            .getter(Votes::getServiceName)
            .time(EventValue.Time.NOW)
            .build());

        // event-string (adresse IP du service)
        reg.register(EventValue.builder(Votes.class, String.class)
            .patterns("vote address", "voter address")
            .getter(Votes::getAddress)
            .time(EventValue.Time.NOW)
            .build());

        // event-string (timestamp du vote)
        reg.register(EventValue.builder(Votes.class, String.class)
            .patterns("vote timestamp", "vote time")
            .getter(Votes::getTimestamp)
            .time(EventValue.Time.NOW)
            .build());
    }
}
