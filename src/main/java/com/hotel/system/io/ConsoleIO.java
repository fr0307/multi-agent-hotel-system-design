package com.hotel.system.io;

import com.hotel.system.config.AppConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ConsoleIO {
    private final BufferedReader reader;
    private final boolean autoApprove;

    public ConsoleIO(BufferedReader reader) {
        this(reader, false);
    }

    public ConsoleIO(BufferedReader reader, AppConfig cfg) {
        this(reader, cfg != null && cfg.autoApprove);
    }

    public ConsoleIO(BufferedReader reader, boolean autoApprove) {
        this.reader = reader;
        this.autoApprove = autoApprove;
    }

    public String readKeyword(String prompt, String... allowed) throws IOException {
        // Always print the prompt so the conversation log/console shows what was asked.
        System.out.print(prompt);

        if (autoApprove) {
            String autoChoice = pickAutoChoice(allowed);
            System.out.println(autoChoice + "  [auto-approve]");
            return autoChoice;
        }

        Map<String, String> set = new LinkedHashMap<>();
        for (String a : allowed) set.put(a.toLowerCase(Locale.ROOT), a);

        while (true) {
            String line = reader.readLine();
            if (line == null) return allowed[0];
            String v = line.trim().toLowerCase(Locale.ROOT);
            if (set.containsKey(v)) return v;
            System.out.println("Invalid input. Allowed: " + String.join("/", allowed));
            System.out.print(prompt);
        }
    }

    private static String pickAutoChoice(String[] allowed) {
        for (String a : allowed) {
            if ("approve".equalsIgnoreCase(a)) return "approve";
        }
        return allowed.length == 0 ? "approve" : allowed[0];
    }
}
