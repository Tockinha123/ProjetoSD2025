package br.com.tocka;

import java.util.HashMap;
import java.util.Map;

/**
 * Small configuration helper.
 *
 * Precedence: CLI args > environment variables > defaults.
 *
 * Supported args:
 *  --rabbit-host <host>   or --rabbit-host=<host>
 *  --rabbit-port <port>   or --rabbit-port=<port>
 *  --rabbit-user <user>   or --rabbit-user=<user>
 *  --rabbit-pass <pass>   or --rabbit-pass=<pass>
 *
 * Supported env vars:
 *  RABBIT_HOST, RABBIT_PORT, RABBIT_USER, RABBIT_PASS
 */
public final class Config {

    private final Map<String, String> args;

    private Config(Map<String, String> args) {
        this.args = args;
    }

    public static Config fromArgs(String[] argv) {
        return new Config(parseArgs(argv));
    }

    public boolean hasFlag(String argKey) {
        return args.containsKey(argKey);
    }

    public String get(String argKey, String envKey, String defaultValue) {
        String value = args.get(argKey);
        if (isBlank(value)) {
            value = System.getenv(envKey);
        }
        if (isBlank(value)) {
            value = defaultValue;
        }
        return value;
    }

    public int getInt(String argKey, String envKey, int defaultValue) {
        String value = args.get(argKey);
        if (isBlank(value)) {
            value = System.getenv(envKey);
        }
        if (isBlank(value)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static Map<String, String> parseArgs(String[] argv) {
        Map<String, String> map = new HashMap<>();
        if (argv == null) {
            return map;
        }

        for (int i = 0; i < argv.length; i++) {
            String token = argv[i];
            if (token == null) {
                continue;
            }
            token = token.trim();

            if ("-h".equals(token)) {
                map.put("help", "");
                continue;
            }
            if (!token.startsWith("--") || token.length() <= 2) {
                continue;
            }

            String key;
            String value;

            int eqIndex = token.indexOf('=');
            if (eqIndex > 2) {
                key = token.substring(2, eqIndex).trim();
                value = token.substring(eqIndex + 1).trim();
            } else {
                key = token.substring(2).trim();
                value = null;

                if (i + 1 < argv.length) {
                    String next = argv[i + 1];
                    if (next != null) {
                        next = next.trim();
                    }
                    if (!isBlank(next) && !next.startsWith("--")) {
                        value = next;
                        i++;
                    }
                }
            }

            if (!isBlank(key)) {
                map.put(key, value == null ? "" : value);
            }
        }

        return map;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
