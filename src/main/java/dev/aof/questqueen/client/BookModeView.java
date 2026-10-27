package dev.aof.questqueen.client;

import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.api.client.BookIcon;
import dev.aof.questqueen.api.client.BookMode;
import dev.aof.questqueen.api.client.InspectContext;
import dev.aof.questqueen.api.client.LinkGate;
import dev.aof.questqueen.api.client.LinkOverlayContext;
import dev.aof.questqueen.api.client.ModeGrid;
import dev.aof.questqueen.api.client.ModeLink;
import dev.aof.questqueen.api.client.ModeTile;
import dev.aof.questqueen.api.client.SidebarEntry;
import dev.aof.questqueen.api.client.ThemeSnapshot;
import dev.aof.questqueen.api.client.TileOverlayContext;
import dev.aof.questqueen.api.client.TileState;
import dev.aof.questqueen.api.client.TopBarContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The book's addon modes: the top bar toggle, the switch animation, and a registered {@link BookMode} drawn with the
 * book's own chrome. Quest mode keeps its own code; this only takes over while another mode shows, and with no mode
 * registered every entry point returns at once without drawing anything.
 *
 * <p>A mode's tiles are {@link QuestTileWidget}s, the same widget quest tiles use, and its links are the same edge
 * ports and path arrows, so a mode reads as part of the book.
 */
final class BookModeView {
    static final long SWITCH_MS = 300L;
    private static final int SWITCH_SLIDE = 48;
    private static final int TOGGLE_H = 14;
    private static final int TOGGLE_PAD = 10;
    private static final int SEARCH_DIM = 0xA6000000;
    private static final int[][] NO_PORTS = new int[0][];

    /** Per mode: the sidebar entry last open. Per mode and entry: the camera (x, y, zoom). */
    private static final Map<String, String> LAST_ENTRY = new HashMap<>();
    private static final Map<String, double[]> CAMERAS = new HashMap<>();

    private final QuestBookScreen book;
    private String modeId;
    private BookMode mode;
    private int seenRevision = Integer.MIN_VALUE;
    private List<SidebarEntry> entries = List.of();
    private String entryId = "";
    private ModeGrid grid = ModeGrid.EMPTY;
    private final Map<String, QuestTileWidget> widgets = new HashMap<>();
    private double camX;
    private double camY;
    private float zoom = 1f;
    private String selectedId = "";
    private boolean inspectOpen;
    private int sidebarScroll;
    private String query = "";
    private boolean dragging;
    private double lastMx;
    private double lastMy;
    private String hoverTileId = "";
    /** A click on a locked sidebar entry shakes its padlock. */
    private String lockedCueId = "";
    private long lockedCueAt = Long.MIN_VALUE;
    private static final long LOCKED_CUE_MS = 400L;

    private long switchAt = Long.MIN_VALUE;
    private int switchDir = 1;
    private int fromVoid;
    private int fromSidebar;
    private int fromToggleX;
    private int fromToggleW;

    BookModeView(QuestBookScreen book) {
        this.book = book;
        String last = BookModes.lastMode();
        this.modeId = last;
        this.mode = BookModes.byId(last).orElse(null);
        if (mode != null) {
            mode.onShown();
        }
    }

    /** True while a mode other than quests shows. */
    boolean active() {
        return mode != null;
    }

    String modeId() {
        return modeId;
    }

    // ---- switching ---------------------------------------------------------------------------------------------

    void switchMode(String id) {
        String next = BookModes.QUESTS.equals(id) || BookModes.byId(id).isPresent() ? id : BookModes.QUESTS;
        if (next.equals(modeId)) {
            return;
        }
        List<String> order = toggleIds();
        int fromIndex = order.indexOf(modeId);
        int toIndex = order.indexOf(next);
        switchDir = toIndex >= fromIndex ? 1 : -1;
        fromVoid = QuestColors.VOID;
        fromSidebar = QuestColors.SIDEBAR;
        int[] seg = segment(fromIndex);
        fromToggleX = seg[0];
        fromToggleW = seg[1];
        switchAt = UiFx.enabled() ? UiFx.nowMs() : Long.MIN_VALUE;
        if (mode != null) {
            saveCamera();
            mode.onHidden();
        }
        modeId = next;
        mode = BookModes.byId(next).orElse(null);
        BookModes.rememberMode(next);
        seenRevision = Integer.MIN_VALUE;
        selectedId = "";
        inspectOpen = false;
        query = "";
        dragging = false;
        if (mode != null) {
            mode.onShown();
        }
        book.onModeSwitched();
    }

    /** The book came back from a screen it opened (a recipe viewer, say). */
    void onReturned() {
        if (mode != null) {
            mode.onShown();
        }
    }

    void onRemoved() {
        if (mode != null) {
            saveCamera();
            mode.onHidden();
        }
        BookModes.rememberMode(modeId);
    }

    /** Board offset of the switch slide, 0 when no switch is running. */
    int slidePx() {
        if (switchAt == Long.MIN_VALUE) {
            return 0;
        }
        float t = UiFx.progress(switchAt, SWITCH_MS);
        if (t >= 1f) {
            switchAt = Long.MIN_VALUE;
            return 0;
        }
        return Math.round((1f - UiFx.easeOut(t)) * SWITCH_SLIDE * switchDir);
    }

    private float switchProgress() {
        return switchAt == Long.MIN_VALUE ? 1f : UiFx.easeOut(UiFx.progress(switchAt, SWITCH_MS));
    }

    /**
     * The outgoing palette fading off the new one: the old background over the board, the old sidebar over the
     * sidebar and top bar. Drawn last, in either mode.
     */
    void drawSwitchFade(GuiGraphics graphics) {
        float t = switchProgress();
        if (t >= 1f) {
            return;
        }
        float a = 1f - t;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 700);
        int left = book.boardLeft();
        MockChrome.box(graphics, left, QuestBookScreen.TOP_H, book.width - left, book.height - QuestBookScreen.TOP_H,
                UiFx.withAlpha(fromVoid, 0.85f * a));
        MockChrome.box(graphics, 0, QuestBookScreen.TOP_H, left, book.height - QuestBookScreen.TOP_H,
                UiFx.withAlpha(fromSidebar, 0.6f * a));
        pose.popPose();
    }

    // ---- toggle ------------------------------------------------------------------------------------------------

    private List<String> toggleIds() {
        List<String> ids = new ArrayList<>();
        ids.add(BookModes.QUESTS);
        for (BookMode each : BookModes.all()) {
            ids.add(each.id());
        }
        return ids;
    }

    /** Quest mode's segment shows this glyph when the toggle is compact. */
    private static final String QUESTS_GLYPH = "book";
    /** The search box keeps at least this much before the toggle goes compact. */
    private static final int SEARCH_ROOM = 96;

    /** The segment's label: the mode's name, or in a compact toggle nothing (it has an icon) or its initial. */
    private static String toggleLabel(String id, boolean compact) {
        String name = BookModes.QUESTS.equals(id) ? "QUESTS"
                : BookModes.byId(id).map(m -> m.label().getString().toUpperCase(Locale.ROOT)).orElse(id);
        if (!compact) {
            return name;
        }
        return segmentIcon(id, true).isEmpty() ? name.substring(0, Math.min(1, name.length())) : "";
    }

    private static BookIcon toggleIcon(String id) {
        if (BookModes.QUESTS.equals(id)) {
            return BookIcon.NONE;
        }
        return BookModes.byId(id).map(BookMode::icon).orElse(BookIcon.NONE);
    }

    private static BookIcon segmentIcon(String id, boolean compact) {
        return compact && BookModes.QUESTS.equals(id) ? BookIcon.glyph(QUESTS_GLYPH) : toggleIcon(id);
    }

    private static int segmentW(String id, boolean compact) {
        String label = toggleLabel(id, compact);
        boolean icon = !segmentIcon(id, compact).isEmpty();
        int w = font().width(label) + TOGGLE_PAD + (icon ? 10 : 0);
        return icon && label.isEmpty() ? w - 2 : w;
    }

    private int fullToggleW() {
        int w = 0;
        for (String id : toggleIds()) {
            w += segmentW(id, false);
        }
        return w + 1;
    }

    /** Short of room (a narrow window), the toggle shows icons and initials so the search box keeps its room. */
    private boolean compact() {
        int right = mode != null ? fitX() - 8 - (extrasW() > 0 ? extrasW() + 6 : 0) : book.searchRight();
        return right - book.searchX() - fullToggleW() - 8 < SEARCH_ROOM;
    }

    /** Width of the toggle; 0 with no mode registered. */
    int toggleW() {
        if (!BookModes.any()) {
            return 0;
        }
        boolean compact = compact();
        int w = 0;
        for (String id : toggleIds()) {
            w += segmentW(id, compact);
        }
        return w + 1;
    }

    /** Room the toggle takes from the search box in quest mode; 0 with no mode registered. */
    int questReserve() {
        int w = toggleW();
        return w == 0 ? 0 : w + 8;
    }

    private int toggleX() {
        return book.searchX() + book.search.getWidth() + 8;
    }

    private int[] segment(int index) {
        List<String> ids = toggleIds();
        boolean compact = compact();
        int x = toggleX();
        for (int i = 0; i < ids.size(); i++) {
            int w = segmentW(ids.get(i), compact);
            if (i == index) {
                return new int[]{x, w};
            }
            x += w;
        }
        return new int[]{x, 0};
    }

    /** The toggle in the top bar. Nothing with no mode registered. */
    void drawToggle(GuiGraphics graphics) {
        if (!BookModes.any() || book.logOpen) {
            return;
        }
        Font font = font();
        List<String> ids = toggleIds();
        int x0 = toggleX();
        int y = 6;
        int w = toggleW();
        MockChrome.box(graphics, x0, y, w, TOGGLE_H, QuestColors.CARD);
        int active = ids.indexOf(modeId);
        int[] seg = segment(active);
        float t = switchProgress();
        int hx = t < 1f ? UiFx.lerpInt(fromToggleX, seg[0], t) : seg[0];
        int hw = t < 1f ? UiFx.lerpInt(fromToggleW, seg[1], t) : seg[1];
        MockChrome.box(graphics, hx, y, hw, TOGGLE_H, QuestColors.SIDEBAR_ACTIVE);
        MockChrome.box(graphics, hx + 1, y + TOGGLE_H - 2, Math.max(1, hw - 2), 1, accentFor(modeId));
        MockChrome.frame(graphics, x0, y, w, TOGGLE_H, QuestColors.SIDEBAR_EDGE);
        boolean compact = compact();
        int x = x0;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int sw = segmentW(id, compact);
            if (i > 0) {
                MockChrome.box(graphics, x, y + 3, 1, TOGGLE_H - 6, QuestColors.SIDEBAR_EDGE);
            }
            int ink = i == active ? QuestColors.TEXT : QuestColors.SIDEBAR_TEXT;
            int tx = x + TOGGLE_PAD / 2;
            BookIcon icon = segmentIcon(id, compact);
            if (!icon.isEmpty()) {
                drawIcon(graphics, icon, tx, y + 3, 8, ink);
                tx += 10;
            }
            graphics.drawString(font, toggleLabel(id, compact), tx, y + 3, ink, false);
            x += sw;
        }
    }

    /** A click on the toggle switches mode. Also routes every click while a mode shows. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!BookModes.any()) {
            return false;
        }
        if (!book.logOpen && button == 0 && QuestBookScreen.over(toggleX(), 6, toggleW(), TOGGLE_H, mouseX, mouseY)) {
            List<String> ids = toggleIds();
            boolean compact = compact();
            int x = toggleX();
            for (String id : ids) {
                int w = segmentW(id, compact);
                if (mouseX >= x && mouseX < x + w) {
                    switchMode(id);
                    return true;
                }
                x += w;
            }
            return true;
        }
        return mode != null && modeClicked(mouseX, mouseY, button);
    }

    private int accentFor(String id) {
        if (BookModes.QUESTS.equals(id)) {
            return QuestColors.NEW;
        }
        ThemeSnapshot theme = BookModes.theme();
        int accent = BookModes.byId(id).map(m -> m.accent(theme)).orElse(0);
        return accent == 0 ? theme.open() : accent;
    }

    // ---- model -------------------------------------------------------------------------------------------------

    private void refreshModel() {
        int revision = BookModes.revision(modeId);
        if (revision == seenRevision) {
            return;
        }
        boolean first = seenRevision == Integer.MIN_VALUE;
        seenRevision = revision;
        try {
            entries = List.copyOf(mode.sidebar());
        } catch (RuntimeException exception) {
            QuestQueen.LOGGER.error("Book mode {} failed to list its sidebar", modeId, exception);
            entries = List.of();
        }
        String want = first ? LAST_ENTRY.getOrDefault(modeId, entryId) : entryId;
        if (entry(want).filter(e -> !e.hidden() && !e.locked()).isEmpty()) {
            want = entries.stream().filter(e -> !e.hidden() && !e.locked()).map(SidebarEntry::id).findFirst().orElse("");
        }
        boolean changed = !want.equals(entryId);
        entryId = want;
        loadGrid();
        if (first || changed) {
            restoreCamera();
        }
    }

    private void loadGrid() {
        try {
            grid = entryId.isEmpty() ? ModeGrid.EMPTY : mode.grid(entryId);
            if (grid == null) {
                grid = ModeGrid.EMPTY;
            }
        } catch (RuntimeException exception) {
            QuestQueen.LOGGER.error("Book mode {} failed to build the board for {}", modeId, entryId, exception);
            grid = ModeGrid.EMPTY;
        }
        Set<String> keep = new HashSet<>();
        for (ModeTile tile : grid.tiles()) {
            keep.add(tile.id());
        }
        widgets.keySet().retainAll(keep);
        if (grid.tile(selectedId).filter(t -> t.state() != TileState.HIDDEN).isEmpty()) {
            selectedId = "";
            inspectOpen = false;
        }
    }

    private Optional<SidebarEntry> entry(String id) {
        for (SidebarEntry entry : entries) {
            if (entry.id().equals(id)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private void openEntry(String id) {
        if (id.equals(entryId)) {
            return;
        }
        saveCamera();
        entryId = id;
        LAST_ENTRY.put(modeId, id);
        selectedId = "";
        inspectOpen = false;
        widgets.clear();
        loadGrid();
        restoreCamera();
    }

    private String cameraKey() {
        return modeId + "|" + entryId;
    }

    private void saveCamera() {
        if (!entryId.isEmpty()) {
            CAMERAS.put(cameraKey(), new double[]{camX, camY, zoom});
            LAST_ENTRY.put(modeId, entryId);
        }
    }

    private void restoreCamera() {
        double[] saved = CAMERAS.get(cameraKey());
        if (saved != null) {
            camX = saved[0];
            camY = saved[1];
            zoom = (float) saved[2];
        } else {
            fit();
        }
    }

    // ---- geometry ----------------------------------------------------------------------------------------------

    private int tilePx() {
        int raw = Math.round(QuestBookScreen.TILE * zoom);
        int chosen = QuestBookScreen.TILE_STEPS[0];
        int best = Integer.MAX_VALUE;
        for (int step : QuestBookScreen.TILE_STEPS) {
            int d = Math.abs(step - raw);
            if (d < best) {
                best = d;
                chosen = step;
            }
        }
        return chosen;
    }

    private int stridePx() {
        int size = tilePx();
        return size + QuestBookScreen.GAP * size / QuestBookScreen.TILE;
    }

    private int contentW() {
        return Math.max(1, book.width - book.boardLeft());
    }

    private int contentH() {
        return Math.max(1, book.height - QuestBookScreen.TOP_H);
    }

    private int sx(int col) {
        return book.boardLeft() + col * stridePx() - (int) Math.round(camX * zoom) + slidePx();
    }

    private int sy(int row) {
        return row * stridePx() - (int) Math.round(camY * zoom);
    }

    /** The whole board in view at the largest zoom rung that holds it, centered. */
    void fit() {
        List<ModeTile> shown = grid.tiles().stream().filter(t -> t.state() != TileState.HIDDEN).toList();
        if (shown.isEmpty()) {
            zoom = 1f;
            camX = -QuestBookScreen.STRIDE;
            camY = -QuestBookScreen.STRIDE - QuestBookScreen.TOP_H;
            return;
        }
        int minC = Integer.MAX_VALUE;
        int minR = Integer.MAX_VALUE;
        int maxC = Integer.MIN_VALUE;
        int maxR = Integer.MIN_VALUE;
        for (ModeTile tile : shown) {
            minC = Math.min(minC, tile.col());
            minR = Math.min(minR, tile.row());
            maxC = Math.max(maxC, tile.col());
            maxR = Math.max(maxR, tile.row());
        }
        int cols = maxC - minC + 1;
        int rows = maxR - minR + 1;
        int chosen = QuestBookScreen.TILE_STEPS[0];
        for (int step : QuestBookScreen.TILE_STEPS) {
            if (step > QuestBookScreen.TILE) {
                break;
            }
            int stride = step + QuestBookScreen.GAP * step / QuestBookScreen.TILE;
            if (cols * stride + 24 <= contentW() && rows * stride + 24 <= contentH()) {
                chosen = step;
            }
        }
        zoom = chosen / (float) QuestBookScreen.TILE;
        double cx = (minC * QuestBookScreen.STRIDE + maxC * QuestBookScreen.STRIDE + QuestBookScreen.TILE) / 2.0;
        double cy = (minR * QuestBookScreen.STRIDE + maxR * QuestBookScreen.STRIDE + QuestBookScreen.TILE) / 2.0;
        camX = cx - contentW() / (2.0 * zoom);
        camY = cy - (QuestBookScreen.TOP_H + contentH() / 2.0) / zoom;
    }

    private void stepZoom(int dir, double anchorX, double anchorY) {
        int px = tilePx();
        int[] steps = QuestBookScreen.TILE_STEPS;
        int idx = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == px) {
                idx = i;
            }
        }
        int next = Math.max(0, Math.min(steps.length - 1, idx + dir));
        if (next == idx) {
            return;
        }
        float before = zoom;
        zoom = steps[next] / (float) QuestBookScreen.TILE;
        double ax = Math.max(0, Math.min(contentW(), anchorX - book.boardLeft()));
        double ay = Math.max(QuestBookScreen.TOP_H, Math.min(book.height, anchorY));
        camX = QuestBookScreen.anchoredCamera(camX, ax, before, zoom);
        camY = QuestBookScreen.anchoredCamera(camY, ay, before, zoom);
    }

    // ---- colors ------------------------------------------------------------------------------------------------

    /** Bind the book theme with the mode's accent. QuestColors is per frame, so quest mode rebinds its own. */
    private ThemeSnapshot bindTheme() {
        book.bindChapterTheme();
        ThemeSnapshot theme = BookModes.theme();
        int accent = 0;
        try {
            accent = mode.accent(theme);
        } catch (RuntimeException ignored) {
            // A broken accent keeps the theme's colors.
        }
        if (accent != 0) {
            QuestColors.apply(theme.palette().withAccent(accent));
        }
        return theme;
    }

    static int stateColor(TileState state) {
        return switch (state) {
            case LOCKED, HIDDEN -> QuestColors.LOCKED_EDGE;
            case AVAILABLE -> QuestColors.NEW;
            case ACTIVE -> QuestColors.CURRENT;
            case DONE -> QuestColors.COMPLETED;
        };
    }

    static String stateWord(TileState state) {
        return switch (state) {
            case LOCKED, HIDDEN -> "LOCKED";
            case AVAILABLE -> "AVAILABLE";
            case ACTIVE -> "ACTIVE";
            case DONE -> "DONE";
        };
    }

    // ---- drawing -----------------------------------------------------------------------------------------------

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            refreshModel();
            ThemeSnapshot theme = bindTheme();
            book.layoutSearchNow();
            graphics.fill(0, 0, book.width, book.height, QuestColors.VOID);
            int size = tilePx();
            hoverTileId = "";
            drawBoard(graphics, theme, size, mouseX, mouseY, partialTick);
            drawSidebar(graphics, mouseX, mouseY);
            if (inspectOpen) {
                grid.tile(selectedId).ifPresent(tile -> drawInspect(graphics, theme, tile, mouseX, mouseY, partialTick));
            }
            drawTopBar(graphics, theme, mouseX, mouseY, partialTick);
            drawTooltip(graphics, mouseX, mouseY);
        } catch (Throwable t) {
            QuestQueen.LOGGER.error("Book mode {} render failed", modeId, t);
            graphics.drawString(font(), "BOOK MODE RENDER ERROR - see log", book.boardLeft() + 8, 40, 0xFFFF5555, true);
        }
        drawSwitchFade(graphics);
    }

    private void drawBoard(GuiGraphics graphics, ThemeSnapshot theme, int size, int mouseX, int mouseY, float pt) {
        int left = book.boardLeft();
        int top = QuestBookScreen.TOP_H;
        if (grid.tiles().isEmpty()) {
            String text = entries.isEmpty() ? "Nothing here yet" : "Nothing on this page yet";
            graphics.drawString(font(), text, left + 12, top + 12, QuestColors.MUTED, false);
            return;
        }
        Set<Long> occupied = new HashSet<>();
        int minC = Integer.MAX_VALUE;
        int minR = Integer.MAX_VALUE;
        int maxC = Integer.MIN_VALUE;
        int maxR = Integer.MIN_VALUE;
        for (ModeTile tile : grid.tiles()) {
            if (tile.state() != TileState.HIDDEN) {
                occupied.add(pack(tile.col(), tile.row()));
            }
            minC = Math.min(minC, tile.col());
            minR = Math.min(minR, tile.row());
            maxC = Math.max(maxC, tile.col());
            maxR = Math.max(maxR, tile.row());
        }
        graphics.enableScissor(left, top, book.width, book.height);
        try {
            for (int c = minC - 1; c <= maxC + 1; c++) {
                for (int r = minR - 1; r <= maxR + 1; r++) {
                    if (occupied.contains(pack(c, r))) {
                        continue;
                    }
                    int x = sx(c);
                    int y = sy(r);
                    if (x + size < left || y + size < top || x > book.width || y > book.height) {
                        continue;
                    }
                    MockChrome.quietCell(graphics, x, y, size);
                }
            }
        } finally {
            graphics.disableScissor();
        }
        boolean overCard = inspectOpen && overCard(mouseX, mouseY);
        boolean overBoard = mouseX >= left && mouseY >= top && !overCard;
        for (ModeTile tile : grid.tiles()) {
            int x = sx(tile.col());
            int y = sy(tile.row());
            boolean hovered = overBoard && mouseX >= x && mouseY >= y && mouseX < x + size && mouseY < y + size;
            if (tile.state() != TileState.HIDDEN) {
                if (hovered) {
                    hoverTileId = tile.id();
                }
                QuestTileWidget widget = widgets.computeIfAbsent(tile.id(),
                        id -> new QuestTileWidget(x, y, size, QuestColors.CARD, 0, 0));
                configure(widget, tile, x, y, size);
                widget.render(graphics, mouseX, mouseY, pt);
                drawTag(graphics, tile, x, y, size);
            }
            graphics.enableScissor(left, top, book.width, book.height);
            try {
                mode.renderTileOverlay(new TileOverlayContext(graphics, entryId, tile, x, y, size, hovered,
                        tile.id().equals(selectedId), mouseX, mouseY, pt, theme));
                if (!query.isEmpty() && tile.state() != TileState.HIDDEN && !matches(tile)) {
                    MockChrome.box(graphics, x, y, size, size, SEARCH_DIM);
                }
            } catch (RuntimeException exception) {
                QuestQueen.LOGGER.error("Book mode {} tile overlay failed", modeId, exception);
            } finally {
                graphics.disableScissor();
            }
        }
        graphics.enableScissor(left, top, book.width, book.height);
        try {
            for (ModeLink link : grid.links()) {
                drawLinkExtras(graphics, theme, link, size, pt);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void configure(QuestTileWidget widget, ModeTile tile, int x, int y, int size) {
        boolean locked = tile.state() == TileState.LOCKED;
        int edge = stateColor(tile.state());
        widget.setChrome(x, y, size, QuestColors.CARD, edge, 0, false, false);
        BookIcon icon = tile.icon();
        String title = tile.label().getString().toUpperCase(Locale.ROOT);
        widget.setDecor(locked, stateWord(tile.state()), MockChrome.tagInk(edge), icon.item(), title, "", "",
                !icon.isEmpty(), net.minecraft.world.item.ItemStack.EMPTY, false, false);
        widget.setGlyph(icon.glyph());
        widget.setSprite(icon.sprite());
        widget.setRewardFaces(List.of(), false);
        widget.setProgress("", QuestColors.TEXT);
        widget.setLedger("", -1f, false, tile.id().hashCode());
        widget.setBoardClip(book.boardLeft(), QuestBookScreen.TOP_H, book.width, book.height);
        widget.setOverlayClip(inspectOpen, cardX(), cardY(), cardW(), cardH());
        widget.setSelectionHalo(tile.id().equals(selectedId));
        widget.setPorts(ports(tile, x, y, size));
        widget.setShown(true);
    }

    /** The tile's tag, bottom left where a quest shows its count. */
    private void drawTag(GuiGraphics graphics, ModeTile tile, int x, int y, int size) {
        if (tile.tag().isEmpty() || size < 40) {
            return;
        }
        int color = tile.tagColor() != 0 ? tile.tagColor() : QuestColors.MUTED;
        graphics.enableScissor(book.boardLeft(), QuestBookScreen.TOP_H, book.width, book.height);
        try {
            QuestTileWidget.tiny(graphics, font(), tile.tag(), x + QuestTileWidget.contentInset(size), y + size - 10,
                    tile.state() == TileState.LOCKED ? UiFx.withAlpha(color, QuestTileWidget.LOCKED_ALPHA) : color);
        } finally {
            graphics.disableScissor();
        }
    }

    private boolean matches(ModeTile tile) {
        try {
            return mode.matchesSearch(entryId, tile, query);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Which way a link leaves its source: along the longer axis, so a skewed link still gets an edge port. */
    static int[] direction(ModeTile from, ModeTile to) {
        return direction(from.col(), from.row(), to.col(), to.row());
    }

    static int[] direction(int fromCol, int fromRow, int toCol, int toRow) {
        int dc = toCol - fromCol;
        int dr = toRow - fromRow;
        if (dc == 0 && dr == 0) {
            return new int[]{0, 0};
        }
        if (Math.abs(dc) >= Math.abs(dr)) {
            return new int[]{Integer.signum(dc), 0};
        }
        return new int[]{0, Integer.signum(dr)};
    }

    /** Edge ports in the format {@link QuestTileWidget#setPorts} takes, as quest tiles build them. */
    private int[][] ports(ModeTile tile, int x, int y, int size) {
        if (tile.state() == TileState.LOCKED || tile.state() == TileState.HIDDEN) {
            return NO_PORTS;
        }
        // One port per side: links that leave the same way share it, and it reads open when any of them is open.
        Map<Integer, Boolean> sides = new java.util.LinkedHashMap<>();
        for (ModeLink link : grid.links()) {
            if (!link.from().equals(tile.id())) {
                continue;
            }
            Optional<ModeTile> to = grid.tile(link.to());
            if (to.isEmpty() || to.get().state() == TileState.HIDDEN) {
                continue;
            }
            int[] d = direction(tile, to.get());
            if (d[0] == 0 && d[1] == 0) {
                continue;
            }
            boolean gated = to.get().state() == TileState.LOCKED;
            sides.merge((d[0] + 1) * 3 + (d[1] + 1), gated, Boolean::logicalAnd);
        }
        List<int[]> ports = new ArrayList<>();
        sides.forEach((side, gated) -> {
            int dx = side / 3 - 1;
            int dy = side % 3 - 1;
            int[] edge = QuestBookScreen.sourceEdgeMid(x, y, size, dx, dy);
            int color = MockChrome.pathArrowColor(gated);
            ports.add(new int[]{edge[0], edge[1], color, gated ? 1 : 0, dx, dy, edge[0], edge[1], 0});
        });
        return ports.toArray(new int[0][]);
    }

    /** OR / XOR tag beside the link's arrow, then the mode's own link overlay. */
    private void drawLinkExtras(GuiGraphics graphics, ThemeSnapshot theme, ModeLink link, int size, float pt) {
        Optional<ModeTile> from = grid.tile(link.from());
        Optional<ModeTile> to = grid.tile(link.to());
        if (from.isEmpty() || to.isEmpty()) {
            return;
        }
        int fx = sx(from.get().col());
        int fy = sy(from.get().row());
        int[] d = direction(from.get(), to.get());
        int[] port = QuestBookScreen.sourceEdgeMid(fx, fy, size, d[0], d[1]);
        boolean drawn = from.get().state() != TileState.LOCKED && from.get().state() != TileState.HIDDEN
                && to.get().state() != TileState.HIDDEN;
        if (drawn && link.gate() != LinkGate.AND && size >= 48) {
            String word = link.gate().name();
            int ink = link.gate() == LinkGate.OR ? QuestColors.GATE_OR : QuestColors.GATE_XOR;
            int w = (int) Math.ceil(font().width(word) * QuestTileWidget.TINY_SCALE) + 4;
            int gap = QuestBookScreen.GAP * size / QuestBookScreen.TILE;
            int tx = d[0] != 0 ? port[0] + d[0] * (gap / 2) - w / 2 : port[0] + 5;
            int ty = d[1] != 0 ? port[1] + d[1] * (gap / 2) - 3 : port[1] - 10;
            MockChrome.box(graphics, tx, ty, w, 7, ink);
            QuestTileWidget.tiny(graphics, font(), word, tx + 2, ty + 1, MockChrome.INK);
        }
        try {
            mode.renderLinkOverlay(new LinkOverlayContext(graphics, entryId, link, from.get(), to.get(), fx, fy,
                    sx(to.get().col()), sy(to.get().row()), size, port[0], port[1], d[0], d[1], pt, theme));
        } catch (RuntimeException exception) {
            QuestQueen.LOGGER.error("Book mode {} link overlay failed", modeId, exception);
        }
    }

    private static final int SIDEBAR_ROW_STEP = QuestBookScreen.SIDEBAR_ROW_H + 3;

    private List<SidebarEntry> listed() {
        return entries.stream().filter(e -> !e.hidden()).toList();
    }

    private int rowY(int index) {
        return QuestBookScreen.TOP_H + 8 - sidebarScroll + index * SIDEBAR_ROW_STEP;
    }

    private int sidebarMaxScroll() {
        return QuestBookScreen.sidebarMaxScrollFor(listed().size() * SIDEBAR_ROW_STEP, book.height);
    }

    private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (book.sidebarCollapsed) {
            book.drawSidebarTab(graphics);
            return;
        }
        int bar = book.sidebarWidth();
        int top = QuestBookScreen.TOP_H;
        int slide = Math.round(slidePx() * 0.5f);
        MockChrome.box(graphics, 0, top, bar, book.height - top, QuestColors.SIDEBAR);
        MockChrome.box(graphics, bar - 1, top, 1, book.height - top, QuestColors.SIDEBAR_EDGE);
        List<SidebarEntry> rows = listed();
        Font font = font();
        if (rows.isEmpty()) {
            graphics.drawString(font, "Nothing here yet", 8, 32, QuestColors.MUTED, true);
        }
        graphics.enableScissor(0, top, bar, book.height);
        try {
            for (int i = 0; i < rows.size(); i++) {
                SidebarEntry entry = rows.get(i);
                int y = rowY(i);
                if (!QuestBookScreen.sidebarRowFullyInside(y, QuestBookScreen.SIDEBAR_ROW_H, top, book.height)) {
                    continue;
                }
                boolean active = entry.id().equals(entryId);
                if (active) {
                    MockChrome.box(graphics, 0, y - 1, bar, QuestBookScreen.SIDEBAR_ROW_H, QuestColors.SIDEBAR_ACTIVE);
                    MockChrome.box(graphics, 0, y - 1, 2, QuestBookScreen.SIDEBAR_ROW_H, QuestColors.NEW);
                }
                int x = 6 + slide;
                int color = entry.locked() ? QuestColors.LOCKED_TEXT : active ? QuestColors.TEXT : QuestColors.SIDEBAR_TEXT;
                int textX = x;
                if (entry.locked()) {
                    int shake = 0;
                    if (entry.id().equals(lockedCueId) && UiFx.enabled() && !UiFx.finished(lockedCueAt, LOCKED_CUE_MS)) {
                        float t = UiFx.progress(lockedCueAt, LOCKED_CUE_MS);
                        shake = Math.round((float) Math.sin(t * Math.PI * 6) * 2f * (1f - t));
                    }
                    MockChrome.padlock(graphics, x + 4 + shake, y + 8, QuestColors.LOCKED_EDGE);
                    textX = x + 12;
                } else if (!entry.icon().isEmpty()) {
                    drawIcon(graphics, entry.icon(), x, y + 1, 12, color);
                    textX = x + 15;
                }
                int right = bar - 8;
                int badgeW = 0;
                if (!entry.badge().isEmpty() && !entry.locked()) {
                    badgeW = font.width(entry.badge());
                    graphics.drawString(font, entry.badge(), right - badgeW, y + 4, QuestColors.MUTED, false);
                }
                String label = book.ellipsize(entry.label().getString(), right - textX - (badgeW > 0 ? badgeW + 4 : 0));
                if (!label.isEmpty()) {
                    graphics.drawString(font, label, textX, y + 4, color, false);
                }
            }
        } finally {
            graphics.disableScissor();
        }
        book.drawSidebarTab(graphics);
    }

    /** An icon {@code px} square at {@code (x, y)}. */
    static void drawIcon(GuiGraphics graphics, BookIcon icon, int x, int y, int px, int color) {
        if (!icon.glyph().isEmpty()) {
            QuestGlyphs.draw(graphics, icon.glyph(), x, y, px, color);
        } else if (icon.sprite() != null) {
            graphics.blit(icon.sprite(), x, y, px, px, 0f, 0f, 16, 16, 16, 16);
        } else if (!icon.item().isEmpty()) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(px / 16f, px / 16f, 1f);
            graphics.renderItem(icon.item(), 0, 0);
            pose.popPose();
        }
    }

    // ---- inspect card ------------------------------------------------------------------------------------------

    private int cardW() {
        return book.cardW();
    }

    private int cardX() {
        int x = book.width - cardW() - 16;
        return Math.max(book.boardLeft() + 8, x);
    }

    private int cardY() {
        return QuestBookScreen.TOP_H + 2;
    }

    private int titleLines(ModeTile tile) {
        return Math.max(1, Math.min(2, font().split(Component.literal(
                tile.label().getString().toUpperCase(Locale.ROOT)), cardW() - 20).size()));
    }

    private int bodyTop(ModeTile tile) {
        return cardY() + 14 + titleLines(tile) * 10 + 4;
    }

    private int bodyH(ModeTile tile) {
        int want;
        try {
            want = mode.inspectHeight(entryId, tile, cardW() - 16);
        } catch (RuntimeException exception) {
            want = 0;
        }
        return Math.max(0, Math.min(want, book.height - bodyTop(tile) - 12));
    }

    private int cardH() {
        Optional<ModeTile> tile = grid.tile(selectedId);
        return tile.map(t -> bodyTop(t) - cardY() + bodyH(t) + 8).orElse(0);
    }

    private boolean overCard(double mouseX, double mouseY) {
        return inspectOpen && grid.tile(selectedId).isPresent()
                && QuestBookScreen.over(cardX(), cardY(), cardW(), cardH(), mouseX, mouseY);
    }

    private void drawInspect(GuiGraphics graphics, ThemeSnapshot theme, ModeTile tile, int mouseX, int mouseY, float pt) {
        int x = cardX();
        int y = cardY();
        int w = cardW();
        int h = cardH();
        int edge = stateColor(tile.state());
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 260);
        MockChrome.railPanel(graphics, x, y, w, h, QuestColors.CARD, QuestColors.SIDEBAR_EDGE, edge);
        book.tinyString(graphics, stateWord(tile.state()), x + 8, y + 4, edge);
        boolean overClose = QuestBookScreen.over(x + w - 14, y, 12, 12, mouseX, mouseY);
        MockChrome.closeX(graphics, x + w - 10, y + 4, overClose ? QuestColors.TEXT : QuestColors.MUTED);
        Font font = font();
        List<net.minecraft.util.FormattedCharSequence> title = font.split(Component.literal(
                tile.label().getString().toUpperCase(Locale.ROOT)), w - 20);
        for (int i = 0; i < Math.min(2, title.size()); i++) {
            graphics.drawString(font, title.get(i), x + 8, y + 14 + i * 10, QuestColors.TEXT, false);
        }
        int top = bodyTop(tile);
        int bodyH = bodyH(tile);
        if (bodyH > 0) {
            graphics.enableScissor(x + 4, top, x + w - 4, top + bodyH);
            try {
                mode.renderInspect(new InspectContext(graphics, font, entryId, tile, x + 8, top, w - 16, bodyH,
                        mouseX, mouseY, pt, theme));
            } catch (RuntimeException exception) {
                QuestQueen.LOGGER.error("Book mode {} inspect card failed", modeId, exception);
            } finally {
                graphics.disableScissor();
            }
        }
        pose.popPose();
    }

    // ---- top bar -----------------------------------------------------------------------------------------------

    private int fitX() {
        return book.width - 8 - QuestBookScreen.FIT_S;
    }

    private int extrasW() {
        try {
            return Math.max(0, mode.topBarExtrasWidth());
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private int extrasX() {
        return toggleX() + toggleW() + 6;
    }

    /** The search box's width while a mode shows: up to the toggle, the mode's slot and the fit button. */
    int searchW() {
        int extras = extrasW();
        int right = fitX() - 8 - (extras > 0 ? extras + 6 : 0) - toggleW() - 8;
        return Math.max(48, right - book.searchX());
    }

    private void drawTopBar(GuiGraphics graphics, ThemeSnapshot theme, int mouseX, int mouseY, float pt) {
        int w = book.width;
        MockChrome.box(graphics, 0, 0, w, QuestBookScreen.TOP_H, QuestColors.SIDEBAR);
        MockChrome.box(graphics, 0, QuestBookScreen.TOP_H - 1, w, 1, QuestColors.SIDEBAR_EDGE);
        book.drawSidebarTitle(graphics);
        int sx = book.searchX();
        MockChrome.box(graphics, sx - 2, 6, book.search.getWidth() + 4, 14, QuestColors.CARD);
        MockChrome.frame(graphics, sx - 2, 6, book.search.getWidth() + 4, 14, QuestColors.SIDEBAR_EDGE);
        book.search.render(graphics, mouseX, mouseY, pt);
        drawToggle(graphics);
        int extras = extrasW();
        if (extras > 0) {
            try {
                mode.renderTopBarExtras(new TopBarContext(graphics, font(), extrasX(), 6, extras, 14, mouseX, mouseY,
                        theme));
            } catch (RuntimeException exception) {
                QuestQueen.LOGGER.error("Book mode {} top bar failed", modeId, exception);
            }
        }
        MockChrome.box(graphics, fitX(), 6, QuestBookScreen.FIT_S, QuestBookScreen.FIT_S, QuestColors.CARD);
        MockChrome.frame(graphics, fitX(), 6, QuestBookScreen.FIT_S, QuestBookScreen.FIT_S, QuestColors.SIDEBAR_EDGE);
        MockChrome.fitIcon(graphics, fitX(), 6, QuestColors.SIDEBAR_TEXT);
        if (!book.sidebarCollapsed) {
            MockChrome.chevron(graphics, book.tabX() + 3, book.tabY() + QuestBookScreen.TAB_H / 2 - 2, true,
                    QuestColors.SIDEBAR_HEADER);
        } else {
            MockChrome.chevron(graphics, book.tabX() + 3, book.tabY() + QuestBookScreen.TAB_H / 2 - 2, false,
                    QuestColors.SIDEBAR_HEADER);
        }
    }

    private void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoverTileId.isEmpty()) {
            return;
        }
        Optional<ModeTile> tile = grid.tile(hoverTileId);
        if (tile.isEmpty()) {
            return;
        }
        List<Component> lines;
        try {
            lines = mode.tileTooltip(entryId, tile.get());
        } catch (RuntimeException exception) {
            lines = List.of();
        }
        if (lines == null || lines.isEmpty()) {
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 600);
        graphics.renderComponentTooltip(font(), lines, mouseX, mouseY);
        pose.popPose();
    }

    // ---- input -------------------------------------------------------------------------------------------------

    private boolean modeClicked(double mouseX, double mouseY, int button) {
        if (book.overTab(mouseX, mouseY)) {
            book.toggleSidebar();
            return true;
        }
        if (book.search.isMouseOver(mouseX, mouseY)) {
            return book.superMouseClicked(mouseX, mouseY, button);
        }
        if (book.search.isFocused()) {
            book.search.setFocused(false);
            book.setFocused(null);
        }
        if (mouseY < QuestBookScreen.TOP_H) {
            int extras = extrasW();
            if (extras > 0 && QuestBookScreen.over(extrasX(), 6, extras, 14, mouseX, mouseY)) {
                try {
                    mode.onTopBarClick((int) mouseX - extrasX(), (int) mouseY - 6, button);
                } catch (RuntimeException exception) {
                    QuestQueen.LOGGER.error("Book mode {} top bar click failed", modeId, exception);
                }
                return true;
            }
            if (QuestBookScreen.over(fitX(), 6, QuestBookScreen.FIT_S, QuestBookScreen.FIT_S, mouseX, mouseY)) {
                fit();
            }
            return true;
        }
        if (!book.sidebarCollapsed && mouseX < book.sidebarWidth()) {
            List<SidebarEntry> rows = listed();
            for (int i = 0; i < rows.size(); i++) {
                int y = rowY(i);
                if (mouseY >= y - 2 && mouseY < y + QuestBookScreen.SIDEBAR_ROW_H) {
                    if (rows.get(i).locked()) {
                        lockedCueId = rows.get(i).id();
                        lockedCueAt = UiFx.nowMs();
                    } else {
                        openEntry(rows.get(i).id());
                    }
                    return true;
                }
            }
            return true;
        }
        Optional<ModeTile> selected = grid.tile(selectedId);
        if (inspectOpen && selected.isPresent() && overCard(mouseX, mouseY)) {
            int x = cardX();
            int y = cardY();
            if (QuestBookScreen.over(x + cardW() - 14, y, 12, 12, mouseX, mouseY)) {
                inspectOpen = false;
                return true;
            }
            int top = bodyTop(selected.get());
            if (mouseY >= top && mouseY < top + bodyH(selected.get())) {
                try {
                    mode.onInspectClick(entryId, selected.get(), (int) mouseX - (x + 8), (int) mouseY - top, button);
                } catch (RuntimeException exception) {
                    QuestQueen.LOGGER.error("Book mode {} inspect click failed", modeId, exception);
                }
            }
            return true;
        }
        int size = tilePx();
        for (ModeTile tile : grid.tiles()) {
            if (tile.state() == TileState.HIDDEN) {
                continue;
            }
            int x = sx(tile.col());
            int y = sy(tile.row());
            if (mouseX >= x && mouseY >= y && mouseX < x + size && mouseY < y + size) {
                boolean taken;
                try {
                    taken = mode.onTileClick(entryId, tile, button);
                } catch (RuntimeException exception) {
                    QuestQueen.LOGGER.error("Book mode {} tile click failed", modeId, exception);
                    taken = true;
                }
                if (!taken && button == 0) {
                    selectedId = tile.id();
                    inspectOpen = true;
                }
                return true;
            }
        }
        if (button == 0 && inspectOpen) {
            inspectOpen = false;
            return true;
        }
        dragging = true;
        lastMx = mouseX;
        lastMy = mouseY;
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (dragging) {
            camX -= (mouseX - lastMx) / zoom;
            camY -= (mouseY - lastMy) / zoom;
            lastMx = mouseX;
            lastMy = mouseY;
        }
        return true;
    }

    boolean mouseReleased() {
        dragging = false;
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!book.sidebarCollapsed && mouseX < book.sidebarWidth() && mouseY >= QuestBookScreen.TOP_H) {
            sidebarScroll = Math.max(0, Math.min(sidebarMaxScroll(), sidebarScroll - (int) Math.round(scrollY * 38)));
            return true;
        }
        if (overCard(mouseX, mouseY) || mouseY < QuestBookScreen.TOP_H) {
            return true;
        }
        stepZoom(scrollY > 0 ? 1 : -1, mouseX, mouseY);
        return true;
    }

    /**
     * Keys while a mode shows. Escape closes the search, then the card, then the book; Enter in the search jumps to
     * the first match. True when the key was handled.
     */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean typing = book.search.isFocused();
        if (!typing && QuestKeybinds.OPEN_BOOK.matches(keyCode, scanCode)) {
            book.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (typing) {
                book.search.setFocused(false);
                book.setFocused(null);
            } else if (inspectOpen) {
                inspectOpen = false;
            } else {
                book.onClose();
            }
            return true;
        }
        if (typing && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            for (ModeTile tile : grid.tiles()) {
                if (tile.state() != TileState.HIDDEN && !query.isEmpty() && matches(tile)) {
                    selectedId = tile.id();
                    inspectOpen = true;
                    centerOn(tile);
                    break;
                }
            }
            return true;
        }
        return false;
    }

    private void centerOn(ModeTile tile) {
        camX = tile.col() * QuestBookScreen.STRIDE + QuestBookScreen.TILE / 2.0 - contentW() / (2.0 * zoom);
        camY = tile.row() * QuestBookScreen.STRIDE + QuestBookScreen.TILE / 2.0
                - (QuestBookScreen.TOP_H + contentH() / 2.0) / zoom;
    }

    void onSearch(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    Component searchHint() {
        try {
            return mode.searchHint();
        } catch (RuntimeException exception) {
            return Component.literal("Search");
        }
    }

    private static long pack(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
