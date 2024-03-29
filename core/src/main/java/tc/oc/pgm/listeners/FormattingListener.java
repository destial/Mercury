package tc.oc.pgm.listeners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tc.oc.pgm.core.Core;
import tc.oc.pgm.core.CoreLeakEvent;
import tc.oc.pgm.destroyable.Destroyable;
import tc.oc.pgm.destroyable.DestroyableDestroyedEvent;
import tc.oc.pgm.goals.Contribution;
import tc.oc.pgm.util.LegacyFormatUtils;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextFormatter;
import tc.oc.pgm.wool.PlayerWoolPlaceEvent;

public class FormattingListener implements Listener {
  @EventHandler(priority = EventPriority.HIGHEST)
  public void playerWoolPlace(final PlayerWoolPlaceEvent event) {
    if (!event.getWool().isVisible()) return;
    event
        .getMatch()
        .sendMessage(
            LegacyFormatUtils.horizontalDivider(
                event.getPlayer().getParty().getColor().asBungee(), 200));
    event
        .getMatch()
        .sendMessage(
            TextComponent.builder()
                .append("   ")
                .append(
                    TranslatableComponent.of(
                        "wool.complete.owned",
                        event.getPlayer().getName(NameStyle.COLOR),
                        event.getWool().getComponentName()))
                .build());
    event
        .getMatch()
        .sendMessage(
            LegacyFormatUtils.horizontalDivider(
                event.getPlayer().getParty().getColor().asBungee(), 200));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void coreLeak(final CoreLeakEvent event) {
    final Core core = event.getCore();
    if (!core.isVisible()) return;
    List<? extends Contribution> sort = new ArrayList<>(core.getContributions());
    ChatColor clr = ChatColor.GRAY;
    for (Contribution entry : sort) {
      clr = entry.getPlayerState().getParty().getColor().asBungee();
      break;
    }
    event.getMatch().sendMessage(LegacyFormatUtils.horizontalDivider(clr, 200));
    event
        .getMatch()
        .sendMessage(
            TextComponent.builder()
                .append("   ")
                .append(
                    TranslatableComponent.of(
                        "core.complete.owned",
                        formatContributions(core.getContributions(), false),
                        core.getComponentName().color(core.getOwner().getName().color()),
                        core.getOwner().getName()))
                .build());
    event.getMatch().sendMessage(LegacyFormatUtils.horizontalDivider(clr, 200));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void destroyableDestroyed(final DestroyableDestroyedEvent event) {
    Destroyable destroyable = event.getDestroyable();
    if (!destroyable.isVisible()) return;
    List<? extends Contribution> sort = new ArrayList<>(destroyable.getContributions());
    ChatColor clr = ChatColor.GRAY;

    for (Contribution entry : sort) {
      clr = entry.getPlayerState().getParty().getColor().asBungee();
      break;
    }

    event.getMatch().sendMessage(LegacyFormatUtils.horizontalDivider(clr, 200));
    event
        .getMatch()
        .sendMessage(
            TextComponent.builder()
                .append("   ")
                .append(
                    TranslatableComponent.of(
                        "destroyable.complete.owned",
                        formatContributions(destroyable.getContributions(), false),
                        destroyable
                            .getComponentName()
                            .color(destroyable.getOwner().getName().color()),
                        destroyable.getOwner().getName()))
                .build());
    event.getMatch().sendMessage(LegacyFormatUtils.horizontalDivider(clr, 200));
  }

  private Component formatContributions(
      Collection<? extends Contribution> contributions, boolean showPercentage) {
    List<? extends Contribution> sorted = new ArrayList<>(contributions);
    sorted.sort(
        (o1, o2) -> {
          return Double.compare(o2.getPercentage(), o1.getPercentage()); // reverse
        });

    List<Component> contributors = new ArrayList<>();
    boolean someExcluded = false;
    for (Contribution entry : sorted) {
      if (entry.getPercentage() > 0.2) { // 20% necessary to be included
        if (showPercentage) {
          contributors.add(
              TranslatableComponent.of(
                  "objective.credit.percentage",
                  entry.getPlayerState().getName(NameStyle.COLOR),
                  TextComponent.of(Math.round(entry.getPercentage() * 100), TextColor.AQUA)));
        } else {
          contributors.add(entry.getPlayerState().getName(NameStyle.COLOR));
        }
      } else {
        someExcluded = true;
      }
    }

    final Component credit;
    if (contributors.isEmpty()) {
      credit =
          TranslatableComponent.of(
              someExcluded ? "objective.credit.many" : "objective.credit.unknown");
    } else {
      if (someExcluded) {
        contributors.add(
            TranslatableComponent.of("objective.credit.etc")); // Some contributors < 20%
      }
      credit = TextFormatter.list(contributors, TextColor.WHITE);
    }

    return TextComponent.builder().append(credit).build();
  }
}
