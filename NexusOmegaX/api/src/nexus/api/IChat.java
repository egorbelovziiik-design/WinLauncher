package nexus.api;

/** Chat access: sending messages and printing client-side feedback. */
public interface IChat {

    /** Sends a chat message or command to the server (no prefix handling). */
    void send(String text);

    /** Prints a Nexus-prefixed message to the local chat HUD only. */
    void print(String text);

    /** Prints a message with an ARGB colored prefix. */
    void print(String prefix, String text, int color);

    /** Current chat history (raw strings, newest last). */
    java.util.List<String> history();

    void clearHistory();

    /** Adds a suggestion used by command autocomplete in the vanilla chat box. */
    void addSuggestion(String suggestion);
}
