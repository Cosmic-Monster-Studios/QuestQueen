package dev.aof.questqueen.api.client;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * A second page of the quest book, beside quests: its own sidebar entries and tile grids, drawn by Quest Queen with
 * the book's own frame, sidebar, pan and zoom, tiles, path arrows and inspect card. The mode supplies the data, the
 * inspect card's body and any overlays.
 *
 * <p>Register one with {@link QuestQueenClientApi#registerBookMode}. Every method runs on the render thread. Quest
 * Queen asks for {@link #sidebar()} and {@link #grid(String)} again only after {@link QuestQueenClientApi#refresh}, so
 * call that when the data changes. Overlay hooks run every frame.
 */
public interface BookMode {
    /** Namespaced, like {@code mymod:skills}. */
    String id();

    /** The mode's name on the top bar toggle. */
    Component label();

    /** Drawn before the label on the toggle; none by default. */
    default BookIcon icon() {
        return BookIcon.NONE;
    }

    /**
     * The mode's accent for this theme (ARGB), used where quest mode uses the open-path blue and current green:
     * AVAILABLE and ACTIVE tiles, open path arrows, the toggle and the sidebar marker. 0 keeps the theme's colors.
     * Locked stays locked red whatever the accent.
     */
    default int accent(ThemeSnapshot theme) {
        return 0;
    }

    /** The sidebar rows, top to bottom, in place of chapters. */
    List<SidebarEntry> sidebar();

    /** The board for one sidebar entry. */
    ModeGrid grid(String entryId);

    /**
     * A click on a tile ({@code button} 0 left, 1 right). Return true to take it; otherwise a left click selects the
     * tile and opens its inspect card.
     */
    default boolean onTileClick(String entryId, ModeTile tile, int button) {
        return false;
    }

    /** Hover lines for a tile; none by default. */
    default List<Component> tileTooltip(String entryId, ModeTile tile) {
        return List.of();
    }

    /** Height of the inspect card's body for this tile, at {@code width}. */
    default int inspectHeight(String entryId, ModeTile tile, int width) {
        return 72;
    }

    /** Draw the inspect card's body. */
    default void renderInspect(InspectContext context) {
    }

    /**
     * A click inside the inspect card's body, {@code localX}/{@code localY} from its top-left. Return true when it
     * did something.
     */
    default boolean onInspectClick(String entryId, ModeTile tile, int localX, int localY, int button) {
        return false;
    }

    /** Draw over one tile after Quest Queen has drawn it (HIDDEN tiles included). */
    default void renderTileOverlay(TileOverlayContext context) {
    }

    /** Draw along one link after all tiles are drawn. */
    default void renderLinkOverlay(LinkOverlayContext context) {
    }

    /** Width of the mode's slot in the top bar (0 for none), e.g. for a points counter. */
    default int topBarExtrasWidth() {
        return 0;
    }

    default void renderTopBarExtras(TopBarContext context) {
    }

    /** A click in the top bar slot, {@code localX}/{@code localY} from its top-left. */
    default boolean onTopBarClick(int localX, int localY, int button) {
        return false;
    }

    /** The search box hint while this mode shows. */
    default Component searchHint() {
        return Component.literal("Search " + label().getString().toLowerCase(Locale.ROOT));
    }

    /** Whether a tile matches the search text (already lowercased and trimmed); the label by default. */
    default boolean matchesSearch(String entryId, ModeTile tile, String query) {
        return tile.label().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    /** The book switched to this mode. */
    default void onShown() {
    }

    /** The book switched away from this mode, or closed while showing it. */
    default void onHidden() {
    }
}
