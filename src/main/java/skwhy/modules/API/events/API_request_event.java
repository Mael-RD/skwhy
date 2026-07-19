package skwhy.modules.API.events;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import skwhy.modules.API.types.API_request;

@Name("API Request")
@Description("Fires when the built-in HTTP/HTTPS server receives a valid API request.")
@Examples({
    "on api request:",
    "\tset {_req} to event-apirequest",
    "\treply to {_req} with \"Hello!\""
})
@Since("1.3.0")
public class API_request_event extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final API_request request;

    public API_request_event(API_request request) {
        this.request = request;
    }

    public API_request getRequest() {
        return request;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}