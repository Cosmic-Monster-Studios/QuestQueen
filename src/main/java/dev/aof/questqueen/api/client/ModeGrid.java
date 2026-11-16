package dev.aof.questqueen.api.client;

import java.util.List;
import java.util.Optional;

/** The board for one sidebar entry: its tiles and the links between them. */
public record ModeGrid(List<ModeTile> tiles, List<ModeLink> links) {
    public static final ModeGrid EMPTY = new ModeGrid(List.of(), List.of());

    public ModeGrid {
        tiles = tiles == null ? List.of() : List.copyOf(tiles);
        links = links == null ? List.of() : List.copyOf(links);
    }

    public Optional<ModeTile> tile(String id) {
        for (ModeTile tile : tiles) {
            if (tile.id().equals(id)) {
                return Optional.of(tile);
            }
        }
        return Optional.empty();
    }
}
