package tc.oc.pgm.death;

import java.util.Locale;
import java.util.logging.Logger;
import net.kyori.text.Component;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import net.kyori.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerDeathBroadcastEvent;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.util.text.TextTranslations;

@ListenerScope(MatchScope.RUNNING)
public class DeathMessageMatchModule implements MatchModule, Listener {

  private final Logger logger;

  public DeathMessageMatchModule(Match match) {
    logger = match.getLogger();
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onVanillaDeath(final PlayerDeathEvent event) {
    event.setDeathMessage(null);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void handleDeathBroadcast(MatchPlayerDeathEvent event) {
    if (!event.getMatch().isRunning()) return;

    DeathMessageBuilder builder = new DeathMessageBuilder(event, logger);
    Component message = builder.getMessage().color(TextColor.GRAY);

    for (MatchPlayer viewer : event.getMatch().getPlayers()) {
      switch (viewer.getSettings().getValue(SettingKey.DEATH)) {
        case DEATH_OWN:
          if (event.isInvolved(viewer)) {
            viewer.sendMessage(message);
          } else if (event.isTeamKill() && viewer.getBukkit().hasPermission(Permissions.STAFF)) {
            viewer.sendMessage(message.decoration(TextDecoration.ITALIC, true));
          }
          break;
        case DEATH_ALL:
          if (event.isInvolved(viewer)) {
            viewer.sendMessage(message.decoration(TextDecoration.BOLD, true));
          } else {
            viewer.sendMessage(message);
          }
          break;
      }
    }
    Component translated = TextTranslations.translate(message, Locale.US);
    event.getMatch().callEvent(new MatchPlayerDeathBroadcastEvent(event, translated));
    Bukkit.getConsoleSender()
        .sendMessage(
            ComponentSerializer.parse(GsonComponentSerializer.INSTANCE.serialize(translated)));
  }
}
