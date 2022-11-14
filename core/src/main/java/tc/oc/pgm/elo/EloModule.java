package tc.oc.pgm.elo;

import java.util.logging.Logger;
import org.jdom2.Document;
import org.jetbrains.annotations.Nullable;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapModule;
import tc.oc.pgm.api.map.factory.MapFactory;
import tc.oc.pgm.api.map.factory.MapModuleFactory;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.module.exception.ModuleLoadException;
import tc.oc.pgm.util.xml.InvalidXMLException;

public class EloModule implements MapModule<EloMatchModule> {
  @Nullable
  @Override
  public EloMatchModule createMatchModule(Match match) throws ModuleLoadException {
    return PGM.get().getConfiguration().shouldQueueElo() ? new EloMatchModule(match) : null;
  }

  public static class Factory implements MapModuleFactory<EloModule> {

    @Nullable
    @Override
    public EloModule parse(MapFactory factory, Logger logger, Document doc)
        throws InvalidXMLException {
      if (doc.getRootElement().getChild("disable-elo") == null) return new EloModule();
      return null;
    }
  }
}
