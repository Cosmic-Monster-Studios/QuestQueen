package dev.aof.questqueen.api.client;

import dev.aof.questqueen.client.BookPalette;

/**
 * The book's colors as they are now: the theme of the chapter the book shows (or last showed), with that chapter's
 * own background color applied. Read-only; colors are ARGB.
 */
public record ThemeSnapshot(String themeId, BookPalette palette) {
    /** Board background. */
    public int background() {
        return palette.voidColor();
    }

    /** Card and panel faces. */
    public int card() {
        return palette.card();
    }

    public int text() {
        return palette.text();
    }

    public int muted() {
        return palette.muted();
    }

    public int sidebar() {
        return palette.sidebar();
    }

    /** Hairline frames and rules. */
    public int edge() {
        return palette.sidebarEdge();
    }

    /** The open-path / AVAILABLE color. */
    public int open() {
        return palette.neu();
    }

    /** The current / ACTIVE color. */
    public int current() {
        return palette.current();
    }

    /** The completed / DONE color. */
    public int completed() {
        return palette.completed();
    }

    /** The locked color, always {@code #8C1A24}. */
    public int locked() {
        return palette.lockedEdge();
    }

    public int gateOr() {
        return palette.gateOr();
    }

    public int gateXor() {
        return palette.gateXor();
    }
}
