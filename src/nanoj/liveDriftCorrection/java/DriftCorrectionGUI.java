package nanoj.liveDriftCorrection.java;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.MainFrame;
//import org.micromanager.internal.conf2.ConfiguratorDlg2;
import org.micromanager.internal.utils.ReportingUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.prefs.Preferences;

public class DriftCorrectionGUI{

    // Static and final objects
    private static final String defaultConfigFileLocation = "Default Drift Correction File";
    private static final String defaultDriftFileLocation =
            System.getProperty("user.home")+File.separator+"drift.csv";
    private static final int defaultXPosition = 200;
    private static final int defaultYPosition = 200;

    // Flags and names
    private static final String FRAME_NAME = "NanoJ Live Drift Correction";
    private static final String START = "Start!";
    private static final String STOP = "STOP!";
    private static final String CONTROL = "Control";
    private static final String CONFIGURATION = "Configuration";
    private static final String CORRECTION_THREAD_NAME = "Drift Correction Thread";
    private static final String HARDWARE_THREAD_NAME = "Hardware Manager Thread";
    private static final String INIT = "Initial load";

    // Preference keys
    private static final String X_POSITION = "xPos";
    private static final String Y_POSITION = "yPos";
    private static final String SHOW_LATEST = "showLive";
    private static final String SHOW_MAP = "showMap";
    private static final String SHOW_PLOT = "showPlot";
    private static final String SAVE_PLOTS = "savePlots";
    private static final String CORRECTION_MODE = "correctionMode";
    private static final String CAMERA = "camera";
    private static final String SEPARATE = "separateXYStages";
    private static final String Z_STAGE = "zStage";
    private static final String XY_STAGE = "xyStage";
    private static final String X_STAGE = "xStage";
    private static final String Y_STAGE = "yStage";
    private static final String DRIFT_FILE_LOCATION = "driftFileLocation";
    private static final String CONFIG_FILE_LOCATION = "driftConfigFileLocation";
    private static final String EXPOSURE_TIME = "exposureTime";
    private static final String ROI_SIZE = "roiSize";
    private static final String EDGE_CLIP = "edgeClip";
    private static final String STEP_SIZE = "stepSize";
    private static final String PERIOD = "period";
    private static final String BOUNDS = "bounds";
    private static final String CAL_SCALING = "calScaling";
    private static final String CAL_ANGLE = "calAngle";
    private static final String CAL_FLIPPING = "calFlipping";

    // Preference defaults
    private static final String EXPOSURE_TIME_DEFAULT = "33"; // milliseconds
    private static final String ROI_SIZE_DEFAULT = "512";
    private static final String EDGE_CLIP_DEFAULT = "30";
    private static final String STEP_SIZE_DEFAULT = "150"; // nanometers
    private static final String PERIOD_DEFAULT = "0.5"; // seconds
    private static final String BOUNDS_DEFAULT = "3"; // microns
    private static final double CAL_DEFAULT = -1;

    // Labels
    private static final String SNAP_IMAGE_LABEL = "Snap";
    private static final String STREAM_IMAGES_BUTTON_LABEL = "Live";
    private static final String SHOW_LATEST_BUTTON_LABEL = "Show latest image";
    private static final String SHOW_MAP_BUTTON_LABEL = "Show cross correlation map";
    private static final String SHOW_DRIFT_PLOT_LABEL = "Show drift plots";
    private static final String SAVE_DRIFT_PLOT_LABEL = "Save drift data to file";
    private static final String SAVE_DRIFT_LOCATION_LABEL = "Save Location";
    private static final String CONFIGURE_LABEL = "Select configuration file";
    private static final String LOAD_LABEL = "Load Hardware";
    private static final String UNLOAD_LABEL = "Unload Hardware";
    private static final String GET_BG_LABEL = "Get background reference image.";
    private static final String CLEAR_BG_LABEL = "Clear background reference.";
    private static final String CALIBRATE_LABEL = "Calibrate pixel size";
    private static final String EXPOSURE_TIME_LABEL = "Exposure time for each frame in milliSec.";
    private static final String ROI_BOX_LABEL = "Maximum ROI to analyse";
    private static final String EDGE_CLIP_LABEL = "How many pixels to trim from the edges";
    private static final String STEP_SIZE_LABEL = "Step size (nm) for Z correction";
    private static final String PERIOD_LABEL = "Time between corrections in seconds";
    private static final String BOUNDS_LABEL = "Maximum translation (microns)";
    private static final String SEPARATE_STAGES_LABEL = "Separate XY stage devices?";
    private static final String SAVE_DIALOG_TITLE = "File name and location (date and time added automatically)";
    private static final String CAMERA_LIST_LABEL = "Camera";
    private static final String XY_STAGE_LIST_LABEL = "XY Stage";
    private static final String X_STAGE_LIST_LABEL = "X Axis Stage";
    private static final String Y_STAGE_LIST_LABEL = "Y Axis Stage";
    private static final String FOCUS_STAGE_LIST_LABEL = "Z Stage / Focus Device";
    private static final String SCALING = "Scale: ";
    private static final String ANGLE = "Angle: ";
    private static final String FLIP = "Flip X: ";
    private static final String [] correctionModesLabels = new String[]{
        "XYZ Drift Correction",
                "Z Drift Correction",
                "XY Drift Correction"};

    // Error flags
    public static final String ROI_SIZE_TOO_SMALL =
            "The requested ROI size is too small.\n" +
                    "It should be at least twice the edge clip value plus 10.";
    public static final String ROI_LARGER_THAN_CAMERA =
            "The requested ROI size is too large.\n" +
                    "It should only be as large as the smallest dimension of the camera (width or height).";
    public static final String HARDWARE_NOT_SET = "Hardware not set, please configure first.";
    public static final String HARDWARE_NOT_LOADED = "Hardware not loaded, please load first.";
    public static final String WIZARD_LAUNCHING_ERROR = "Error during launch of Drift Hardware Configuration Wizard!";
    public static final String WIZARD_CREATION_ERROR = "Error during creation of Drift Hardware Configuration Wizard!";
    public static final String CONFIGURATION_FILE_ERROR = "Error during configuration file loading!";
    public static final String SET_HARDWARE_ERROR = "Error while trying to define hardware configuration in core!";
    public static final String HARDWARE_CONNECTION_ERROR = "Error while talking with hardware.";
    public static final String CALIBRATION_ERROR = "Error during calibration!";
    public static final String SNAP_ERROR = "Error while taking image.";

    // Messages
    private static final String procedure_will_move_stage = "" +
            "The calibration procedure moves the stage.\n" +
            "Please ensure there are no obstructions to stage movement.";
    private static final String procedure_succeeded = "The procedure has succeeded!";

    // Static objects
    private static MainFrame mainFrame;

    // Non-static objects
    private Preferences preferences = Preferences.userRoot().node(this.getClass().getName());
    private DriftCorrection driftCorrection;
    //private ConfiguratorDlg2 configurator;

    private DriftCorrectionHardware hardwareManager = new DriftCorrectionHardware(getConfig());
    private DriftCorrectionData driftData = new DriftCorrectionData();
    private DriftCorrectionProcess processor = new DriftCorrectionProcess(driftData);
    private DriftCorrectionCalibration calibrator = new DriftCorrectionCalibration(hardwareManager, processor);
    private StartButtonListener startButtonListener = new StartButtonListener();
    private SeparateXYStagesListener separateXYStagesListener = new SeparateXYStagesListener();
    private HardwareSettingsListener hardwareSettingsListener = new HardwareSettingsListener();
    private ConfigurationListener configurationListener = new ConfigurationListener();

    private ArrayList<DeviceList> devices = new ArrayList<DeviceList>();

    //////////////////////// GUI objects
    private JFrame guiFrame = new JFrame(FRAME_NAME);
    private JTabbedPane guiPanel = new JTabbedPane();
    private Dimension controlDimensions = new Dimension(300, 400);
    private Dimension configurationDimensions = new Dimension(300, 710);

    // Control Panel objects
    private JToggleButton startButton = new DToggleButton(START, startButtonListener);
    private JToggleButton streamImagesButton = new DToggleButton(STREAM_IMAGES_BUTTON_LABEL, new StreamImagesListener());
    private JComboBox correctionModes = new DComboBox(new CorrectionModeListener(), CORRECTION_MODE);
    private JCheckBox showLatestButton = new DCheckBox(SHOW_LATEST_BUTTON_LABEL, new ShowLatestListener(), SHOW_LATEST);
    private JCheckBox showMapButton = new DCheckBox(SHOW_MAP_BUTTON_LABEL, new ShowMapButtonListener(), SHOW_MAP);
    private JCheckBox showDriftPlotButton = new DCheckBox(SHOW_DRIFT_PLOT_LABEL, new ShowDriftPlotListener(), SHOW_PLOT);
    private JCheckBox saveDriftPlotButton = new DCheckBox(SAVE_DRIFT_PLOT_LABEL, new SaveToggleListener(), SAVE_PLOTS);

    // Configuration Panel objects
    private JButton selectConfigurationFileButton = new DButton(CONFIGURE_LABEL, configurationListener);
    private JButton loadConfigurationButton = new DButton(LOAD_LABEL, configurationListener);
    private JButton unloadConfigurationButton = new DButton(UNLOAD_LABEL, configurationListener);
    private JLabel calibrationScalingLabel = new DLabel(SCALING + preferences.getDouble(CAL_SCALING, CAL_DEFAULT));
    private JLabel calibrationAngleLabel = new DLabel(ANGLE + preferences.getDouble(CAL_ANGLE, CAL_DEFAULT));
    private JLabel calibrationFlipLabel = new DLabel(FLIP + preferences.getBoolean(CAL_FLIPPING, false));
    private DeviceList cameraList = new DeviceList(DeviceType.CameraDevice, CAMERA);
    private JCheckBox separateXYStages = new DCheckBox(SEPARATE_STAGES_LABEL, separateXYStagesListener, SEPARATE);
    private JTextField exposureTimeBox = new DTextField(EXPOSURE_TIME, EXPOSURE_TIME_DEFAULT);
    private JTextField roiBox = new DTextField(ROI_SIZE, ROI_SIZE_DEFAULT);
    private JTextField edgeClipBox = new DTextField(EDGE_CLIP, EDGE_CLIP_DEFAULT);
    private JTextField stepSizeBox = new DTextField(STEP_SIZE, STEP_SIZE_DEFAULT);
    private JTextField periodBox = new DTextField(PERIOD, PERIOD_DEFAULT);
    private JTextField boundsLimitBox = new DTextField(BOUNDS, BOUNDS_DEFAULT);
    private DeviceList focusDeviceList = new DeviceList(DeviceType.StageDevice, Z_STAGE);
    private DeviceList xyStageList = new DeviceList(DeviceType.XYStageDevice, XY_STAGE);
    private JLabel xyStageListLabel = new DLabel(XY_STAGE_LIST_LABEL);
    private DeviceList xStageList = new DeviceList(DeviceType.StageDevice, X_STAGE);
    private JLabel xStageListLabel = new DLabel(X_STAGE_LIST_LABEL);
    private DeviceList yStageList = new DeviceList(DeviceType.StageDevice, Y_STAGE);
    private JLabel yStageListLabel = new DLabel(Y_STAGE_LIST_LABEL);

    //////////////////////// Instance

    public static final DriftCorrectionGUI INSTANCE = new DriftCorrectionGUI();

    //////////////////////// Constructor

    // Constructor is private to make the GUI a singleton
    private DriftCorrectionGUI() {
        mainFrame = MMStudio.getFrame();

        // Load calibration from non-volatile storage
        hardwareManager.setCalibration(DriftCorrectionCalibration.createCalibration(
                preferences.getDouble(CAL_SCALING, CAL_DEFAULT),
                preferences.getDouble(CAL_ANGLE, CAL_DEFAULT),
                preferences.getBoolean(CAL_FLIPPING, false)
        ));

        // Add Listeners
        guiFrame.addWindowListener(new RememberPositionListener());
        guiPanel.addChangeListener(new PanelListener());
        hardwareManager.addObserver(new LiveStreamer());
        separateXYStages.addActionListener(hardwareSettingsListener);

        // Define GUI object settings
        guiFrame.setPreferredSize(controlDimensions);
        guiFrame.setLocation(
            preferences.getInt(X_POSITION, defaultXPosition),
            preferences.getInt(Y_POSITION, defaultYPosition)
        );

        // Make Start Button HUGE
        startButton.setPreferredSize(new Dimension(300,150));
        startButton.setMaximumSize(new Dimension(300,150));
        startButton.setFont(new Font("Arial", Font.PLAIN, 30));

        // Trigger the separate stages listener to display the GUI accordingly
        separateXYStagesListener.separateStages(separateXYStages.isSelected());

        // Give the list of correction modes to the comboBox and set to what was set in the previous session
        correctionModes.setModel(new DefaultComboBoxModel(correctionModesLabels));
        correctionModes.setSelectedIndex(preferences.getInt(CORRECTION_MODE, 0));

        // Create panels
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel,BoxLayout.Y_AXIS));

        JPanel configurationPanel = new JPanel();
        configurationPanel.setLayout(new BoxLayout(configurationPanel,BoxLayout.PAGE_AXIS));

        // Add GUI elements to CONTROL panel
        controlPanel.add(startButton);
        controlPanel.add(new DButton(SNAP_IMAGE_LABEL, new SnapImageListener()));
        controlPanel.add(streamImagesButton);
        controlPanel.add(correctionModes);
        controlPanel.add(showLatestButton);
        controlPanel.add(showMapButton);
        controlPanel.add(showDriftPlotButton);
        controlPanel.add(saveDriftPlotButton);
        controlPanel.add(new DButton(SAVE_DRIFT_LOCATION_LABEL, new SaveLocationListener()));

        // Add GUI elements to CONFIGURATION panel
        configurationPanel.add(selectConfigurationFileButton);
        configurationPanel.add(loadConfigurationButton);
        configurationPanel.add(unloadConfigurationButton);
        configurationPanel.add(calibrationScalingLabel);
        configurationPanel.add(calibrationAngleLabel);
        configurationPanel.add(calibrationFlipLabel);
        configurationPanel.add(new DButton(CALIBRATE_LABEL, new CalibrationButtonListener()));
        configurationPanel.add(new DButton(GET_BG_LABEL, new GetBackgroundListener()));
        configurationPanel.add(new DButton(CLEAR_BG_LABEL, new ClearBackgroundListener()));
        configurationPanel.add(new DLabel(CAMERA_LIST_LABEL));
        configurationPanel.add(cameraList);
        configurationPanel.add(new DLabel(EXPOSURE_TIME_LABEL));
        configurationPanel.add(exposureTimeBox);
        configurationPanel.add(new DLabel(ROI_BOX_LABEL));
        configurationPanel.add(roiBox);
        configurationPanel.add(new DLabel(EDGE_CLIP_LABEL));
        configurationPanel.add(edgeClipBox);
        configurationPanel.add(new DLabel(STEP_SIZE_LABEL));
        configurationPanel.add(stepSizeBox);
        configurationPanel.add(new DLabel(PERIOD_LABEL));
        configurationPanel.add(periodBox);
        configurationPanel.add(new DLabel(BOUNDS_LABEL));
        configurationPanel.add(boundsLimitBox);
        configurationPanel.add(Box.createRigidArea(new Dimension(1,10)));
        configurationPanel.add(new DLabel(FOCUS_STAGE_LIST_LABEL));
        configurationPanel.add(focusDeviceList);
        configurationPanel.add(Box.createRigidArea(new Dimension(1,10)));
        configurationPanel.add(separateXYStages);
        configurationPanel.add(Box.createRigidArea(new Dimension(1,5)));
        configurationPanel.add(xyStageListLabel);
        configurationPanel.add(xyStageList);
        configurationPanel.add(xStageListLabel);
        configurationPanel.add(xStageList);
        configurationPanel.add(yStageListLabel);
        configurationPanel.add(yStageList);

        // Build GUI frame with the panels
        guiPanel.addTab(CONTROL, controlPanel);
        guiPanel.addTab(CONFIGURATION, configurationPanel);
        guiFrame.setContentPane(guiPanel);
    }

    //////////////////////////// Methods

    // Method required to get a reference to the main app and core
    public void setApp(MMStudio app) {
        //hardwareManager.setMainCore(app.getMMCore());
        hardwareManager.setMainCore(app.core());
    }

    public void initiateThreads() {
        if (!(hardwareManager == null))
            hardwareManager.unLoad();

        // TODO: Does this matter?
        driftData.setShowMap(preferences.getBoolean(SHOW_MAP, false));
        driftData.setShowPlot(preferences.getBoolean(SHOW_PLOT, false));
        driftData.setSavePlots(preferences.getBoolean(SAVE_PLOTS, false));
        driftData.setDataFile(new File(preferences.get(DRIFT_FILE_LOCATION, defaultDriftFileLocation)));

        // Create the drift correction object
        driftCorrection = new DriftCorrection(hardwareManager, driftData, processor);
        driftCorrection.setCorrectionMode(correctionModes.getSelectedIndex());
        driftCorrection.addObserver(startButtonListener);

        Thread driftCorrectionThread = new Thread(driftCorrection, CORRECTION_THREAD_NAME);
        driftCorrectionThread.start();

        Thread hardwareManagerThread = new Thread(hardwareManager, HARDWARE_THREAD_NAME);
        hardwareManagerThread.start();

        // Load devices upon starting the thread if hardware has been previously defined
        try {
            if (configIsValid()) {
                hardwareManager.setConfigFileLocation(getConfig());
                hardwareManager.load();
                // Trigger settings listener so it updates GUI with values that hardware manager just loaded
                hardwareSettingsListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_FIRST, INIT));
                // Give settings to hardware manager
                giveHardwareSettings();
            }
        } catch (Exception e) {
            ReportingUtils.showError(e, HARDWARE_CONNECTION_ERROR);
        }
    }

    public void show() {
        guiFrame.pack();
        guiFrame.setVisible(true);
    }

    // Give HardwareManager the hardware settings
    private void giveHardwareSettings() throws Exception{
        // The ROI estimation will snap a picture, this prevents it from showing
        driftData.setShowLatest(false);

        processor.setEdgeClip(Integer.parseInt(edgeClipBox.getText()));
        hardwareManager.setStepSize(Double.parseDouble(stepSizeBox.getText())/1000);
        driftCorrection.setSleep((long) (Double.parseDouble(periodBox.getText())*1000));
        driftCorrection.setThreshold(Double.parseDouble(boundsLimitBox.getText()));

        hardwareManager.setCamera(cameraList.getSelectedItem().toString());
        hardwareManager.setExposureTime(Double.parseDouble(exposureTimeBox.getText()));

        int roiSize = Integer.parseInt(roiBox.getText());
        int xLocation = 0;
        int yLocation = 0;
        if (hardwareManager.getCameraWidth() > roiSize ) {
            xLocation = (hardwareManager.getCameraWidth() / 2) - (roiSize / 2);
        }
        if (hardwareManager.getCameraHeight() > roiSize) {
            yLocation = (hardwareManager.getCameraHeight() / 2) - (roiSize /2);
        }
        hardwareManager.setROI(xLocation, yLocation, roiSize, roiSize);
        hardwareManager.snap();

        hardwareManager.setFocusDevice(focusDeviceList.getSelectedItem().toString());

        hardwareManager.setSeparateXYStageDevices(
                xStageList.getSelectedItem().toString(),
                yStageList.getSelectedItem().toString());
        hardwareManager.setXYStage(xyStageList.getSelectedItem().toString());

        // Reset the showLatest setting that is set in the checkbox
        driftData.setShowLatest(showLatestButton.isSelected());
    }

    // Calls the Micro-manager Hardware configurator to configure the Drift Correction Hardware.
    /*@Deprecated
    public String makeConfigurationFile(CMMCore core, String location) {
        try {
            // Set cursor to waiting mode
            mainFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            // Unload all devices to be able to reload in the configuration wizard
            hardwareManager.getDriftCore().unloadAllDevices();
            // Create new configuration wizard object
            configurator = new ConfiguratorDlg2(core, location);
        } catch (Exception e) {
            ReportingUtils.showError(e, WIZARD_CREATION_ERROR);
        } finally {
            // Reset cursor
            mainFrame.setCursor(Cursor.getDefaultCursor());
        }

        if (configurator == null) {
            ReportingUtils.showError(WIZARD_LAUNCHING_ERROR);
            return "";
        }

        // Make wizard visible
        configurator.setVisible(true);
        // Once user has configured the config file, return it's location
        return configurator.getFileName();
    }*/

    private void chooseFile(String key, String def, String description, String extensions) {
        File savePath = new File(preferences.get(key, def));
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter(description, extensions));
        fileChooser.setDialogTitle(SAVE_DIALOG_TITLE);
        fileChooser.setCurrentDirectory(savePath.getParentFile());
        int returnVal = fileChooser.showSaveDialog(guiFrame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            savePath = fileChooser.getSelectedFile();
            preferences.put(key, savePath.getAbsolutePath());
        }
    }

    private boolean configIsValid() {
        File file = new File(getConfig());
        return ((!getConfig().equals(defaultConfigFileLocation) ||
                getConfig() == null) && file.exists());
    }

    //////////////////////////// Getters / Setters

    // Get configuration file location from the non-volatile preferences storage
    private String getConfig() {
        return preferences.get(CONFIG_FILE_LOCATION, defaultConfigFileLocation);
    }

    private int getCorrectionMode() {
        return correctionModes.getSelectedIndex();
    }

    //////////////////////////// Inner Classes

    class DToggleButton extends JToggleButton {

        DToggleButton(String name, ActionListener listener) {
            super(name);
            setPreferredSize(new Dimension(300,26));
            setMaximumSize(new Dimension(300,26));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            addActionListener(listener);
        }
    }

    class DButton extends JButton {

        DButton(String label, ActionListener listener) {
            super(label);
            setPreferredSize(new Dimension(300,26));
            setMaximumSize(new Dimension(300,26));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            addActionListener(listener);
        }

    }

    class DComboBox extends JComboBox{
        final Dimension dimension = new Dimension(300,26);
        final String key;

        DComboBox(ActionListener listener, String preferenceKey) {
            super();
            key = preferenceKey;
            setMaximumSize(dimension);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            addActionListener(listener);
        }
    }

    class DCheckBox extends JCheckBox implements Observer{
        final String key;

        DCheckBox(String label, ActionListener listener, String preferenceKey) {
            super(label);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            key = preferenceKey;
            setSelected(preferences.getBoolean(key,false));
            hardwareSettingsListener.addObserver(this);
            addActionListener(listener);
            addActionListener(hardwareSettingsListener);
        }

        @Override
        public void update(Observable o, Object arg) {
            preferences.putBoolean(key, this.isSelected());
        }
    }

    class DLabel extends JLabel {

        DLabel(String label) {
            super(label);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
    }

    class DTextField extends JTextField implements Observer{
        final Dimension dimension = new Dimension(60, 26);
        final String key;

        DTextField(String preferenceKey, String def) {
            super();
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(dimension);
            key = preferenceKey;
            setText(preferences.get(key, def));
            hardwareSettingsListener.addObserver(this);
            addActionListener(hardwareSettingsListener);
        }

        @Override
        public void update(Observable o, Object arg) {
            preferences.put(key, this.getText());
        }
    }

    class DeviceList extends JComboBox implements Observer {
        final DeviceType type;
        final String name;
        final Dimension dimension = new Dimension(300,26);

        DeviceList(DeviceType givenType, String name) {
            setMaximumSize(dimension);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            this.name = name;
            this.type = givenType;
            hardwareManager.addObserver(this);
            this.addActionListener(hardwareSettingsListener);
            devices.add(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            this.setModel(new DefaultComboBoxModel(hardwareManager.getLoadedDevicesOfType(type)));
            int storedIndex = preferences.getInt(name, 0);
            if(this.getItemCount() > storedIndex && storedIndex >= 0) {
                this.setSelectedIndex(storedIndex);
            }

        }
    }

    //////////////////////////// Listener classes

    class PanelListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent e) {
            if (guiPanel.getSelectedIndex() == 0) guiFrame.setPreferredSize(controlDimensions);
            else guiFrame.setPreferredSize(configurationDimensions);
            guiFrame.pack();
        }
    }

    class StartButtonListener implements ActionListener, Observer {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (startButton.isSelected()) {
                // Check the hardware has been loaded
                if (!hardwareManager.isLoaded()) {
                    ReportingUtils.showError(HARDWARE_NOT_LOADED);
                    startButton.setSelected(false);
                    return;
                }

                // Check that we have a valid configuration
                if (!configIsValid()) {
                    ReportingUtils.showError(HARDWARE_NOT_SET);
                    startButton.setSelected(false);
                    return;
                }

                // Give the hardware manager the current settings
                try {
                    giveHardwareSettings();
                } catch (Exception e1) {
                    driftCorrection.runAcquisition(false);
                    startButton.setSelected(false);
                    startButton.setText(START);
                    ReportingUtils.showError(e1, HARDWARE_CONNECTION_ERROR);

                }

                // Create the data file if requested
                if(saveDriftPlotButton.isSelected())
                    driftData.createFile(preferences.get(DRIFT_FILE_LOCATION, defaultDriftFileLocation));

                // Start acquisition and change text on the button
                driftCorrection.runAcquisition(true);
                startButton.setText(STOP);
            }
            else {
                // Stop acquisition and change text on the button
                driftCorrection.runAcquisition(false);
                startButton.setText(START);
            }
        }

        @Override
        public void update(Observable o, Object arg) {
            // If the driftCorrection stops for some reason, change back the start button
            if (!driftCorrection.isRunning()) {
                ReportingUtils.showMessage("" + arg);
                startButton.setSelected(false);
                startButton.setText(START);
            }
        }
    }

    class SnapImageListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                giveHardwareSettings();
            } catch (Exception e1) {
                ReportingUtils.showError(e1, HARDWARE_CONNECTION_ERROR);
            }
            driftData.setShowLatest(true);
            try {
                driftCorrection.snapAndProcess();
            } catch (Exception e1) {
                ReportingUtils.showError(e1, SNAP_ERROR);
            }
            driftData.setShowLatest(showLatestButton.isSelected());
        }
    }

    class LiveStreamer implements Observer {

        @Override
        public void update(Observable o, Object arg) {
            if (arg == DriftCorrectionHardware.NEW_STREAM_IMAGE)
                try {
                    driftData.setLatestImage(processor.process(hardwareManager.getImage()));
                } catch (Exception e) {
                    if (!e.getMessage().equals(DriftCorrectionProcess.MISMATCHED_IMAGE_SIZES))
                        ReportingUtils.logError(e);
                }

        }
    }

    class StreamImagesListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (streamImagesButton.isSelected()) {
                try {
                    giveHardwareSettings();
                } catch (Exception e1) {
                    ReportingUtils.showError(e1, HARDWARE_CONNECTION_ERROR);
                }
                driftData.setShowLatest(true);
            }
            else driftData.setShowLatest(showLatestButton.isSelected());
            hardwareManager.setStreamImages(streamImagesButton.isSelected());
        }
    }

    class ShowLatestListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            driftData.setShowLatest(showLatestButton.isSelected());
        }
    }

    class ShowMapButtonListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            driftData.setShowMap(showMapButton.isSelected());
        }
    }

    class ShowDriftPlotListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            driftData.setShowPlot(showDriftPlotButton.isSelected());
        }
    }

    class CorrectionModeListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            preferences.putInt(CORRECTION_MODE, getCorrectionMode());
            if (driftCorrection != null && driftData != null) {
                driftCorrection.setCorrectionMode(getCorrectionMode());
            }
        }
    }

    class SaveToggleListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            driftData.setSavePlots(saveDriftPlotButton.isSelected());
            preferences.putBoolean(SAVE_PLOTS, saveDriftPlotButton.isSelected());
        }
    }

    class SaveLocationListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            String description = "Comma Separated Spreadsheet";
            String extension = "csv";
            chooseFile(DRIFT_FILE_LOCATION, defaultDriftFileLocation, description, extension);
        }
    }

    class ConfigurationListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == selectConfigurationFileButton) {
                try {
                    String description = "Micro-Manager Configuration file";
                    String extension = "cfg";
                    chooseFile(CONFIG_FILE_LOCATION, getConfig(), description, extension);
                    unloadConfiguration();
                    hardwareManager.setConfigFileLocation(getConfig());
                    loadConfiguration();
                } catch (Exception ex) {
                    ReportingUtils.showError(ex, CONFIGURATION_FILE_ERROR);
                    return;
                }
            }

            if (e.getSource() == loadConfigurationButton) {
                loadConfiguration();
            }

            if (e.getSource() == unloadConfigurationButton) {
                unloadConfiguration();
            }
        }

        void loadConfiguration() {
            hardwareManager.load();
            try {
                giveHardwareSettings();
            } catch (Exception e1) {
                ReportingUtils.showError(e1, HARDWARE_CONNECTION_ERROR);
            }
        }

        void unloadConfiguration() {
            if (streamImagesButton.isSelected()) {
                streamImagesButton.setSelected(false);
                hardwareManager.setStreamImages(false);
            }
            hardwareManager.unLoad();
        }
    }

    class GetBackgroundListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            ReportingUtils.showMessage(procedure_will_move_stage);
            try {
                driftData.setBackgroundImage(
                        processor.clip(calibrator.obtainBackgroundImage())
                );
                driftData.setLatestImage(driftData.getBackgroundImage());
                ReportingUtils.showMessage(procedure_succeeded);
            } catch (Exception e1) {
                ReportingUtils.showError(e1, HARDWARE_CONNECTION_ERROR);
            }
        }
    }

    class ClearBackgroundListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            driftData.setBackgroundImage(null);
        }
    }

    class CalibrationButtonListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            ReportingUtils.showMessage(procedure_will_move_stage);
            try {
                if (calibrator.calibrate()) {
                    giveHardwareSettings();
                    hardwareManager.setCalibration(calibrator.getCalibration());
                    double scale = calibrator.getScale();
                    double angle = calibrator.getAngle();
                    boolean flip = calibrator.getFlipX();
                    preferences.putDouble(CAL_SCALING, scale);
                    preferences.putDouble(CAL_ANGLE, angle);
                    preferences.putBoolean(CAL_FLIPPING, flip);
                    String scaling = SCALING + scale;
                    String angling = ANGLE + angle;
                    String flipping = FLIP + flip;
                    calibrationScalingLabel.setText(scaling);
                    calibrationAngleLabel.setText(angling);
                    calibrationFlipLabel.setText(flipping);
                    ReportingUtils.showMessage( procedure_succeeded
                            + "\n" + scaling + "\n" + angling + "\n" + flipping);
                }
            } catch (Exception e1) {
                ReportingUtils.showError(e1, CALIBRATION_ERROR);
            }

        }
    }

    class SeparateXYStagesListener implements ActionListener {

        void separateStages(boolean truth) {
            xyStageListLabel.setVisible(!truth);
            xyStageList.setVisible(!truth);
            xStageListLabel.setVisible(truth);
            xStageList.setVisible(truth);
            yStageListLabel.setVisible(truth);
            yStageList.setVisible(truth);

            hardwareManager.setIsSeparateXYStages(truth);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            separateStages(separateXYStages.isSelected());
            preferences.putBoolean(SEPARATE, separateXYStages.isSelected());
        }
    }

    class HardwareSettingsListener extends Observable implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            // Check that the size of the ROI makes sense
            try {
                if (Integer.parseInt(roiBox.getText()) < (processor.getEdgeClip()*2)+10) {
                    ReportingUtils.showMessage(ROI_SIZE_TOO_SMALL);
                    roiBox.setText("" + (processor.getEdgeClip()*2+10));

                } else if (Integer.parseInt(roiBox.getText()) > hardwareManager.getCameraHeight() ||
                        Integer.parseInt(roiBox.getText()) > hardwareManager.getCameraWidth()) {

                    ReportingUtils.showError(ROI_LARGER_THAN_CAMERA);
                    int size = Math.min(hardwareManager.getCameraHeight(), hardwareManager.getCameraWidth());
                    roiBox.setText("" + size);
                }
            } catch (Exception e1) {
                ReportingUtils.showError(e1, HARDWARE_CONNECTION_ERROR);
            }

            // Notify Observers that settings have changed
            setChanged();
            notifyObservers();

            // Save the options in the device lists
            for (DeviceList list: devices) {
                if (list.getItemCount() > 1)
                    preferences.putInt(list.name, list.getSelectedIndex());
            }
        }
    }

    class RememberPositionListener implements WindowListener {

        @Override
        public void windowOpened(WindowEvent e) { }

        @Override
        public void windowClosing(WindowEvent e) {
            Point position = guiFrame.getLocationOnScreen();
            int xPos = position.x;
            int yPos = position.y;
            preferences.putInt(X_POSITION,xPos);
            preferences.putInt(Y_POSITION,yPos);
        }

        @Override
        public void windowClosed(WindowEvent e) { }

        @Override
        public void windowIconified(WindowEvent e) { }

        @Override
        public void windowDeiconified(WindowEvent e) { }

        @Override
        public void windowActivated(WindowEvent e) { }

        @Override
        public void windowDeactivated(WindowEvent e) { }
    }
}