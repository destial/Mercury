package tc.oc.pgm.tablist;

import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import net.md_5.bungee.api.chat.BaseComponent;
import tc.oc.pgm.elo.EloMatchModule;
import tc.oc.pgm.teams.Team;
import tc.oc.pgm.teams.TeamMatchModule;
import tc.oc.pgm.util.tablist.DynamicTabEntry;
import tc.oc.pgm.util.tablist.TabView;
import tc.oc.pgm.util.text.TextFormatter;
import tc.oc.pgm.util.text.TextTranslations;

public class TeamTabEntry extends DynamicTabEntry {

  private final Team team;

  protected TeamTabEntry(Team team) {
    this.team = team;
  }

  @Override
  public BaseComponent[] getContent(TabView view) {
    TextComponent.Builder builder =
        TextComponent.builder()
            .append(String.valueOf(team.getPlayers().size()), TextColor.WHITE)
            .append("/", TextColor.DARK_GRAY)
            .append(String.valueOf(team.getMaxPlayers()), TextColor.GRAY)
            .append(" ")
            .append(
                team.getShortName(), TextFormatter.convert(team.getColor()), TextDecoration.BOLD);
    if (team.getMatch().hasModule(EloMatchModule.class) && TeamMatchModule.USE_ELO_BALANCING) {
      builder.append(TextComponent.of(" (" + ((int) team.getElo()) + ")", TextColor.BLUE));
    }
    Component content = builder.build();
    return TextTranslations.toBaseComponentArray(content, view.getViewer());
  }
}
