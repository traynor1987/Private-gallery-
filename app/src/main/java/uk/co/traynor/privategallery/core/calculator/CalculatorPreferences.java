package uk.co.traynor.privategallery.core.calculator;

import java.io.*;
import java.util.*;

/** One strict codec for local state and authenticated portable imports. No recovery by silent coercion. */
public final class CalculatorPreferences {
    public static final String NAME = "nova_calculator_v1";
    public static final int MAX_HISTORY = 50;
    // Fifty 512-character modified-UTF expressions, results and framing fit below this cap.
    private static final int MAX_ENCODED_HISTORY = 110000;
    public record Entry(String expression, String result, boolean radians) {}
    private CalculatorPreferences() {}
    public static void validate(Map<String, ?> values) {
        for (var entry : values.entrySet()) {
            Object v = entry.getValue();
            switch (entry.getKey()) {
                case "angle": require(v instanceof String && Set.of("DEG", "RAD").contains(v)); break;
                case "scientific": case "history-enabled": require(v instanceof Boolean); break;
                case "memory": require(v instanceof String); CalculatorEngine.storedNumber((String)v); break;
                case "history": require(v instanceof String); decode((String)v); break;
                default: throw new IllegalArgumentException("Unsupported Calculator preference");
            }
        }
        if (Boolean.FALSE.equals(values.get("history-enabled")) && values.containsKey("history")) require(decode((String)values.get("history")).isEmpty());
    }
    public static String encode(List<Entry> entries) {
        require(entries.size() <= MAX_HISTORY);
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(1); out.writeInt(entries.size());
                for (Entry entry : entries) {
                    validateEntry(entry); out.writeUTF(entry.expression()); out.writeUTF(entry.result()); out.writeBoolean(entry.radians());
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException impossible) { throw new IllegalArgumentException("Could not encode history", impossible); }
    }
    public static List<Entry> decode(String encoded) {
        require(encoded != null && encoded.length() <= MAX_ENCODED_HISTORY);
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            require(Base64.getEncoder().encodeToString(bytes).equals(encoded));
            try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                require(in.readInt() == 1); int count = in.readInt(); require(count >= 0 && count <= MAX_HISTORY);
                List<Entry> result = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String expression = in.readUTF(), value = in.readUTF(); int angle = in.readUnsignedByte(); require(angle <= 1);
                    Entry entry = new Entry(expression, value, angle == 1); validateEntry(entry); result.add(entry);
                }
                require(in.available() == 0); return List.copyOf(result);
            } finally { Arrays.fill(bytes, (byte)0); }
        } catch (IOException e) { throw new IllegalArgumentException("Invalid Calculator history", e); }
    }
    private static void validateEntry(Entry entry) {
        require(entry != null && entry.expression() != null && entry.expression().length() <= CalculatorEngine.MAX_EXPRESSION);
        CalculatorEngine.storedNumber(entry.result());
        require(CalculatorEngine.canonical(CalculatorEngine.evaluate(entry.expression(), entry.radians())).equals(entry.result()));
    }
    private static void require(boolean value) { if (!value) throw new IllegalArgumentException("Invalid Calculator settings or history"); }
}
