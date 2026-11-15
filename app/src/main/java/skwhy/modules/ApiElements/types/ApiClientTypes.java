package skwhy.modules.ApiElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import skwhy.apiclient.ApiCall;
import skwhy.apiclient.ApiResponse;

/** Types Skript du client HTTP : {@code apicall} (requête à envoyer) et {@code apiresponse} (réponse reçue). Aucun n'est écrivable dans un texte. */
public final class ApiClientTypes {

    private ApiClientTypes() {}

    public static void register() {
        Classes.registerClass(new ClassInfo<>(ApiCall.class, "apicall")
            .name("[API] API Call")
            .description(
                "An outgoing HTTP or HTTPS request that is built first and sent afterwards (method, url, headers, query parameters, body, authentication, timeout). " +
                "Create it with 'a new api call to \"url\"' or with the 'set {_call} to a new api call:' section, change it with the 'api ... of' expressions, " +
                "then send it with 'send api call' (see the section and the effect of the same name).",
                "Not to be confused with 'api request' (a request received by the built-in API server)."
            )
            .usage("Obtained with 'a new api call to %string%' or the 'new api call' section.")
            .user("api ?calls?")
            .examples(
                "set {_call} to a new api call to \"https://api.example.com/users\"",
                "set api header \"Accept\" of {_call} to \"application/json\""
            )
            .since("1.6.1")
            .parser(unparsable(call -> call.toString())));

        Classes.registerClass(new ClassInfo<>(ApiResponse.class, "apiresponse")
            .name("[API] API Response")
            .description(
                "The answer to a sent api call: status code, body, headers, duration. " +
                "An HTTP error (404, 500...) is a normal response with its own status code; " +
                "when no HTTP answer arrives (timeout, refused connection, invalid certificate...) the status code is 0 and 'api error of' tells why."
            )
            .usage("Obtained by 'send api call ... and store the response in ...' or by 'send api call ... and wait for the response in ...'.")
            .user("api ?responses?")
            .examples(
                "send api call {_call} and wait for the response in {_response}",
                "broadcast \"status: %api status of {_response}%\""
            )
            .since("1.6.1")
            .parser(unparsable(response -> response.toString())));
    }

    private static <T> Parser<T> unparsable(java.util.function.Function<T, String> text) {
        return new Parser<>() {
            @Override
            public T parse(String s, ParseContext context) {
                return null;
            }

            @Override
            public boolean canParse(ParseContext context) {
                return false;
            }

            @Override
            public String toString(T value, int flags) {
                return text.apply(value);
            }

            @Override
            public String toVariableNameString(T value) {
                return text.apply(value);
            }
        };
    }
}
