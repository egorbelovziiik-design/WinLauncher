package dev.funtime.auctionhelper.util;

import java.util.ArrayList;
import java.util.List;

/** RFC 4180-ish CSV helper (comma separated, quoted fields, CRLF tolerant). */
public final class Csv {
    private Csv() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public static String join(List<String> fields) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(fields.get(i)));
        }
        return builder.toString();
    }

    /** Splits a single CSV line into fields. */
    public static List<String> split(String line) {
        List<String> fields = new ArrayList<String>();
        if (line == null) {
            return fields;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else if (c != '\r') {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }
}
