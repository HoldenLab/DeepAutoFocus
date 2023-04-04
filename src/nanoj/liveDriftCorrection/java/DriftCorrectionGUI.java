package nanoj.liveDriftCorrection.java;

import mmcorej.DeviceType;
import org.micromanager.internal.MMStudio;
//import org.micromanager.internal.MainFrame;
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
import java.text.DecimalFormat;

public class DriftCorrectionGUI{

    // Static and final objects
    private static final String defaultConfigFileLocation = "Default Drift Correction File";
    private static final String defaultDriftFileLocation =
            System.getProperty("user.home")+File.separator+"drift.csv";
    private static final int defaultXPosition = 200;
    private static final int defaultYPosition = 200;

    // Flags and names
    private static final String FRAME_NAME = "ImLock";
    private static final String START = "3. Start!";
    private static final String STOP = "STOP!";
    private static final String STOP_LIVE = "Stop Live";
    private static final String CONTROL = "Control";
    private static final String CONFIGURATION = "Configuration";
    private static final String ADVANCED = "Advanced";
    private static final String CORRECTION_THREAD_NAME = "Drift Correction Thread";
    private static final String HARDWARE_THREAD_NAME = "Hardware Manager Thread";
    private static final String BG_SUB_THREAD_NAME = "Background subtraction Thread";
    private static final String CAL_THREAD_NAME = "Calibration Thread";
    private static final String INIT = "Initial load";

    // Preference keys
    private static final String X_POSITION = "xPos";
    private static final String Y_POSITION = "yPos";
    private static final String SHOW_LATEST = "showLive";
    private static final String SHOW_MAP = "showMap";
    private static final String SHOW_PLOT = "showPlot";
    private static final String SAVE_PLOTS = "savePlots";
    private static final String TUNING_MODE = "TuningMode";
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
    private static final String Zp = "Zp"; // 190404 kw
    private static final String Zi = "Zi"; // 220110 kw
    private static final String Lp = "Lp"; // 220118 JE
    private static final String Li = "Li"; // 220118 JE
    private static final String BIAS = "Bias"; // 221208 JE
    private static final String ADZ = "Bias"; // 230404 JE
    private static final String LDZ = "Bias"; // 230404 JE
    private static final String PERIOD = "period";
    private static final String REF_UPDATE = "refUpdate";
    private static final String BOUNDS = "bounds";
    private static final String CAL_STEP_SIZE = "calStepSize"; // 201223 kw
    private static final String CAL_SCALING = "calScaling";
    private static final String CAL_ANGLE = "calAngle";
    private static final String CAL_FLIPPING_X = "calFlipping X";
    private static final String CAL_FLIPPING_Y = "calFlipping Y";
    private static final String CAL_SWITCHING_XY = "calSwitching XY";
    private static final String BACK_STEP_SIZE = "backStepSize"; // 201223 kw

    // Preference defaults
    private static final String BACK_STEP_SIZE_DEFAULT = "100"; // microns 201223 kw
    private static final String CAL_STEP_SIZE_DEFAULT = "5"; // microns 201223 kw
    private static final String EXPOSURE_TIME_DEFAULT = "500"; // milliseconds
    private static final String ROI_SIZE_DEFAULT = "2000";
    private static final String EDGE_CLIP_DEFAULT = "0";
    private static final String STEP_SIZE_DEFAULT = "1500"; // nanometers
    private static final String Zp_DEFAULT = "0.3"; // 190404 kw
    private static final String Zi_DEFAULT = "0"; // 220118 JE
    private static final String Lp_DEFAULT = "0.1"; // 220118 JE
    private static final String Li_DEFAULT = "0"; // 220118 JE
    private static final String BIAS_DEFAULT = "0"; // 230404 JE
    private static final String ADZ_DEFAULT = "0"; // 230404 JE
    private static final String LDZ_DEFAULT = "10"; // 221208 JE
    private static final String PERIOD_DEFAULT = "500"; // milliseconds
    private static final String REF_UPDATE_DEFAULT = "0"; // minutes
    private static final String BOUNDS_DEFAULT = "10"; // microns
    private static final double CAL_DEFAULT = 0;
    DecimalFormat df = new DecimalFormat("#.##");

    // Labels
    private static final String SNAP_IMAGE_LABEL = "Snap";
    private static final String STREAM_IMAGES_BUTTON_LABEL = "Live";
    private static final String SHOW_LATEST_BUTTON_LABEL = "Show latest image";
    private static final String SHOW_MAP_BUTTON_LABEL = "Show cross correlation map";
    private static final String SHOW_DRIFT_PLOT_LABEL = "Show drift plots";
    private static final String SAVE_DRIFT_PLOT_LABEL = "Save drift data to file";
    private static final String TUNING_MODE_LABEL = "Tuning Mode";
    private static final String SAVE_DRIFT_LOCATION_LABEL = "Save Location";
    private static final String CONFIGURE_LABEL = "Select configuration file";
    private static final String LOAD_LABEL = "Load Hardware";
    private static final String UNLOAD_LABEL = "Unload Hardware";
    private static final String GET_BG_LABEL = "1. Get background image";
    private static final String CLEAR_BG_LABEL = "Clear background reference";
    private static final String CALIBRATE_LABEL = "2. Calibrate pixel size";
    private static final String EXPOSURE_TIME_LABEL = "Exposure time for each frame (ms)";
    private static final String ROI_BOX_LABEL = "Maximum ROI to analyse";
    private static final String EDGE_CLIP_LABEL = "Pixels to trim from the edges";
    private static final String STEP_SIZE_LABEL = "Step size (nm) for Z correction";
    private static final String Zp_LABEL = "Zp (Axial proportional gain)"; //190404 kw
    private static final String Zi_LABEL = "Zi (Axial integral gain)"; // 220118 JE
    private static final String Lp_LABEL = "Lp (Lateral proportional gain)"; // 220118 JE
    private static final String Li_LABEL = "Li (Lateral integral gain)"; // 220118 JE
    private static final String BIAS_LABEL = "Lateral gain Bias (+ >> x, - >> y)"; // 221208 JE
    private static final String ADZ_LABEL = "Axial Dead Zone (nm)"; // 230404 JE
    private static final String LDZ_LABEL = "Lateral Dead Zone (nm)"; // 230404 JE
    private static final String PERIOD_LABEL = "Time between corrections (ms)";
    private static final String REF_UPDATE_LABEL = "Time between reference updates (min)";
    private static final String BOUNDS_LABEL = "Maximum translation (um)";
    private static final String SEPARATE_STAGES_LABEL = "Separate XY stage devices?";
    private static final String SAVE_DIALOG_TITLE = "File name and location (date and time added automatically)";
    private static final String CAMERA_LIST_LABEL = "Camera";
    private static final String XY_STAGE_LIST_LABEL = "XY Stage";
    private static final String X_STAGE_LIST_LABEL = "X Axis Stage";
    private static final String Y_STAGE_LIST_LABEL = "Y Axis Stage";
    private static final String FOCUS_STAGE_LIST_LABEL = "Z Stage / Focus Device";
    private static final String BACK_STEP_SIZE_LABEL = "Background subtraction step size (um)"; // 201223 kw
    private static final String CAL_STEP_SIZE_LABEL = "Calibration step size (um)"; // 201223 kw
    private static final String SCALING = "Scale: ";
    private static final String SCALE_UNITS = " nm/pixel";
    private static final String ANGLE = "Angle: ";
    private static final String FLIP_X = "Flip X axis: ";
    private static final String FLIP_Y = "Flip Y axis: ";
    private static final String SWITCH_XY = "Switch X and Y axes: ";
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
    public static final String ROI_NOT_DIV_4 =
            "The requested ROI size is not divisible by 4.";
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
    //private static MainFrame mainFrame;

    // Non-static objects
    private Preferences preferences = Preferences.userRoot().node(this.getClass().getName());
    private DriftCorrection driftCorrection;
    //private ConfiguratorDlg2 configurator;

    private DriftCorrectionHardware hardwareManager = new DriftCorrectionHardware(getConfig());
    private DriftCorrectionData driftData = new DriftCorrectionData(hardwareManager);
    private DriftCorrectionProcess processor = new DriftCorrectionProcess(driftData);
    private DriftCorrectionCalibration calibrator = new DriftCorrectionCalibration(hardwareManager, processor, driftData);
    private DriftCorrectionBGSub bgSub = new DriftCorrectionBGSub(hardwareManager, processor, driftData);
    private GetBackgroundListener bgSubListener = new GetBackgroundListener();
    private CalibrationButtonListener calListener = new CalibrationButtonListener();
    private StartButtonListener startButtonListener = new StartButtonListener();
    private SeparateXYStagesListener separateXYStagesListener = new SeparateXYStagesListener();
    private HardwareSettingsListener hardwareSettingsListener = new HardwareSettingsListener();
    private ConfigurationListener configurationListener = new ConfigurationListener();
    
    // Textbox listeners. 201228 kw
    private ExposureTimeListener exposureTimeListener = new ExposureTimeListener();

    private ArrayList<DeviceList> devices = new ArrayList<DeviceList>();

    //////////////////////// GUI objects
    private JFrame guiFrame = new JFrame(FRAME_NAME);
    private JTabbedPane guiPanel = new JTabbedPane();
    private Dimension controlDimensions = new Dimension(600, 600);
    private Dimension configurationDimensions = new Dimension(300, 900);
    private Dimension advancedDimensions = new Dimension(300, 900);

    // Control Panel objects
    private JToggleButton startButton = new DToggleButton(START, startButtonListener);
    private JToggleButton snapImageButton = new DToggleButton(SNAP_IMAGE_LABEL, new SnapImageListener());
    private JToggleButton streamImagesButton = new DToggleButton(STREAM_IMAGES_BUTTON_LABEL, new StreamImagesListener());
    private JToggleButton bgImageButton = new DToggleButton(GET_BG_LABEL, bgSubListener);
    private JToggleButton calButton = new DToggleButton(CALIBRATE_LABEL, calListener);
    private JToggleButton saveLocationButton = new DToggleButton(SAVE_DRIFT_LOCATION_LABEL, new SaveLocationListener());
    private JComboBox correctionModes = new DComboBox(new CorrectionModeListener(), CORRECTION_MODE);
    private JCheckBox showLatestButton = new DCheckBox(SHOW_LATEST_BUTTON_LABEL, new ShowLatestListener(), SHOW_LATEST);
    private JCheckBox showMapButton = new DCheckBox(SHOW_MAP_BUTTON_LABEL, new ShowMapButtonListener(), SHOW_MAP);
    private JCheckBox showDriftPlotButton = new DCheckBox(SHOW_DRIFT_PLOT_LABEL, new ShowDriftPlotListener(), SHOW_PLOT);
    private JCheckBox saveDriftPlotButton = new DCheckBox(SAVE_DRIFT_PLOT_LABEL, new SaveToggleListener(), SAVE_PLOTS);
    private JCheckBox TuningModeButton = new DCheckBox(TUNING_MODE_LABEL, new TuneToggleListener(), TUNING_MODE);

    // Configuration Panel objects
    private JButton selectConfigurationFileButton = new DButton(CONFIGURE_LABEL, configurationListener);
    private JButton loadConfigurationButton = new DButton(LOAD_LABEL, configurationListener);
    private JButton unloadConfigurationButton = new DButton(UNLOAD_LABEL, configurationListener);
    private JTextField backgroundStepSizeBox = new DTextField(BACK_STEP_SIZE, BACK_STEP_SIZE_DEFAULT);
    private JTextField calibrationStepSizeBox = new DTextField(CAL_STEP_SIZE, CAL_STEP_SIZE_DEFAULT);
    private JLabel calibrationScalingLabel = new DLabel(SCALING + df.format(preferences.getDouble(CAL_SCALING, CAL_DEFAULT)+70) + SCALE_UNITS);
    private JLabel calibrationAngleLabel = new DLabel(ANGLE + df.format(preferences.getDouble(CAL_ANGLE, CAL_DEFAULT)));
    private JLabel calibrationFlip_XLabel = new DLabel(FLIP_X + preferences.getBoolean(CAL_FLIPPING_X, false));
    private JLabel calibrationFlip_YLabel = new DLabel(FLIP_Y + preferences.getBoolean(CAL_FLIPPING_Y, false));
    private JLabel calibrationSwitch_XYLabel = new DLabel(SWITCH_XY + preferences.getBoolean(CAL_SWITCHING_XY, false));
    private DeviceList cameraList = new DeviceList(DeviceType.CameraDevice, CAMERA);
    private JCheckBox separateXYStages = new DCheckBox(SEPARATE_STAGES_LABEL, separateXYStagesListener, SEPARATE);
    private JTextField exposureTimeBox = new DTextField(EXPOSURE_TIME, EXPOSURE_TIME_DEFAULT, exposureTimeListener);
    private JTextField roiBox = new DTextField(ROI_SIZE, ROI_SIZE_DEFAULT);
    private JTextField edgeClipBox = new DTextField(EDGE_CLIP, EDGE_CLIP_DEFAULT);
    private JTextField stepSizeBox = new DTextField(STEP_SIZE, STEP_SIZE_DEFAULT);
    private JTextField ZpBox = new DTextField(Zp, Zp_DEFAULT); // 190404 kw
    private JTextField ZiBox = new DTextField(Zi, Zi_DEFAULT); // 220118 JE
    private JTextField LpBox = new DTextField(Lp, Lp_DEFAULT); // 220118 JE
    private JTextField LiBox = new DTextField(Li, Li_DEFAULT); // 220118 JE   
    private JTextField periodBox = new DTextField(PERIOD, PERIOD_DEFAULT);
    private JTextField refUpdateBox = new DTextField(REF_UPDATE, REF_UPDATE_DEFAULT);
    private JTextField boundsLimitBox = new DTextField(BOUNDS, BOUNDS_DEFAULT);
    private DeviceList focusDeviceList = new DeviceList(DeviceType.StageDevice, Z_STAGE);
    private DeviceList xyStageList = new DeviceList(DeviceType.XYStageDevice, XY_STAGE);
    private JLabel xyStageListLabel = new DLabel(XY_STAGE_LIST_LABEL);
    private DeviceList xStageList = new DeviceList(DeviceType.StageDevice, X_STAGE);
    private JLabel xStageListLabel = new DLabel(X_STAGE_LIST_LABEL);
    private DeviceList yStageList = new DeviceList(DeviceType.StageDevice, Y_STAGE);
    private JLabel yStageListLabel = new DLabel(Y_STAGE_LIST_LABEL);
    
    // Adcanced Panel objects
    private JTextField BiasBox = new DTextField(BIAS, BIAS_DEFAULT); // 221208 JE
    private JTextField ADZBox = new DTextField(ADZ, ADZ_DEFAULT); // 230404 JE
    private JTextField LDZBox = new DTextField(LDZ, ADZ_DEFAULT); // 230404 JE
    

    //////////////////////// Instance

    public static final DriftCorrectionGUI INSTANCE = new DriftCorrectionGUI();

    //////////////////////// Constructor

    // Constructor is private to make the GUI a singleton
    private DriftCorrectionGUI() {
        //mainFrame = MMStudio.getFrame();

        // Load calibration from non-volatile storage
        hardwareManager.setCalibration(DriftCorrectionCalibration.createCalibration(preferences.getDouble(CAL_SCALING, CAL_DEFAULT +70), preferences.getDouble(CAL_ANGLE, CAL_DEFAULT)));
        driftData.setflipX(preferences.getBoolean(CAL_FLIPPING_X, false));
        driftData.setflipY(preferences.getBoolean(CAL_FLIPPING_Y, false));
        driftData.setSwitchXY(preferences.getBoolean(CAL_SWITCHING_XY, false));
        
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

        // Make large buttons
        streamImagesButton.setPreferredSize(new Dimension(300,100));
        streamImagesButton.setMaximumSize(new Dimension(300,100));
        streamImagesButton.setFont(new Font("Arial", Font.PLAIN, 30));
        
        bgImageButton.setPreferredSize(new Dimension(300, 100));
        bgImageButton.setMaximumSize(new Dimension(300, 100));
        bgImageButton.setFont(new Font("Arial", Font.PLAIN, 20));
        
        calButton.setPreferredSize(new Dimension(300, 100));
        calButton.setMaximumSize(new Dimension(300, 100));
        calButton.setFont(new Font("Arial", Font.PLAIN, 20));

        startButton.setPreferredSize(new Dimension(300,100));
        startButton.setMaximumSize(new Dimension(300,100));
        startButton.setFont(new Font("Arial", Font.PLAIN, 30));

        // Trigger the separate stages listener to display the GUI accordingly
        separateXYStagesListener.separateStages(separateXYStages.isSelected());

        // Give the list of correction modes to the comboBox and set to what was set in the previous session
        correctionModes.setModel(new DefaultComboBoxModel(correctionModesLabels));
        correctionModes.setSelectedIndex(preferences.getInt(CORRECTION_MODE, 0));

        // Create panels
        JPanel controlPanel = new JPanel();
        GroupLayout controlPanelLayout = new GroupLayout(controlPanel); // now using GroupLayout 201229 kw
        controlPanel.setLayout(controlPanelLayout);
        controlPanelLayout.setAutoCreateGaps(true);
        controlPanelLayout.setAutoCreateContainerGaps(true);

        JPanel configurationPanel = new JPanel();
        configurationPanel.setLayout(new BoxLayout(configurationPanel,BoxLayout.PAGE_AXIS));
        
        JPanel advancedPanel = new JPanel();
        advancedPanel.setLayout(new BoxLayout(advancedPanel,BoxLayout.PAGE_AXIS));

        // Add GUI elements to CONTROL panel
        controlPanelLayout.setHorizontalGroup(
            controlPanelLayout.createSequentialGroup()
                .addGroup(controlPanelLayout.createParallelGroup()
                    .addComponent(snapImageButton)
                    .addComponent(streamImagesButton)
                    .addComponent(correctionModes)
                    .addComponent(showLatestButton)
                    .addComponent(showMapButton)
                    .addComponent(showDriftPlotButton)
                    .addComponent(TuningModeButton)
                    .addComponent(saveDriftPlotButton)
                    .addComponent(saveLocationButton))
                .addGroup(controlPanelLayout.createParallelGroup()
                    .addComponent(bgImageButton)
                    .addComponent(calButton)
                    .addComponent(startButton))
        );
        controlPanelLayout.setVerticalGroup(
            controlPanelLayout.createSequentialGroup()
                .addGroup(controlPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(snapImageButton)
                        .addComponent(bgImageButton))
                .addComponent(streamImagesButton)
                .addGroup(controlPanelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(correctionModes)
                    .addComponent(calButton))
                .addComponent(showLatestButton) 
                .addComponent(showMapButton)
                .addComponent(showDriftPlotButton)
                .addComponent(TuningModeButton)
                .addComponent(saveDriftPlotButton)
                .addGroup(controlPanelLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                        .addComponent(saveLocationButton)
                        .addComponent(startButton))
        );

        // Add GUI elements to CONFIGURATION panel
        configurationPanel.add(selectConfigurationFileButton);
        configurationPanel.add(loadConfigurationButton);
        configurationPanel.add(unloadConfigurationButton);
        configurationPanel.add(new DLabel(CAMERA_LIST_LABEL));
        configurationPanel.add(cameraList);
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
        configurationPanel.add(calibrationScalingLabel);
        configurationPanel.add(calibrationAngleLabel);
        configurationPanel.add(calibrationFlip_XLabel);
        configurationPanel.add(calibrationFlip_YLabel);
        configurationPanel.add(calibrationSwitch_XYLabel);
        configurationPanel.add(new DButton(CLEAR_BG_LABEL, new ClearBackgroundListener()));
        configurationPanel.add(new DLabel("Current Calibration:"));
        configurationPanel.add(calibrationScalingLabel);
        configurationPanel.add(calibrationAngleLabel);
        configurationPanel.add(calibrationFlip_XLabel);
        configurationPanel.add(calibrationFlip_YLabel);
        configurationPanel.add(calibrationSwitch_XYLabel);
        configurationPanel.add(new JSeparator());
        configurationPanel.add(new DLabel(BACK_STEP_SIZE_LABEL)); // 201223 kw
        configurationPanel.add(backgroundStepSizeBox);
        configurationPanel.add(new DLabel(CAL_STEP_SIZE_LABEL)); // 201223 kw
        configurationPanel.add(calibrationStepSizeBox);       
        configurationPanel.add(new DLabel(EXPOSURE_TIME_LABEL));
        configurationPanel.add(exposureTimeBox);
        configurationPanel.add(new DLabel(ROI_BOX_LABEL));
        configurationPanel.add(roiBox);
        configurationPanel.add(new DLabel(EDGE_CLIP_LABEL));
        configurationPanel.add(edgeClipBox);
        configurationPanel.add(new DLabel(STEP_SIZE_LABEL));
        configurationPanel.add(stepSizeBox);
        configurationPanel.add(new DLabel(Zp_LABEL)); //190404 kw
        configurationPanel.add(ZpBox);
        configurationPanel.add(new DLabel(Zi_LABEL)); //220118 JE
        configurationPanel.add(ZiBox);
        configurationPanel.add(new DLabel(Lp_LABEL)); //220118 JE
        configurationPanel.add(LpBox);
        configurationPanel.add(new DLabel(Li_LABEL)); //220118 JE
        configurationPanel.add(LiBox);
        configurationPanel.add(new DLabel(PERIOD_LABEL));
        configurationPanel.add(periodBox);
        configurationPanel.add(new DLabel(REF_UPDATE_LABEL));
        configurationPanel.add(refUpdateBox);
        configurationPanel.add(new DLabel(BOUNDS_LABEL));
        configurationPanel.add(boundsLimitBox);
        
        // Add GUI elements to ADVANCED panel
        advancedPanel.add(new DLabel(BIAS_LABEL)); //220118 JE
        advancedPanel.add(BiasBox);
        advancedPanel.add(new DLabel(ADZ_LABEL)); //230404 JE
        advancedPanel.add(LDZBox);
        advancedPanel.add(new DLabel(LDZ_LABEL)); //230404 JE
        advancedPanel.add(ADZBox);

        // Build GUI frame with the panels
        guiPanel.addTab(CONTROL, controlPanel);
        guiPanel.addTab(CONFIGURATION, configurationPanel);
        guiPanel.addTab(ADVANCED, advancedPanel);
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
        driftData.setTuneMode(preferences.getBoolean(TUNING_MODE, false));
        driftData.setDataFile(new File(preferences.get(DRIFT_FILE_LOCATION, defaultDriftFileLocation)));

        // Create the drift correction object
        driftCorrection = new DriftCorrection(hardwareManager, driftData, processor);
        driftCorrection.setCorrectionMode(correctionModes.getSelectedIndex());
        driftCorrection.addObserver(startButtonListener);
        
        // Create bg subtraction object
        bgSub = new DriftCorrectionBGSub(hardwareManager, processor, driftData);
        bgSub.addObserver(bgSubListener);
        
        // Create cal object
        calibrator = new DriftCorrectionCalibration(hardwareManager, processor, driftData);
        calibrator.addObserver(calListener);

        Thread driftCorrectionThread = new Thread(driftCorrection, CORRECTION_THREAD_NAME);
        driftCorrectionThread.start();

        Thread hardwareManagerThread = new Thread(hardwareManager, HARDWARE_THREAD_NAME);
        hardwareManagerThread.start();
        
        Thread bgCorrThread = new Thread(bgSub, BG_SUB_THREAD_NAME);
        bgCorrThread.start();
        
        Thread calThread = new Thread(calibrator, CAL_THREAD_NAME);
        calThread.start();

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
        
        //calibrator.setBackgroundStep(Double.parseDouble(backgroundStepSizeBox.getText())); // 201223 kw
        bgSub.setBackgroundStep(Double.parseDouble(backgroundStepSizeBox.getText()));
        calibrator.setStep(Double.parseDouble(calibrationStepSizeBox.getText()));
        
        driftCorrection.setZp((double) (Double.parseDouble(ZpBox.getText()))); //190404 kw
        driftCorrection.setZi((double) (Double.parseDouble(ZiBox.getText()))); //220118 JE
        driftCorrection.setLp((double) (Double.parseDouble(LpBox.getText()))); //220118 JE
        driftCorrection.setLi((double) (Double.parseDouble(LiBox.getText()))); //220118 JE
        driftCorrection.setBias((double) (Double.parseDouble(BiasBox.getText()))); //221208 JE
        driftCorrection.setADZ((double) (Double.parseDouble(ADZBox.getText()))); //230404 JE
        driftCorrection.setLDZ((double) (Double.parseDouble(LDZBox.getText()))); //230404 JE
        driftCorrection.setSleep((long) (Double.parseDouble(periodBox.getText())));
        driftCorrection.setRefUpdate((double) (Double.parseDouble(refUpdateBox.getText())));
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
        //hardwareManager.snap();

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
        
        DTextField(String preferenceKey, String def, ActionListener listener){ // 201228 kw
            super();
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(dimension);
            key = preferenceKey;
            setText(preferences.get(key, def));
            hardwareSettingsListener.addObserver(this);
            addActionListener(listener);
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
                hardwareManager.setStreamImages(false); // Stop live streaming if starting to correct 220518JE
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
                hardwareManager.setStreamImages(streamImagesButton.isSelected()); // return streaming to control of its button 220518JE
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
            if (streamImagesButton.isSelected() & !startButton.isSelected()) {
                try {
                    giveHardwareSettings();
                } catch (Exception e1) {
                    ReportingUtils.showError(e1, HARDWARE_CONNECTION_ERROR);
                }
                driftData.setShowLatest(true);
                streamImagesButton.setText(STOP_LIVE);
            }
            else {
                driftData.setShowLatest(showLatestButton.isSelected());
                streamImagesButton.setText(STREAM_IMAGES_BUTTON_LABEL);
            }
            if (!startButton.isSelected()) hardwareManager.setStreamImages(streamImagesButton.isSelected());
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
    
    class TuneToggleListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            driftData.setTuneMode(TuningModeButton.isSelected());
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

    class GetBackgroundListener implements ActionListener, Observer {

        @Override
        public void actionPerformed(ActionEvent e) {
            hardwareManager.setStreamImages(false); // Stop live streaming if starting to get background 220518JE
            if (bgImageButton.isSelected()) {
                
                // Give the hardware manager the current settings
                try {
                    giveHardwareSettings();
                } catch (Exception e1) {
                    bgSub.runAcquisition(false);
                    bgImageButton.setSelected(false);
                    ReportingUtils.showError(e1, HARDWARE_CONNECTION_ERROR);

                }
                
                //calibrator.setBackgroundStep(Double.parseDouble(backgroundStepSizeBox.getText()));
                bgSub.setBackgroundStep(Double.parseDouble(backgroundStepSizeBox.getText()));
            
                ReportingUtils.showMessage(procedure_will_move_stage);
                try {
                    // Start bg image acquisition 201231 kw
                    bgSub.runAcquisition(true);
                
                    /*driftData.setBackgroundImage(
                        processor.clip(calibrator.obtainBackgroundImage())
                    );
                    driftData.setLatestImage(driftData.getBackgroundImage());
                    ReportingUtils.showMessage(procedure_succeeded);*/
                    
                } catch (Exception e1) {
                    ReportingUtils.showError(e1, HARDWARE_CONNECTION_ERROR);
                }
            }
            else {
                bgSub.runAcquisition(false);
            }
        }
        @Override
        public void update(Observable o, Object arg) {
            // If the bg sub routine stops for some reason, change back the start button
            if (!bgSub.isRunning()) {
                ReportingUtils.showMessage("" + arg);
                bgImageButton.setSelected(false);
                hardwareManager.setStreamImages(streamImagesButton.isSelected()); // return streaming to control of its button 220518JE
            }
        }
    }

    class ClearBackgroundListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            driftData.setBackgroundImage(null);
        }
    }

    class CalibrationButtonListener implements ActionListener, Observer {

        @Override
        public void actionPerformed(ActionEvent e) {
            hardwareManager.setStreamImages(false); // Stop camera streaming if starting calibration 220518JE
            if (calButton.isSelected()) {
                calibrator.setStep(Double.parseDouble(calibrationStepSizeBox.getText()));
                
                ReportingUtils.showMessage(procedure_will_move_stage);

                try {
                    giveHardwareSettings();
                    
                    calibrator.runAcquisition(true);
                    
                } catch (Exception e1) {
                    ReportingUtils.showError(e1, CALIBRATION_ERROR);
                }
            }
            else {
                calibrator.runAcquisition(false);
                hardwareManager.setStreamImages(streamImagesButton.isSelected()); // return streaming to control of its button 220518JE
            }
        }
        @Override
        public void update(Observable o, Object arg) {
            // If the cal sub routine stops for some reason, change back the button
            if (!calibrator.isRunning()) {
                ReportingUtils.showMessage("" + arg);
                calButton.setSelected(false);
                hardwareManager.setStreamImages(streamImagesButton.isSelected()); // return streaming to control of its button 220518JE
            }
            // end of cal routine - import values to GUI 201231 kw
            else {
                double scale = calibrator.getScale();
                double angle = calibrator.getAngle();
                boolean flip_X = driftData.getflipX();
                boolean flip_Y = driftData.getflipY();
                boolean switch_XY = driftData.getSwitchXY();
                preferences.putDouble(CAL_SCALING, scale);
                preferences.putDouble(CAL_ANGLE, angle);
                preferences.putBoolean(CAL_FLIPPING_X, flip_X);
                preferences.putBoolean(CAL_FLIPPING_Y, flip_Y);
                preferences.putBoolean(CAL_SWITCHING_XY, switch_XY);
                String scaling = SCALING + df.format(scale*1000);
                String angling = ANGLE + df.format(angle);
                String flipping_X = FLIP_X + flip_X;
                String flipping_Y = FLIP_Y + flip_Y;
                String switching_XY = SWITCH_XY + switch_XY;
                calibrationScalingLabel.setText(scaling + " nm/pixel");
                calibrationAngleLabel.setText(angling + " radians");
                calibrationFlip_XLabel.setText(flipping_X);
                ReportingUtils.showMessage( procedure_succeeded
                        + "\n" + scaling + " nm/pixel\n" + angling + " radians" + "\n" + flipping_X + "\n" + flipping_Y + "\n" + switching_XY);
                hardwareManager.setStreamImages(streamImagesButton.isSelected()); // return streaming to control of its button 220518JE
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
                } else if (Integer.parseInt(roiBox.getText()) > hardwareManager.getCameraHeight() || Integer.parseInt(roiBox.getText()) > hardwareManager.getCameraWidth()) {
                    ReportingUtils.showError(ROI_LARGER_THAN_CAMERA);
                    int size = Math.min(hardwareManager.getCameraHeight(), hardwareManager.getCameraWidth());
                    roiBox.setText("" + size);
                } else if (Integer.parseInt(roiBox.getText()) % 4 != 0 && Integer.parseInt(roiBox.getText()) > 10) { // new error in case not divisible by 4 (also needs to be >10 or other error triggered). 210205 kw
                    ReportingUtils.showMessage(ROI_NOT_DIV_4);
                    int newROIsize = (int) (Math.ceil(Integer.parseInt(roiBox.getText())/4)) * 4;
                    roiBox.setText("" + newROIsize);
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
    
    class ExposureTimeListener implements ActionListener {
            
        @Override
        public void actionPerformed(ActionEvent e){
            hardwareManager.setExposureTime(Double.parseDouble(exposureTimeBox.getText()));
        }
    }

    class RememberPositionListener implements WindowListener {

        @Override
        public void windowOpened(WindowEvent e) { }

        @Override
        public void windowClosing(WindowEvent e) {
            driftCorrection.runAcquisition(false); // 190404 kw
            startButton.setSelected(false);
            startButton.setText(START);
            streamImagesButton.setSelected(false);
            driftData.setShowLatest(showLatestButton.isSelected());
            hardwareManager.setStreamImages(streamImagesButton.isSelected());
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