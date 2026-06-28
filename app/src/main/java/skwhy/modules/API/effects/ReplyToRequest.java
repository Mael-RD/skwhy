package skwhy.modules.API.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.modules.API.types.API_request;

@Name("[API] Reply To Request")
@Description("Sends an HTTP response to an API request. Optionally specify a status code (default 200).")
@Examples({
    "on api request:",
    "\treply to event-apirequest with \"hello\"",
    "",
    "on api request:",
    "\treply to event-apirequest with code 404 and \"not found\"",
    "",
    "on api request:",
    "\tset {_body} to \"{\\\"ok\\\": true}\"",
    "\treply to event-apirequest with {_body}"
})
@Since("1.3.0")
public class ReplyToRequest extends Effect {

    private Expression<API_request> requestExpr;
    private Expression<Number>      codeExpr;
    private Expression<String>      bodyExpr;
    private boolean hasCode;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        hasCode = (matchedPattern == 1);
        if (hasCode) {
            requestExpr = (Expression<API_request>) exprs[0];
            codeExpr    = (Expression<Number>)      exprs[1];
            bodyExpr    = (Expression<String>)      exprs[2];
        } else {
            requestExpr = (Expression<API_request>) exprs[0];
            bodyExpr    = (Expression<String>)      exprs[1];
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        API_request req = requestExpr.getSingle(event);
        if (req == null) return;

        String body = bodyExpr.getSingle(event);
        if (body == null) body = "";

        if (hasCode && codeExpr != null) {
            Number code = codeExpr.getSingle(event);
            req.reply(code != null ? code.intValue() : 200, body);
        } else {
            req.reply(body);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (hasCode) {
            return "reply to " + requestExpr.toString(event, debug)
                + " with code " + codeExpr.toString(event, debug)
                + " and " + bodyExpr.toString(event, debug);
        }
        return "reply to " + requestExpr.toString(event, debug)
            + " with " + bodyExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(ReplyToRequest.class)
                // Pattern 0 : sans code
                .addPattern("reply to %apirequest% with %string%")
                // Pattern 1 : avec code HTTP explicite
                .addPattern("reply to %apirequest% with code %number% and %string%")
                .build()
        );
    }
}