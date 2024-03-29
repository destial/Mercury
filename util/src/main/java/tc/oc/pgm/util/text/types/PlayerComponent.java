package tc.oc.pgm.util.text.types;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.event.HoverEvent.Action;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import net.kyori.text.format.TextFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextFormatter;

/** PlayerComponent is used to format player names in a consistent manner with optional styling */
public interface PlayerComponent {

  static Component UNKNOWN =
      TranslatableComponent.of("misc.unknown", TextColor.DARK_AQUA, TextDecoration.ITALIC);

  static Component of(UUID playerId, NameStyle style) {
    Player player = Bukkit.getPlayer(playerId);
    return player != null ? of(player, style) : UNKNOWN;
  }

  static Component of(String name, NameStyle style) {
    return of(null, name, style);
  }

  static Component of(CommandSender sender, NameStyle style) {
    return sender instanceof Player
        ? of((Player) sender, style)
        : TranslatableComponent.of("misc.console", TextColor.DARK_AQUA);
  }

  static Component of(Player player, NameStyle style) {
    return of(player, style, null);
  }

  static Component of(Player player, String defName, NameStyle style) {
    return of(player, defName, style, null);
  }

  static Component of(Player player, NameStyle style, @Nullable Player viewer) {
    return of(player, "", style, viewer);
  }

  static Component of(
      @Nullable Player player, String defName, NameStyle style, @Nullable Player viewer) {
    // Offline player or not visible
    if ((player == null || !player.isOnline())) {
      return formatOffline(defName).build();
    }

    // For name styles that don't allow vanished, make vanished appear offline
    if (!style.showVanish && isVanished(player)) {
      return formatOffline(getName(player)).build();
    }

    TextComponent.Builder builder;

    switch (style) {
      case COLOR:
        builder = formatColor(player);
        break;
      case CONCISE:
        builder = formatConcise(player, false);
        break;
      case FANCY:
        builder = formatFancy(player);
        break;
      case TAB:
        builder = formatTab(player, viewer);
        break;
      case LEGACY_TAB:
        builder = formatLegacyTab(player, viewer);
        break;
      case VERBOSE:
        builder = formatVerbose(player);
        break;
      default:
        builder = formatPlain(player);
        break;
    }

    return builder.build();
  }

  static String getName(Player player) {
    return player.getName();
  }

  static String getDisplayName(Player player) {
    return player.getDisplayName();
  }

  // What an offline or vanished username renders as
  static TextComponent.Builder formatOffline(String name) {
    return TextComponent.builder().append(name);
  }

  // No color or formatting, simply the name
  static TextComponent.Builder formatPlain(Player player) {
    return TextComponent.builder().append(getName(player));
  }

  // Color only
  static TextComponent.Builder formatColor(Player player) {
    String displayName = getDisplayName(player);
    char colorChar = displayName.charAt((displayName.indexOf(getName(player)) - 1));
    TextColor color = TextFormatter.convert(ChatColor.getByChar(colorChar));
    return TextComponent.builder(getName(player), color);
  }

  // Color, flair & teleport
  static TextComponent.Builder formatFancy(Player player) {
    TextComponent.Builder prefix = getPrefixComponent(player);
    TextComponent.Builder colorName = formatColor(player);
    TextComponent.Builder suffix = getSuffixComponent(player);
    return formatTeleport(prefix.append(colorName).append(suffix), getName(player));
  }

  // Color, flair, death status, and vanish
  static TextComponent.Builder formatTab(Player player, @Nullable Player viewer) {
    TextComponent.Builder prefix = getPrefixComponent(player);
    TextComponent.Builder colorName = formatColor(player);
    TextComponent.Builder suffix = getSuffixComponent(player);

    if (isDead(player)) {
      colorName.color(TextColor.DARK_GRAY);
    }

    if (isVanished(player)) {
      colorName = formatVanished(colorName);
    }

    if (player.equals(viewer)) {
      colorName.decoration(TextDecoration.BOLD, true);
    }

    return prefix.append(colorName).append(suffix);
  }

  // Color, flair, and vanish
  static TextComponent.Builder formatLegacyTab(Player player, @Nullable Player viewer) {
    TextComponent.Builder prefix = getPrefixComponent(player);
    TextComponent.Builder colorName = formatColor(player);
    TextComponent.Builder suffix = getSuffixComponent(player);
    if (isVanished(player)) {
      colorName = formatVanished(colorName);
    }

    if (player.equals(viewer)) {
      colorName.decoration(TextDecoration.BOLD, true);
    }
    return prefix.append(colorName).append(suffix);
  }

  // Color and flair with optional vanish
  static TextComponent.Builder formatConcise(Player player, boolean vanish) {
    TextComponent.Builder prefix = getPrefixComponent(player);
    TextComponent.Builder colorName = formatColor(player);
    TextComponent.Builder suffix = getSuffixComponent(player);

    if (isVanished(player) && vanish) {
      colorName = formatVanished(colorName);
    }

    return prefix.append(colorName).append(suffix);
  }

  // Color, flair, vanished, and teleport
  static TextComponent.Builder formatVerbose(Player player) {
    return formatTeleport(formatConcise(player, true), getName(player));
  }

  /**
   * Get the player's prefix as a {@link Component}
   *
   * @param player The player
   * @return a component with a player's prefix
   */
  static TextComponent.Builder getPrefixComponent(Player player) {
    String displayName = getDisplayName(player);
    String prefix = displayName.substring(0, displayName.indexOf(getName(player)) - 2);
    return stringToComponent(prefix);
  }

  /**
   * Get the player's suffix as a {@link Component}
   *
   * @param player The player
   * @return a component with a player's prefix
   */
  static TextComponent.Builder getSuffixComponent(Player player) {
    String[] parts = getDisplayName(player).split(getName(player));
    if (parts.length != 2) {
      return TextComponent.builder();
    }
    return stringToComponent(parts[1]);
  }

  static TextComponent.Builder stringToComponent(String str) {
    TextComponent.Builder component = TextComponent.builder();
    boolean isColor = false;
    TextFormat color = null;
    List<TextFormat> decorations = Lists.newArrayList();
    if (str.isEmpty()) {
      return component.color(TextColor.WHITE);
    }
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) == ChatColor.COLOR_CHAR) {
        isColor = true;
        continue;
      }
      if (isColor) {
        TextFormat formatting = TextFormatter.convertFormat(ChatColor.getByChar(str.charAt(i)));
        if (formatting instanceof TextColor) {
          color = formatting;
          decorations.clear();
        } else {
          decorations.add(formatting);
        }
        isColor = false;
      } else {
        TextComponent part =
            TextComponent.of(
                String.valueOf(str.charAt(i)),
                (color != null ? (TextColor) color : TextColor.WHITE));
        for (TextFormat decoration : decorations)
          part = part.decoration((TextDecoration) decoration, true);
        component.append(part);
      }
    }
    return component;
  }

  // Format component to have teleport click/hover
  static TextComponent.Builder formatTeleport(TextComponent.Builder username, String name) {
    return username
        .hoverEvent(
            HoverEvent.of(
                Action.SHOW_TEXT,
                TranslatableComponent.of("misc.teleportTo", TextColor.GRAY, username.build())))
        .clickEvent(ClickEvent.runCommand("/tp " + name));
  }

  // Format for visible vanished players
  static TextComponent.Builder formatVanished(TextComponent.Builder builder) {
    return builder.decoration(TextDecoration.STRIKETHROUGH, true);
  }

  // Player state checks
  static boolean isVanished(Player player) {
    return player.hasMetadata("isVanished");
  }

  static boolean isDead(Player player) {
    return player.hasMetadata("isDead") || player.isDead();
  }
}
