package hanamuramiyu.monban.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class PlayerIdentity {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final String name;
    private final String normalizedName;
    private final UUID technicalUuid;
    private final IdentityType type;

    private PlayerIdentity(String name, UUID technicalUuid, IdentityType type) {
        this.name = requireValidName(name);
        this.normalizedName = normalizeValidName(name);
        this.technicalUuid = technicalUuid;
        this.type = Objects.requireNonNull(type, "type");

        if (type == IdentityType.ONLINE && technicalUuid == null) {
            throw new NullPointerException("technicalUuid");
        }
    }

    public static PlayerIdentity online(String name, UUID verifiedUuid) {
        return new PlayerIdentity(name, Objects.requireNonNull(verifiedUuid, "verifiedUuid"), IdentityType.ONLINE);
    }

    public static PlayerIdentity offline(String name) {
        return new PlayerIdentity(name, null, IdentityType.OFFLINE);
    }

    public static PlayerIdentity offline(String name, UUID technicalUuid) {
        return new PlayerIdentity(name, Objects.requireNonNull(technicalUuid, "technicalUuid"), IdentityType.OFFLINE);
    }

    public String name() {
        return name;
    }

    public String normalizedName() {
        return normalizedName;
    }

    public Optional<UUID> technicalUuid() {
        return Optional.ofNullable(technicalUuid);
    }

    public Optional<UUID> verifiedUuid() {
        return type == IdentityType.ONLINE
                ? Optional.of(technicalUuid)
                : Optional.empty();
    }

    public boolean hasVerifiedUuid() {
        return type == IdentityType.ONLINE;
    }

    public IdentityType type() {
        return type;
    }

    public boolean matchesName(String otherName) {
        return isValidName(otherName) && normalizedName.equals(normalizeValidName(otherName));
    }

    public boolean sameIdentityAs(PlayerIdentity other) {
        return equals(other);
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public static String normalizeName(String name) {
        return normalizeValidName(requireValidName(name));
    }

    private static String requireValidName(String name) {
        Objects.requireNonNull(name, "name");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid Minecraft player name: " + name);
        }
        return name;
    }

    private static String normalizeValidName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PlayerIdentity other)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        return switch (type) {
            case ONLINE -> technicalUuid.equals(other.technicalUuid);
            case OFFLINE -> normalizedName.equals(other.normalizedName);
        };
    }

    @Override
    public int hashCode() {
        return switch (type) {
            case ONLINE -> Objects.hash(type, technicalUuid);
            case OFFLINE -> Objects.hash(type, normalizedName);
        };
    }

    @Override
    public String toString() {
        return "PlayerIdentity[name=" + name + ", technicalUuid=" + technicalUuid + ", type=" + type + "]";
    }
}
