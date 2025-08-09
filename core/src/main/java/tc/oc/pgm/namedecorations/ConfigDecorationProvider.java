package tc.oc.pgm.namedecorations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.PGM;

/**
 * A simple, config-based decoration provider, that will assign prefixes and suffixes based on
 * player permissions
 */
public class ConfigDecorationProvider implements NameDecorationProvider {

  @Override
  public String getPrefix(UUID uuid) {
    Player player = Bukkit.getPlayer(uuid);
    if (!PGM.get().isPAPIEnabled())
      return groups(uuid)
          .filter(g -> g.getPrefix() != null)
          .map(Config.Group::getPrefix)
          .collect(Collectors.joining());
    return PlaceholderAPI.setPlaceholders(
        player.getPlayer(),
        groups(uuid)
            .filter(g -> g.getPrefix() != null)
            .map(Config.Group::getPrefix)
            .collect(Collectors.joining()));
  }

  @Override
  public String getSuffix(UUID uuid) {
    Player player = Bukkit.getPlayer(uuid);
    if (!PGM.get().isPAPIEnabled())
      return groups(uuid)
          .filter(g -> g.getSuffix() != null)
          .map(Config.Group::getSuffix)
          .collect(Collectors.joining());
    return PlaceholderAPI.setPlaceholders(
        player.getPlayer(),
        groups(uuid)
            .filter(g -> g.getSuffix() != null)
            .map(Config.Group::getSuffix)
            .collect(Collectors.joining()));
  }

  @Override
  public String getMessageColor(UUID uuid) {
    return groups(uuid)
        .filter(g -> g.getMessageColor() != null && !g.getMessageColor().equals("&r"))
        .map(Config.Group::getMessageColor)
        .collect(Collectors.joining());
  }

  public Optional<ChatColor> getMessageChatColor(UUID uuid) {
    return groups(uuid)
        .filter(g -> g.getMessageChatColor() != null)
        .map(Config.Group::getMessageChatColor)
        .findFirst();
  }

  @Override
  public Component getPrefixComponent(UUID uuid) {
    return generateFlair(groups(uuid), true);
  }

  @Override
  public Component getSuffixComponent(UUID uuid) {
    return generateFlair(groups(uuid), false);
  }

  private Component generateFlair(Stream<? extends Config.Group> flairs, boolean prefix) {
    TextComponent.Builder builder = TextComponent.builder();
    flairs
        .filter(p -> prefix ? p.getPrefix() != null : p.getSuffix() != null)
        .map(Config.Group::getFlair)
        .forEach(flair -> builder.append(flair.getComponent(prefix)));
    return builder.build();
  }

  private Stream<? extends Config.Group> groups(UUID uuid) {
    final Player player = Bukkit.getPlayer(uuid);
    if (player == null) return Stream.empty();
    List<String> nodes =
        player.getEffectivePermissions().stream()
            .filter(PermissionAttachmentInfo::getValue)
            .map(PermissionAttachmentInfo::getPermission)
            .collect(Collectors.toList());
    return PGM.get().getConfiguration().getGroups().stream()
        .filter(g -> nodes.stream().anyMatch(p -> p.equals(g.getPermission().getName())));
  }
}
