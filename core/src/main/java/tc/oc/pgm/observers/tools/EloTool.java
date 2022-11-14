package tc.oc.pgm.observers.tools;

import com.google.common.collect.Lists;
import java.util.List;
import net.kyori.text.Component;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.menu.InventoryMenu;
import tc.oc.pgm.menu.InventoryMenuItem;
import tc.oc.pgm.util.text.TextTranslations;

public class EloTool implements InventoryMenuItem {
  private static final String TRANSLATION_KEY = "setting.elo.";

  @Override
  public Component getName() {
    return TranslatableComponent.of("setting.elo");
  }

  @Override
  public ChatColor getColor() {
    return ChatColor.GREEN;
  }

  @Override
  public List<String> getLore(MatchPlayer player) {
    Component joinState =
        TranslatableComponent.of(
            hasElo(player) ? "misc.on" : "misc.off",
            hasElo(player) ? TextColor.GREEN : TextColor.RED);
    Component lore = TranslatableComponent.of(TRANSLATION_KEY + "lore", TextColor.GRAY, joinState);
    return Lists.newArrayList(TextTranslations.translateLegacy(lore, player.getBukkit()));
  }

  @Override
  public Material getMaterial(MatchPlayer player) {
    return Material.EMERALD;
  }

  @Override
  public void onInventoryClick(InventoryMenu menu, MatchPlayer player, ClickType clickType) {
    toggleElo(player);
    menu.refreshWindow(player);
  }

  public boolean hasElo(MatchPlayer player) {
    return player.getSettings().getValue(SettingKey.ELO) == SettingValue.ELO_ON;
  }

  public void toggleElo(MatchPlayer player) {
    player
        .getSettings()
        .setValue(SettingKey.ELO, hasElo(player) ? SettingValue.ELO_OFF : SettingValue.ELO_ON);
  }
}
