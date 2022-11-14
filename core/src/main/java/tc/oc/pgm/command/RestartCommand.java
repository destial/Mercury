package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import app.ashcon.intake.parametric.annotation.Default;
import app.ashcon.intake.parametric.annotation.Switch;
import java.time.Duration;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.listeners.ChatDispatcher;
import tc.oc.pgm.restart.RequestRestartEvent;
import tc.oc.pgm.restart.RestartManager;
import tc.oc.pgm.util.chat.Audience;
import tc.oc.pgm.util.chat.PlayerAudience;

public final class RestartCommand {

  @Command(
      aliases = {"restart", "queuerestart", "qr"},
      desc = "Restart the server",
      flags = "f",
      perms = Permissions.STOP)
  public void restart(
      Audience audience,
      Match match,
      @Default("30s") Duration duration,
      @Switch('f') boolean force) {
    RestartManager.queueRestart("Restart requested via /queuerestart command", duration);

    if (force && match.isRunning()) {
      match.finish();
    }
    Component component =
        TranslatableComponent.of("admin.queueRestart.restartingNow", TextColor.GREEN);
    if (match.isRunning()) {
      component = TranslatableComponent.of("admin.queueRestart.restartQueued", TextColor.RED);
    }
    String name =
        audience instanceof PlayerAudience
            ? ((PlayerAudience) audience).getAudience().getName()
            : "Console";
    match.sendMessage(TextComponent.of(name + " has issued a server restart", TextColor.GREEN));
    ChatDispatcher.broadcastAdminChatMessage(component, match);

    match.callEvent(new RequestRestartEvent());
  }
}
