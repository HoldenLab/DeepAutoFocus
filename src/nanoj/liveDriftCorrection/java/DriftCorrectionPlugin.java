package nanoj.liveDriftCorrection.java;

import java.awt.event.WindowEvent;
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.scijava.plugin.SciJavaPlugin;
//import org.micromanager.api.MMPlugin;
//import org.micromanager.api.ScriptInterface;

@org.scijava.plugin.Plugin(type = org.micromanager.MenuPlugin.class)
public class DriftCorrectionPlugin implements org.micromanager.MenuPlugin, org.scijava.plugin.SciJavaPlugin {
    static DriftCorrectionGUI driftGui = DriftCorrectionGUI.INSTANCE;

    public static final String menuName = "NanoJ Online Drift Correction";
    public static final String tooltipDescription = "Asynchronous Image-Correlation-based 3D drift correction.";
    public static final String version = "0.5";
    public static final String copyright = "Copyright University College London, 2017";
    private MMStudio app_;

    public void dispose() {}

    /* commented out because ScriptInterface is deprecated in 2.0 -kw 190226
    public void setApp(ScriptInterface app) {
        driftGui.setApp((MMStudio) app);
    }*/

    public void show() {
        driftGui.initiateThreads();
        driftGui.show();
    }

    public String getDescription() { return tooltipDescription; }

    public String getInfo() { return tooltipDescription; }

    public String getVersion() { return version; }

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
