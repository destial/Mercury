package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.player.MatchPlayer;

public final class LobbyCommand {
  public LobbyCommand() {
    PGM.get().getServer().getMessenger().registerOutgoingPluginChannel(PGM.get(), "BungeeCord");
  }

  @Command(
      aliases = {"lobby", "hub"},
      desc = "Return to main lobby")
  public void lobby(MatchPlayer player) {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(b);
    try {
      out.writeUTF("Connect");
      out.writeUTF("lobby");
    } catch (Exception e) {
      e.printStackTrace();
    }
    player.getBukkit().sendPluginMessage(PGM.get(), "BungeeCord", b.toByteArray());
  }
}
