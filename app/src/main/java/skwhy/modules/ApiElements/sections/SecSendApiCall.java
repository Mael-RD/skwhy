package skwhy.modules.ApiElements.sections;

import java.util.List;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import skwhy.apiclient.ApiCall;
import skwhy.apiclient.ApiResponse;
import skwhy.modules.ApiElements.ApiClientService;

@Name("[API] Send API Call (Without Waiting)")
@Description("Sends an api call and runs the section when the response arrives. The code after the section keeps running immediately (nothing waits, nothing blocks the server): " +
    "the request is sent in the background and the section runs later on the main thread, with the response in the variable and the local variables the code had when it was sent. " +
    "The section also runs when the request failed (timeout, refused connection, HTTP error): check 'api response ... is successful' or 'api status of'. " +
    "The optional timeout replaces the one of the call (by default api_client.default_timeout_seconds of the config). " +
    "To pause the code until the response, use the effect 'send api call ... and wait for the response in ...'.")
@Examples({
    "send api call {_call} and store the response in {_response}:",
    "\tif api response {_response} is successful:",
    "\t\tsend \"Answer: %api body of {_response}%\" to {_player}",
    "\telse:",
    "\t\tsend \"Error %api status of {_response}% %api error of {_response}%\" to {_player}",
    "send \"Request sent...\" to {_player}",
    "send api call {_call} with timeout 3 seconds and store the response in {_response}:",
    "\tbroadcast \"%api status of {_response}% in %api duration of {_response}%\""
})
@Since("1.6.1")
public class SecSendApiCall extends Section {

    private Expression<ApiCall> callExpr;
    private @Nullable Expression<Timespan> timeoutExpr;
    private Expression<?> responseVar;
    private Trigger trigger;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(SecSendApiCall.class)
                .addPattern("send api call %apicall% [with timeout %-timespan%] and store [the] response in %~objects%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult,
                        @Nullable SectionNode sectionNode, List<TriggerItem> triggerItems) {
        if (sectionNode == null) {
            Skript.error("'send api call ... and store the response in ...' needs a section: add ':' at the end of the line.");
            return false;
        }
        callExpr = (Expression<ApiCall>) exprs[0];
        timeoutExpr = (Expression<Timespan>) exprs[1];
        responseVar = exprs[2];
        // Le corps s'exécute plus tard, après un délai : le reste du script ne doit pas le supposer immédiat
        Kleenean before = getParser().getHasDelayBefore();
        getParser().setHasDelayBefore(Kleenean.TRUE);
        try {
            trigger = loadCode(sectionNode, "api response", getParser().getCurrentEvents());
        } finally {
            getParser().setHasDelayBefore(before);
        }
        return true;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        ApiCall call = callExpr.getSingle(event);
        if (call != null) {
            Timespan timeout = timeoutExpr == null ? null : timeoutExpr.getSingle(event);
            Object locals = Variables.copyLocalVariables(event);
            ApiClientService.send(call, timeout == null ? null : timeout.getAs(Timespan.TimePeriod.MILLISECOND), response -> {
                Variables.setLocalVariables(event, locals);
                responseVar.change(event, new ApiResponse[]{ response }, ChangeMode.SET);
                trigger.execute(event);
            });
        }
        return walk(event, false);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "send api call " + callExpr.toString(event, debug) + " and store the response in " + responseVar.toString(event, debug);
    }
}
