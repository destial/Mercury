package tc.oc.pgm.command.graph;

import app.ashcon.intake.argument.CommandArgs;
import app.ashcon.intake.argument.MissingArgumentException;
import app.ashcon.intake.bukkit.parametric.provider.BukkitProvider;
import java.lang.annotation.Annotation;
import java.util.List;
import org.bukkit.command.CommandSender;

public final class StringsProvider implements BukkitProvider<String[]> {

  @Override
  public boolean isProvided() {
    return true;
  }

  @Override
  public String[] get(CommandSender sender, CommandArgs args, List<? extends Annotation> list) {
    String[] arguments = new String[args.size()];
    int i = 0;
    while (args.hasNext()) {
      try {
        arguments[i++] = args.next();
      } catch (MissingArgumentException e) {
        e.printStackTrace();
      }
    }
    return arguments;
  }
}
