package skwhy.modules.ApiElements.conditions;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import skwhy.apiclient.ApiResponse;

@Name("[API] API Response Is Successful")
@Description("Checks that an HTTP answer was received with a status code from 200 to 299. False for any other code (404, 500...) and when no answer arrived (status 0: timeout, refused connection...).")
@Examples({
    "send api call {_call} and wait for the response in {_response}",
    "if api response {_response} is successful:",
    "\tbroadcast api body of {_response}",
    "else:",
    "\tbroadcast \"failed with status %api status of {_response}%\""
})
@Since("1.6.1")
public class CondApiSuccess extends Condition {

    private Expression<ApiResponse> responseExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondApiSuccess.class)
                .addPattern("api response %apiresponse% (is|was) successful")
                .addPattern("api response %apiresponse% (isn't|is not|wasn't|was not) successful")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        responseExpr = (Expression<ApiResponse>) exprs[0];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        return responseExpr.check(event, ApiResponse::isSuccessful, isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "api response " + responseExpr.toString(event, debug) + (isNegated() ? " is not successful" : " is successful");
    }
}
