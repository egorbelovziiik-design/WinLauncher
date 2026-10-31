package dev.funtime.auctionhelper.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads an item's tooltip lines through reflection.
 *
 * <p>The tooltip API changed in almost every Minecraft generation
 * ({@code getTooltipLines(Player, TooltipFlag)} in 1.16.5,
 * {@code getTooltipLines(TooltipContext, Player, TooltipFlag)} later, different
 * {@code TooltipFlag} shapes after 1.20.5). Doing this reflectively keeps the version
 * modules small and, more importantly, makes a signature change a logged warning instead
 * of a crash or a compile break: the mod simply sees an empty lore and reports
 * "unknown GUI".</p>
 */
public final class TooltipReader {
    private TooltipReader() {
    }

    public static List<String> readLines(Object stack, Object player, Object level) {
        if (stack == null) {
            return Collections.emptyList();
        }
        try {
            Method method = findTooltipMethod(stack.getClass());
            if (method == null) {
                return Collections.emptyList();
            }
            Object[] args = buildArguments(method, player, level);
            if (args == null) {
                return Collections.emptyList();
            }
            Object result = method.invoke(stack, args);
            if (!(result instanceof List)) {
                return Collections.emptyList();
            }
            List<?> lines = (List<?>) result;
            List<String> text = new ArrayList<String>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                Object line = lines.get(i);
                if (line == null) {
                    continue;
                }
                try {
                    Method getString = line.getClass().getMethod("getString");
                    Object value = getString.invoke(line);
                    text.add(value == null ? "" : value.toString());
                } catch (ReflectiveOperationException ignored) {
                    text.add(line.toString());
                }
            }
            return text;
        } catch (Throwable error) {
            Log.warn("Could not read item tooltip: " + error.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private static Method findTooltipMethod(Class<?> stackClass) {
        Method best = null;
        for (Method method : stackClass.getMethods()) {
            if (!"getTooltipLines".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length > 3 || params.length < 2) {
                continue;
            }
            if (best == null || params.length > best.getParameterTypes().length) {
                best = method;
            }
        }
        return best;
    }

    private static Object[] buildArguments(Method method, Object player, Object level) {
        Class<?>[] params = method.getParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Object value = valueFor(params[i], player, level);
            if (value == null) {
                return null;
            }
            args[i] = value;
        }
        return args;
    }

    private static Object valueFor(Class<?> type, Object player, Object level) {
        String name = type.getName();
        if (name.endsWith("TooltipFlag")) {
            return tooltipFlag(type);
        }
        if (name.contains("TooltipContext")) {
            return tooltipContext(type, level);
        }
        if (player != null && type.isInstance(player)) {
            return player;
        }
        if (level != null && type.isInstance(level)) {
            return level;
        }
        return null;
    }

    private static Object tooltipFlag(Class<?> type) {
        if (type.isEnum()) {
            return enumConstant(type, "NORMAL");
        }
        try {
            Class<?>[] nested = type.getDeclaredClasses();
            for (Class<?> candidate : nested) {
                if (candidate.isEnum()) {
                    Object constant = enumConstant(candidate, "NORMAL");
                    if (constant != null) {
                        return constant;
                    }
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            java.lang.reflect.Field field = type.getField("NORMAL");
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object enumConstant(Class<?> enumType, String constantName) {
        try {
            Object[] constants = enumType.getEnumConstants();
            if (constants == null) {
                return null;
            }
            for (Object constant : constants) {
                if (constant != null && constantName.equalsIgnoreCase(((Enum<?>) constant).name())) {
                    return constant;
                }
            }
            return constants.length > 0 ? constants[0] : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object tooltipContext(Class<?> type, Object level) {
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            if (!"of".equals(method.getName()) || !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 0) {
                try {
                    return method.invoke(null);
                } catch (Throwable ignored) {
                    // try the next overload
                }
            } else if (params.length == 1 && level != null && params[0].isInstance(level)) {
                try {
                    return method.invoke(null, level);
                } catch (Throwable ignored) {
                    // try the next overload
                }
            }
        }
        try {
            java.lang.reflect.Field field = type.getField("DEFAULT");
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
