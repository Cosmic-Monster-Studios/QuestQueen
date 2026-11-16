package dev.aof.questqueen.client;

import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.api.client.BookMode;
import dev.aof.questqueen.api.client.ThemeSnapshot;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The book modes addons registered, which one the book showed last, and the theme listeners. Internal: addons use
 * {@code QuestQueenClientApi}. With no mode registered the book has no toggle and nothing here is consulted.
 */
public final class BookModes {
    /** Quest mode's id on the toggle and in {@code openBook}. */
    public static final String QUESTS = "questqueen:quests";

    private static final List<BookMode> MODES = new CopyOnWriteArrayList<>();
    private static final Map<String, Integer> REVISIONS = new ConcurrentHashMap<>();
    private static final List<Consumer<ThemeSnapshot>> THEME_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile String lastMode = QUESTS;
    private static volatile ThemeSnapshot theme;

    private BookModes() {
    }

    public static void register(BookMode mode) {
        if (mode == null || mode.id() == null || mode.id().indexOf(':') <= 0) {
            throw new IllegalArgumentException("A Quest Queen book mode needs a namespaced id, like 'mymod:skills'");
        }
        if (QUESTS.equals(mode.id())) {
            throw new IllegalArgumentException("'" + QUESTS + "' is quest mode's own id");
        }
        synchronized (MODES) {
            if (byId(mode.id()).isPresent()) {
                throw new IllegalStateException("Quest Queen book mode '" + mode.id() + "' is already registered");
            }
            MODES.add(mode);
        }
    }

    /** Tests only: no modes, quest mode remembered, no theme seen yet. */
    static void clearForTests() {
        MODES.clear();
        REVISIONS.clear();
        lastMode = QUESTS;
        theme = null;
    }

    public static List<BookMode> all() {
        return List.copyOf(MODES);
    }

    /** True once any addon registered a mode: the book then shows its mode toggle. */
    public static boolean any() {
        return !MODES.isEmpty();
    }

    public static Optional<BookMode> byId(String id) {
        for (BookMode mode : MODES) {
            if (mode.id().equals(id)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /** The mode the book opens on: the last one shown, while it is still registered. */
    static String lastMode() {
        String id = lastMode;
        return QUESTS.equals(id) || byId(id).isPresent() ? id : QUESTS;
    }

    static void rememberMode(String id) {
        lastMode = id == null || id.isBlank() ? QUESTS : id;
    }

    /** Bumped by {@link #refresh}; the book re-reads a mode's sidebar and grid when it changes. */
    static int revision(String id) {
        return REVISIONS.getOrDefault(id, 0);
    }

    public static void refresh(String id) {
        REVISIONS.merge(id, 1, Integer::sum);
    }

    /**
     * Open the book on {@code modeId} ({@link #QUESTS} or a registered mode), or switch the open book to it. An id
     * that is not registered opens quest mode.
     */
    public static void open(String modeId) {
        String id = QUESTS.equals(modeId) || byId(modeId).isPresent() ? modeId : QUESTS;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestBookScreen book) {
            book.switchMode(id);
            return;
        }
        rememberMode(id);
        minecraft.setScreen(new QuestBookScreen());
    }

    public static ThemeSnapshot theme() {
        ThemeSnapshot now = theme;
        if (now != null) {
            return now;
        }
        var chapter = ClientQuestState.rememberedChapter().orElse(null);
        String id = chapter == null ? BookTheme.MIDNIGHT_ROYALTY.id() : BookTheme.byId(chapter.theme()).id();
        return new ThemeSnapshot(id, BookPalette.resolveOrDefault(chapter));
    }

    public static void addThemeListener(Consumer<ThemeSnapshot> listener) {
        THEME_LISTENERS.add(listener);
    }

    public static void removeThemeListener(Consumer<ThemeSnapshot> listener) {
        THEME_LISTENERS.remove(listener);
    }

    /** The book bound a palette; tell listeners when it differs from the last one. Cheap when nothing changed. */
    static void noteTheme(String themeId, BookPalette palette) {
        ThemeSnapshot last = theme;
        if (last != null && last.themeId().equals(themeId) && last.palette().equals(palette)) {
            return;
        }
        ThemeSnapshot next = new ThemeSnapshot(themeId, palette);
        theme = next;
        if (last == null) {
            return;
        }
        for (Consumer<ThemeSnapshot> listener : THEME_LISTENERS) {
            try {
                listener.accept(next);
            } catch (RuntimeException exception) {
                QuestQueen.LOGGER.error("A Quest Queen theme listener failed", exception);
            }
        }
    }
}
