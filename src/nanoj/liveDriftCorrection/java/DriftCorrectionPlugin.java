package nanoj.liveDriftCorrection.java;

import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.MenuPlugin;
import org.scijava.plugin.SciJavaPlugin;
import org.scijava.plugin.Plugin;

@Plugin(type = MenuPlugin.class)
public class DriftCorrectionPlugin implements MenuPlugin, SciJavaPlugin {
    static DriftCorrectionGUI driftGui = DriftCorrectionGUI.INSTANCE;

    public static final String menuName = "NanoJ Online Drift Correction";
    public static final String tooltipDescription = "Asynchronous Image-Correlation-based 3D drift correction.";
    public static final String version = "0.5";
    public static final String copyright = "Copyright University College London, 2017";
    private MMStudio app_;

    public void dispose() {}

    public void show() {
        driftGui.initiateThreads();
        driftGui.show();
    }

    public String getDescription() { return tooltipDescription; }

    public String getInfo() { return tooltipDescription; }

    @Override
    public String getVersion() { return version; }

    @Override
    public String getCopyright() { return copyright; }

    @Override
    public String getSubMenu() {
        return "Beta";
    }

   @Override
    public void onPluginSelected() {
        if (driftGui != null) {
         driftGui.setApp(app_);
         driftGui.initiateThreads();
         driftGui.show();
      }
      if (driftGui == null) {
         //driftGui = new DriftCorrectionGUI();
      }
    }

    @Override
    public void setContext(Studio app) {
        app_ = (MMStudio) app;
    }

    @Override
    public String getName() {
        return menuName;
    }

    @Override
    public String getHelpText() {
        return "Image-based cross-correlation developed by Ricardo Henriques lab";
    }
}
