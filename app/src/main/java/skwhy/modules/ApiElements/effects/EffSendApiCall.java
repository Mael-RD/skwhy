package skwhy.modules.ApiElements.effects;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import skwhy.apiclient.ApiCall;
import skwhy.apiclient.ApiResponse;
import skwhy.modules.ApiElements.ApiClientService;

@Name("[API] Send API Call And Wait")
@Description("Sends an api call and pauses the rest of the code until the response arrives, like 'wait': the server is never blocked, only this script waits. " +
    "The response is put in the variable and the code continues with its local variables. A failed request (timeout, refused connection) or an HTTP error does not stop the script: " +
    "the response has the status 0 (or the HTTP code) and 'api error of' tells why. The optional timeout replaces the one of the call (by default api_client.default_timeout_seconds of the config). " +
    "Like 'wait', it does not delay the code that called a function: use it in a trigger, or use the section 'send api call ... and store the response in ...' that never waits.")
@Examples({
    "set {_call} to a new api call to \"https://api.example.com/status\"",
    "send api call {_call} and wait for the response in {_response}",
    "if api response {_response} is successful:",
    "\tbroadcast api body of {_response}",
    "send api call {_call} with timeout 2 seconds and wait for the response in {_response}",
    "if api error of {_response} is set:",
    "\tbroadcast \"no answer: %api error of {_response}%\""
})
@Since("1.6.1")
public class EffSendApiCall extends Effect {

    private Expression<ApiCall> callExpr;
    private @Nullable Expression<Timespan> timeoutExpr;
    private Expression<?> responseVar;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffSendApiCall.class)
                .addPattern("send api call %apicall% [with timeout %-timespan%] and wait for [the] response in %~objects%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        callExpr = (Expression<ApiCall>) exprs[0];
        timeoutExpr = (Expression<Timespan>) exprs[1];
        responseVar = exprs[2];
        getParser().setHasDelayBefore(Kleenean.TRUE);
        return true;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        ApiCall call = callExpr.getSingle(event);
        if (call == null) return getNext();
        Timespan timeout = timeoutExpr == null ? null : timeoutExpr.getSingle(event);
        debug(event, true);
        Delay.addDelayedEvent(event);
        Object locals = Variables.removeLocals(event);
        ApiClientService.send(call, timeout == null ? null : timeout.getAs(Timespan.TimePeriod.MILLISECOND), response -> {
            if (locals != null) Variables.setLocalVariables(event, locals);
            responseVar.change(event, new ApiResponse[]{ response }, ChangeMode.SET);
            TriggerItem next = getNext();
            if (next != null) TriggerItem.walk(next, event);
            Variables.removeLocals(event);
        });
        return null;
    }

    @Override
    protected void execute(Event event) {
        // inutilisé : walk() reprend le contrôle du déroulement
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "send api call " + callExpr.toString(event, debug) + " and wait for the response in " + responseVar.toString(event, debug);
    }
}
