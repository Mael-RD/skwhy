package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;
import ch.njol.skript.lang.util.SimpleEvent;

import skwhy.SkWhy;
import skwhy.modules.ApiElements.ApiClientService;
import skwhy.modules.ApiElements.conditions.CondApiSuccess;
import skwhy.modules.ApiElements.effects.EffSendApiCall;
import skwhy.modules.ApiElements.effects.ReplyToRequest;
import skwhy.modules.ApiElements.expressions.ExprApiHeaderNames;
import skwhy.modules.ApiElements.expressions.ExprApiKeyed;
import skwhy.modules.ApiElements.expressions.ExprApiStatus;
import skwhy.modules.ApiElements.expressions.ExprApiTime;
import skwhy.modules.ApiElements.expressions.ExprNewApiCall;
import skwhy.modules.ApiElements.sections.SecNewApiCall;
import skwhy.modules.ApiElements.sections.SecSendApiCall;
import skwhy.modules.ApiElements.types.ApiClientTypes;
import skwhy.modules.ApiElements.events.API_request_event;
import skwhy.modules.ApiElements.expressions.RequestContent;
import skwhy.modules.ApiElements.types.API_request;
import skwhy.request.APIserver;

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
        ApiClientTypes.register();
    }

    // load() : sections, effets, conditions, expressions
    @Override
    @SuppressWarnings("null")
    public void load(SkriptAddon addon) {

        // ── 1. Enregistrement de l'event Skript ──
        SyntaxRegistry syntaxRegistry = this.moduleRegistry(addon);

        syntaxRegistry.register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(SimpleEvent.class, "[API] API Request")
                .addEvent(API_request_event.class)
                .addPattern("api request")
                .addDescription(
                    "Fires when the built-in HTTP/HTTPS server receives a valid API request.",
                    "Event value: 'event-apirequest' (the request, which can be answered with the 'reply to' effect)."
                )
                .addExamples(
                    "on api request:",
                    "\tset {_req} to event-apirequest",
                    "\treply to {_req} with \"Hello!\""
                )
                .addKeywords("api", "http", "request")
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
        ReplyToRequest.register(addon);

        // ── 4. Client HTTP (requêtes sortantes, since 1.6.1) ──
        ApiClientService.start();
        ExprNewApiCall.register(addon);
        ExprApiHeaderNames.register(addon); // avant ExprApiKeyed : « api header names of » ne doit pas être lu comme « api header %string% of »
        ExprApiKeyed.register(addon);
        ExprApiStatus.register(addon);
        ExprApiTime.register(addon);
        CondApiSuccess.register(addon);
        SecNewApiCall.register(addon);
        SecSendApiCall.register(addon);
        EffSendApiCall.register(addon);
    }

    /**
     * À appeler depuis onDisable() du plugin principal.
     */
    public static void shutdown() {
        ApiClientService.stop();
        if (apiServer != null) {
            apiServer.stop();
        }
    }

    /** Arrête l'API REST (le serveur HTTP ne se ferme que si l'éditeur ne l'utilise plus non plus). */
    public static void stopHttp() {
        if (apiServer != null) apiServer.stop();
    }

    /** Redémarre l'API REST avec la config courante ; sans effet si le module API est désactivé. */
    public static boolean startHttp() {
        return apiServer != null && apiServer.start();
    }

    public static boolean isHttpRunning() {
        return apiServer != null && apiServer.isStarted();
    }
}