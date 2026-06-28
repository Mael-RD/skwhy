package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;
import ch.njol.skript.lang.util.SimpleEvent;

import skwhy.SkWhy;
import skwhy.request.APIserver;
import skwhy.modules.API.events.API_request_event;
import skwhy.modules.API.types.API_request;
import skwhy.modules.API.expressions.RequestContent;

public class APIModule implements AddonModule {

    private static APIserver apiServer;

    @Override
    public String name() {
        return "ApiModule";
    }

    @Override
    public boolean canLoad(SkriptAddon addon) {
        return true;
    }

    // init() : types en premier, avant toute syntaxe
    @Override
    public void init(SkriptAddon addon) {
        API_request.register();
    }

    // load() : sections, effets, conditions, expressions
    @Override
    public void load(SkriptAddon addon) {

        // ── 1. Enregistrement de l'event Skript ──
        SyntaxRegistry syntaxRegistry = this.moduleRegistry(addon);

        syntaxRegistry.register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(SimpleEvent.class, "API Request")
                .addEvent(API_request_event.class)
                .addPattern("api request")
                .addDescription("Fires when the built-in HTTP/HTTPS server receives a valid API request.")
                .addExamples(
                    "on api request:",
                    "\tset {_req} to event-apirequest",
                    "\treply to {_req} with \"Hello!\""
                )
                .addSince("1.3.0")
                .build()
        );

        // ── 2. Valeurs accessibles dans l'event ──
        EventValueRegistry valueRegistry = addon.registry(EventValueRegistry.class);

        // event-apirequest → l'objet API_request complet
        valueRegistry.register(EventValue.simple(
            API_request_event.class,
            API_request.class,
            API_request_event::getRequest
        ));

        // ── 3. Démarrage du serveur HTTP/HTTPS ──
        apiServer = new APIserver();
        if (!apiServer.start()) {
            SkWhy.getInstance().getLogger().severe("[ApiModule] Le serveur API n'a pas pu démarrer.");
        }

        RequestContent.register(addon);
    }

    /**
     * À appeler depuis onDisable() du plugin principal.
     */
    public static void shutdown() {
        if (apiServer != null) {
            apiServer.stop();
        }
    }
}