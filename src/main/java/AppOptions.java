import java.util.HashMap;
import java.util.Map;

final class AppOptions {
    private final Map<String, String> values = new HashMap<>();

    AppOptions(String[] args) {
        if (args.length % 2 != 0)
            throw new IllegalArgumentException("Options must have a value");
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--"))
                throw new IllegalArgumentException("Unknown argument: " + args[i]);
            values.put(args[i], args[i + 1]);
        }
    }

    String get(String option, String fallback) {
        return values.getOrDefault(option, fallback);
    }

    int port(String option, int fallback) {
        int port = Integer.parseInt(get(option, Integer.toString(fallback)));
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException(option + " must be 1 to 65535");
        return port;
    }
}
