package dev.aof.questqueen.api.client;

/** A path arrow from one tile to another, drawn on the side of {@code from} that faces {@code to}. */
public record ModeLink(String from, String to, LinkGate gate) {
    public ModeLink {
        gate = gate == null ? LinkGate.AND : gate;
    }

    public static ModeLink and(String from, String to) {
        return new ModeLink(from, to, LinkGate.AND);
    }
}
