package dev.aof.questqueen.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * When addon reward and task types may be registered, and what their names must look like. Internal: addons
 * register through {@code dev.aof.questqueen.api.QuestQueenApi}.
 *
 * <p>Registration is open while mods construct and set up, and closes when loading completes, before any world or
 * datapack loads. A chapter decoded after that sees one fixed set of types, so the same file never reads one way on
 * one load and another way on the next.
 */
public final class AddonTypes {
    private static volatile boolean frozen;

    private AddonTypes() {
    }

    /** Close registration. Quest Queen calls this once loading completes; later registrations throw. */
    public static void freeze() {
        frozen = true;
    }

    public static boolean frozen() {
        return frozen;
    }

    /** Tests only: open registration again. */
    static void unfreezeForTests() {
        frozen = false;
    }

    /**
     * The registry key for an addon type, or a clear error. Addon types are namespaced ({@code mymod:thing}) so
     * they can never collide with Quest Queen's own unprefixed types or with another addon's.
     */
    public static String checkedKey(String kind, String type) {
        if (frozen) {
            throw new IllegalStateException("Quest Queen " + kind + " type '" + type
                    + "' registered too late: register in your mod constructor or FMLCommonSetupEvent");
        }
        if (type == null || type.indexOf(':') <= 0 || type.endsWith(":")) {
            throw new IllegalArgumentException("Quest Queen " + kind + " type '" + type
                    + "' must be namespaced, like 'mymod:" + (type == null ? "thing" : type) + "'");
        }
        String key = type.toLowerCase(Locale.ROOT);
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null || !key.equals(type)) {
            throw new IllegalArgumentException("Quest Queen " + kind + " type '" + type
                    + "' is not a valid lowercase id");
        }
        if ("questqueen".equals(id.getNamespace()) || "minecraft".equals(id.getNamespace())) {
            throw new IllegalArgumentException("Quest Queen " + kind + " type '" + type
                    + "' uses a reserved namespace; use your own mod id");
        }
        return key;
    }

    /** True for a namespaced type name, the form every addon type has. */
    public static boolean namespaced(String type) {
        return type != null && type.indexOf(':') > 0;
    }
}
