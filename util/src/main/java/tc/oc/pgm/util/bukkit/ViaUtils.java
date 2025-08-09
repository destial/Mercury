package tc.oc.pgm.util.bukkit;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.legacy.bossbar.BossBar;
import com.viaversion.viaversion.api.legacy.bossbar.BossColor;
import com.viaversion.viaversion.api.legacy.bossbar.BossStyle;
import org.bukkit.entity.Player;
import tc.oc.pgm.util.nms.NMSHacks;

public class ViaUtils {
  /** Minecraft 1.7.6 &ndash; 1.7.10 */
  public static final int VERSION_1_7 = 5;
  /** Minecraft 1.8 &ndash; 1.8.9 */
  public static final int VERSION_1_8 = 47;
  /** Minecraft 1.9 &ndash; 1.9.1-pre1 */
  public static final int VERSION_1_9 = 107;

  private static final boolean ENABLED;

  static {
    boolean viaLoaded = false;
    try {
      Class.forName("vom.viaversion.viaversion.api.Via");
      viaLoaded = true;
    } catch (ClassNotFoundException ignored) {
    }
    ENABLED = viaLoaded;
  }

  public static boolean enabled() {
    return ENABLED;
  }

  /**
   * @see <a
   *     href="https://wiki.vg/Protocol_version_numbers">https://wiki.vg/Protocol_version_numbers</a>
   */
  public static int getProtocolVersion(Player player) {
    if (enabled()) {
      return Via.getAPI().getPlayerVersion(player.getUniqueId());
    } else {
      return NMSHacks.getProtocolVersion(player);
    }
  }

  public static boolean isReady(Player player) {
    return !enabled() || Via.getAPI().isInjected(player.getUniqueId());
  }

  public static BossBar createBossBar() {
    return enabled()
        ? Via.getAPI().legacyAPI().createLegacyBossBar("", BossColor.BLUE, BossStyle.SOLID)
        : null;
  }
}
