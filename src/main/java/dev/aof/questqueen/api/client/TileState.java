package dev.aof.questqueen.api.client;

/** How a book-mode tile reads. Each draws with the theme's color for the matching quest state. */
public enum TileState {
    /** Not reachable yet: locked red rail and padlocked path arrows into it. */
    LOCKED,
    /** Can be taken now: the open-path blue (or the mode's accent). */
    AVAILABLE,
    /** Taken, partly or in progress: the current green. */
    ACTIVE,
    /** Finished or maxed: the completed gold. */
    DONE,
    /** Not shown: an empty cell. Its overlay hook still runs, so the mode can draw something there. */
    HIDDEN
}
