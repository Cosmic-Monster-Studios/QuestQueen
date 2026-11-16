package dev.aof.questqueen.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record Gate(GateOp op, List<GateCondition> conditions) {
    public static final Gate AND = new Gate(GateOp.AND, List.of());
    public static final Gate OR = new Gate(GateOp.OR, List.of());
    public static final Gate XOR = new Gate(GateOp.XOR, List.of());
    public static final Gate NOT = new Gate(GateOp.NOT, List.of());

    public static Gate of(GateOp op) {
        return switch (op) {
            case AND -> AND;
            case OR -> OR;
            case XOR -> XOR;
            case NOT -> NOT;
        };
    }

    public Gate withOp(GateOp next) {
        return new Gate(next, conditions);
    }

    private static final Codec<Gate> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GateOp.CODEC.optionalFieldOf("op", GateOp.AND).forGetter(Gate::op),
            GateCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(Gate::conditions)
    ).apply(instance, Gate::new));

    /**
     * Reads {@code "or"} or {@code {"op": "or", "conditions": [...]}}. Writes the short form only when there are no
     * conditions: writing it always dropped every condition on the way to the client.
     */
    public static final Codec<Gate> CODEC = Codec.either(GateOp.CODEC, OBJECT_CODEC).xmap(
            either -> either.map(op -> new Gate(op, List.of()), gate -> gate),
            gate -> gate.conditions().isEmpty() ? Either.left(gate.op()) : Either.right(gate)
    );

    public Gate {
        conditions = List.copyOf(conditions);
    }
}
