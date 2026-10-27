package dev.aof.questqueen.api.client;

import dev.aof.questqueen.client.BookModes;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Quest Queen's client API: book modes and the book's theme. Client only; call from the render thread or from
 * {@code FMLClientSetupEvent}.
 *
 * <p>With at least one mode registered, the book's top bar shows a toggle ({@code QUESTS | SKILLS}); with none, the
 * book is exactly as it always was.
 */
public final class QuestQueenClientApi {
    /** Quest mode's id, for {@link #openBook}. */
    public static final String QUESTS = BookModes.QUESTS;

    private QuestQueenClientApi() {
    }

    /**
     * Add a mode to the book. Modes appear on the toggle after quests, in registration order.
     *
     * @throws IllegalArgumentException when the id is not namespaced or is {@link #QUESTS}
     * @throws IllegalStateException    when a mode with this id is already registered
     */
    public static void registerBookMode(BookMode mode) {
        BookModes.register(mode);
    }

    public static List<BookMode> bookModes() {
        return BookModes.all();
    }

    public static Optional<BookMode> bookMode(String id) {
        return BookModes.byId(id);
    }

    /**
     * Open the quest book on a mode ({@link #QUESTS} or a registered id), or switch the open book to it. The book
     * remembers the last mode shown and opens on it next time.
     */
    public static void openBook(String modeId) {
        BookModes.open(modeId);
    }

    /** The mode's data changed: the book asks it for its sidebar and grid again before the next frame. */
    public static void refresh(String modeId) {
        BookModes.refresh(modeId);
    }

    /** The book's colors now. */
    public static ThemeSnapshot theme() {
        return BookModes.theme();
    }

    /** Called with the new colors whenever the book binds a different theme (a chapter with another theme opened). */
    public static void addThemeListener(Consumer<ThemeSnapshot> listener) {
        BookModes.addThemeListener(listener);
    }

    public static void removeThemeListener(Consumer<ThemeSnapshot> listener) {
        BookModes.removeThemeListener(listener);
    }
}
