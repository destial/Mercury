package tc.oc.pgm.controlpoint;

import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.controlpoint.events.ControllerChangeEvent;
import tc.oc.pgm.util.LegacyFormatUtils;
import tc.oc.pgm.util.text.TextFormatter;

public class ControlPointAnnouncer implements Listener {
  private final Match match;

  public ControlPointAnnouncer(Match match) {
    this.match = match;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onOwnerChange(ControllerChangeEvent event) {
    if (event.getControlPoint().isVisible()) {
      if (event.getOldController() != null && event.getNewController() == null) {
        this.match.sendMessage(
            LegacyFormatUtils.horizontalDivider(
                event.getOldController().getColor().asBungee(), 200));
        this.match.sendMessage(
            TextComponent.builder()
                .append("   ")
                .append(event.getOldController().getName())
                .append(" lost ", TextColor.GRAY)
                .append(event.getControlPoint().getName(), TextColor.WHITE)
                .build());
        this.match.sendMessage(
            LegacyFormatUtils.horizontalDivider(
                event.getOldController().getColor().asBungee(), 200));
      } else if (event.getNewController() != null) {
        this.match.sendMessage(
            LegacyFormatUtils.horizontalDivider(
                event.getNewController().getColor().asBungee(), 200));
        this.match.sendMessage(
            TextComponent.builder()
                .append("   ")
                .append(event.getNewController().getName())
                .append(" captured ", TextColor.GRAY)
                .append(
                    event.getControlPoint().getName(),
                    TextFormatter.convert(event.getNewController().getColor()))
                .build());
        this.match.sendMessage(
            LegacyFormatUtils.horizontalDivider(
                event.getNewController().getColor().asBungee(), 200));
      }
    }
  }
}
