package dev.aof.questqueen.data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class StartNodes {
    private StartNodes() {
    }

    public static Set<String> of(Chapter chapter) {
        return of(chapter.tiles(), chapter.links());
    }

    public static Set<String> of(List<Tile> tiles, List<Link> links) {
        Set<String> inbound = links.stream().map(Link::to).collect(Collectors.toSet());
        Set<String> starts = new HashSet<>();
        for (Tile tile : tiles) {
            // A tile whose only parents are undrawn (requires) has no arrow in, but it is not a start.
            boolean required = tile.requires().map(gate -> !gate.conditions().isEmpty()).orElse(false);
            if (!inbound.contains(tile.id()) && !required) {
                starts.add(tile.id());
            }
        }
        return starts;
    }

    public static boolean isStart(Chapter chapter, String tileId) {
        return of(chapter).contains(tileId);
    }
}
