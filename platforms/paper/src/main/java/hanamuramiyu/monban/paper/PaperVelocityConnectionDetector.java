package hanamuramiyu.monban.paper;

import org.bukkit.Server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class PaperVelocityConnectionDetector {
    private PaperVelocityConnectionDetector() {
    }

    static boolean isVelocityEnabled(Server server) {
        try {
            Class<?> globalConfiguration = Class.forName(
                    "io.papermc.paper.configuration.GlobalConfiguration"
            );
            Method get = globalConfiguration.getMethod("get");
            Object configuration = get.invoke(null);
            Object proxies = field(configuration, "proxies");
            Object velocity = field(proxies, "velocity");
            Object enabled = field(velocity, "enabled");
            return enabled instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    static boolean isVelocityOnlineMode(Server server) {
        try {
            Class<?> globalConfiguration = Class.forName(
                    "io.papermc.paper.configuration.GlobalConfiguration"
            );
            Method get = globalConfiguration.getMethod("get");
            Object configuration = get.invoke(null);
            Object proxies = field(configuration, "proxies");
            Object velocity = field(proxies, "velocity");
            Object onlineMode = field(velocity, "onlineMode");
            return onlineMode instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
