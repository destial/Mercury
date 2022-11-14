package tc.oc.pgm.tablist;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.serializer.gson.GsonComponentSerializer;
import net.kyori.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.ChatColor;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapTag;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.modules.StatsMatchModule;
import tc.oc.pgm.util.TimeUtils;
import tc.oc.pgm.util.tablist.DynamicTabEntry;
import tc.oc.pgm.util.tablist.TabView;
import tc.oc.pgm.util.text.TextTranslations;

public class MatchFooterTabEntry extends DynamicTabEntry {

  private final Match match;
  private @Nullable Future<?> tickTask;

  public MatchFooterTabEntry(Match match) {
    this.match = match;
  }

  @Override
  public void addToView(TabView view) {
    super.addToView(view);
    if (this.tickTask == null && match.isLoaded()) {
      Runnable tick = MatchFooterTabEntry.this::invalidate;
      this.tickTask =
          match.getExecutor(MatchScope.LOADED).scheduleWithFixedDelay(tick, 0, 1, TimeUnit.SECONDS);
    }
  }

  @Override
  public void removeFromView(TabView view) {
    super.removeFromView(view);
    if (!this.hasViews() && this.tickTask != null) {
      this.tickTask.cancel(true);
      this.tickTask = null;
    }
  }

  private String convert(Component message) {
    return net.md_5.bungee.api.chat.TextComponent.toLegacyText(
        ComponentSerializer.parse(GsonComponentSerializer.INSTANCE.serialize(message)));
  }

  @Override
  public BaseComponent[] getContent(TabView view) {
    TextComponent.Builder content = TextComponent.builder();

    MatchPlayer viewer = match.getPlayer(view.getViewer());
    boolean timeOnly = viewer != null && viewer.isLegacy();

    if (!timeOnly
        && viewer != null
        && viewer.getParty() instanceof Competitor
        && (match.isRunning() || match.isFinished())
        && viewer.getSettings().getValue(SettingKey.STATS).equals(SettingValue.STATS_ON)) {
      content.append(match.needModule(StatsMatchModule.class).getBasicStatsMessage(viewer.getId()));
      content.append(TextComponent.newline());
    }

    Component leftContent = PGM.get().getConfiguration().getLeftTablistText();
    if (leftContent != null && PGM.get().isPAPIEnabled()) {
      String left = convert(leftContent);
      left = PlaceholderAPI.setPlaceholders(view.getViewer(), left);
      leftContent = TextComponent.of(left);
    }

    Component rightContent = PGM.get().getConfiguration().getRightTablistText();
    if (rightContent != null && PGM.get().isPAPIEnabled()) {
      String right = convert(rightContent);
      right = PlaceholderAPI.setPlaceholders(view.getViewer(), right);
      rightContent = TextComponent.of(right);
    }

    if (!timeOnly && leftContent != null) {
      content.append(leftContent.colorIfAbsent(TextColor.WHITE)).append(" - ", TextColor.DARK_GRAY);
    }

    content
        .append(renderSidebarTitle(match.getMap().getTags(), match.getMap().getGamemode()))
        .append(" - ", TextColor.GRAY)
        .append(
            TimeUtils.formatDuration(match.getDuration()),
            this.match.isRunning() ? TextColor.GREEN : TextColor.GOLD);

    if (!timeOnly && rightContent != null) {
      content
          .append(" - ", TextColor.DARK_GRAY)
          .append(rightContent.colorIfAbsent(TextColor.WHITE));
    }

    return TextTranslations.toBaseComponentArray(
        content.colorIfAbsent(TextColor.DARK_GRAY).build(), view.getViewer());
  }

  private static String renderSidebarTitle(
      Collection<MapTag> tags, @Nullable Component gamemodeName) {
    final Component configTitle = PGM.get().getConfiguration().getMatchHeader();
    if (configTitle != null) return LegacyComponentSerializer.legacy().serialize(configTitle);
    if (gamemodeName != null) {
      String customGamemode = LegacyComponentSerializer.legacy().serialize(gamemodeName);
      if (!customGamemode.isEmpty()) return ChatColor.AQUA + customGamemode;
    }

    final List<String> gamemode =
        tags.stream()
            .filter(MapTag::isGamemode)
            .filter(tag -> !tag.isAuxiliary())
            .map(MapTag::getName)
            .collect(Collectors.toList());
    final List<String> auxiliary =
        tags.stream()
            .filter(MapTag::isGamemode)
            .filter(MapTag::isAuxiliary)
            .map(MapTag::getName)
            .collect(Collectors.toList());

    String title = "";

    if (gamemode.size() == 1) {
      title = gamemode.get(0);
    } else if (gamemode.size() >= 2) {
      title = "Objectives";
    }

    if (auxiliary.size() == 1) {
      title += (title.isEmpty() ? "" : " & ") + auxiliary.get(0);
    } else if (gamemode.isEmpty() && auxiliary.size() == 2) {
      title = auxiliary.get(0) + " & " + auxiliary.get(1);
    }

    return ChatColor.AQUA.toString() + ChatColor.BOLD + (title.isEmpty() ? "Match" : title);
  }
}
