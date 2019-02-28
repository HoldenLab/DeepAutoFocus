package nanoj.liveDriftCorrection.java;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.ZProjector;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import org.apache.commons.lang.ArrayUtils;
import org.micromanager.internal.utils.ReportingUtils;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Observable;

public class DriftCorrectionHardware extends Observable implements Runnable {
    CMMCore driftCore = new CMMCore();
    CMMCore mainCore;
    private String[] loadedDevices;
    private String camera;
    private String stageXY;
    private boolean useMainXYAxis;
    private String stageXaxis;
    private boolean useMainXAxis;
    private String stageYAxis;
    private boolean useMainYAxis;
    private String focusDevice;
    private boolean useMainFocus;
    private String configFileLocation;
    private FloatProcessor latestImage;
    private AffineTransform calibration;

    // Settings
    private boolean alive = true;
    private boolean streamImages = false;
    private boolean loaded = false;
    private int cameraWidth = Integer.MAX_VALUE;
    private int cameraHeight = Integer.MAX_VALUE;
    private double exposureTime = 10; // Milliseconds
    private double stepSize = 0.050; // microns
    private boolean separate = false;

    // Preference keys and defaults
    public static final String SEPARATE = "separateXYStages";
    public static final String EXPOSURE_TIME = "exposureTime";

    // Flags
    public static final String LOADED = "Loaded devices";
    public static final String NEW_STREAM_IMAGE = "New Stream Image";

    // Error messages
    public static final String X_STAGE_NOT_SET = "Singe axis X stage device not set.";
    public static final String Y_STAGE_NOT_SET = "Singe axis Y stage device not set.";
    public static final String XY_STAGE_NOT_SET = "XY stage device not set.";
    public static final String Z_STAGE_NOT_SET = "Z axis (Focus) stage device not set.";
    public static final String CAMERA_NOT_SET = "Camera device not set.";
    public static final String CALIBRATION_NOT_SET = "Calibration not set. Please set before moving XY stage.";
    public static final String CORE_DEVICE_NOT_SET = "Asynchronous Micro-Manager instance not set.";
    public static final String CONFIG_NOT_SET = "Config file not set.";
    public static final String ERROR_LOADING_DEVICES = "Error while loading devices";
    public static final String EXPOSURE_TIME_ERROR = "Error while setting exposure time.";
    public static final String ACQUISITION_ERROR = "Error while acquiring image.";

    DriftCorrectionHardware(String config) {
        try {
            driftCore.setCircularBufferMemoryFootprint(32);
            driftCore.clearROI();
        } catch (Exception e) {
            ReportingUtils.showError(e);
        }
        setConfigFileLocation(config);
    }
    
    public void moveFocusStageInSteps(int target) throws Exception {
        moveFocusStage(target*getStepSize());
    }
    
    public void moveFocusStage(double target) throws Exception {
        if (target == 0.0) return;

        CMMCore core;

        if (!useMainFocus && getDriftCore() == null)
            throw new NullPointerException(CORE_DEVICE_NOT_SET);

        else if (getFocusDevice() == null)
            throw new NullPointerException(Z_STAGE_NOT_SET);

        else {
            if (useMainFocus) core = mainCore;
            else core = driftCore;
            core.waitForDevice(focusDevice); // In case there the user moved the stage while processing
            core.setRelativePosition(focusDevice, target);
            core.waitForDevice(getFocusDevice());
        }
    }

    // Move XY stage in microns without having to know if the XY stage is a single or two devices
    public void moveXYStage(Point2D.Double target) throws Exception {
        moveXYStage(target.x, target.y);
    }

    // Move XY stage in microns without having to know if the XY stage is a single or two devices
    public void moveXYStage(double xTarget, double yTarget) throws Exception {
        if (xTarget == 0 && yTarget == 0) return;

        CMMCore core;
        CMMCore Xcore;
        CMMCore Ycore;

        if ( !isSeparateXYStages() )
            if (getXYStage() == null) throw new NullPointerException(XY_STAGE_NOT_SET);
            else {
                if (useMainXYAxis) core = mainCore;
                else core = driftCore;
                core.waitForDevice(stageXY);
                core.setRelativeXYPosition(stageXY, xTarget, yTarget);
                core.waitForDevice(getXYStage());
            }
        else {
            if (getSeparateXYStages()[0] == null) throw new NullPointerException(X_STAGE_NOT_SET);
            else if (getSeparateXYStages()[1] == null) throw new NullPointerException(Y_STAGE_NOT_SET);
            else {
                if (useMainXAxis) Xcore = mainCore;
                else Xcore = driftCore;
                if (useMainYAxis) Ycore = mainCore;
                else Ycore = driftCore;
                Xcore.waitForDevice(stageXaxis); // In case there the user moved the stage while processing
                Xcore.setRelativePosition(stageXaxis, xTarget);
                Ycore.waitForDevice(stageYAxis); // In case there the user moved the stage while processing
                Ycore.setRelativePosition(stageYAxis, yTarget);
                Xcore.waitForDevice(getSeparateXYStages()[0]);
                Ycore.waitForDevice(getSeparateXYStages()[1]);
            }
        }
    }

    public String[] getLoadedDevices() {
        String[] mainDevices = {};

        if (mainCore !=null) mainDevices = mainCore.getLoadedDevices().toArray();
        String[] driftDevices = driftCore.getLoadedDevices().toArray();

        loadedDevices = (String[]) ArrayUtils.addAll(mainDevices, driftDevices);
        return loadedDevices;
    }

    public String[] getLoadedDevicesOfType(DeviceType type) {
        String[] mainDevices = {};

        if (!(type == DeviceType.CameraDevice) && mainCore != null) {
            mainDevices = mainCore.getLoadedDevicesOfType(type).toArray();
        }
        String[] driftDevices = driftCore.getLoadedDevicesOfType(type).toArray();

        return (String[]) ArrayUtils.addAll(mainDevices, driftDevices);
    }

    public CMMCore determineCore(String label, DeviceType type) {
        if (mainCore == null) return driftCore;
        String[] mainDevices = mainCore.getLoadedDevicesOfType(type).toArray();
        if (mainDevices.length == 0) return driftCore;

        String[] driftDevices = driftCore.getLoadedDevicesOfType(type).toArray();
        for (String device: driftDevices)
            if (label.equals(device)) return driftCore;

        return mainCore;
    }

    // Load devices from the predetermined configuration file
    public void load() throws NullPointerException {
        if (getConfigFileLocation() == null) throw new NullPointerException(CONFIG_NOT_SET);
        else if (getDriftCore() == null) throw new NullPointerException(CORE_DEVICE_NOT_SET);
        else {
            try {
                driftCore.unloadAllDevices();
                driftCore.loadSystemConfiguration(getConfigFileLocation());
                driftCore.initializeAllDevices();
            } catch (Exception e) {
                ReportingUtils.showError(e, ERROR_LOADING_DEVICES);
            }
            getLoadedDevices();
            this.setChanged();
            this.notifyObservers(LOADED);
        }
        loaded = true;
    }

    public void unLoad() {
        try {
            driftCore.unloadAllDevices();
        } catch (Exception e) {
            ReportingUtils.showError(e, ERROR_LOADING_DEVICES);
        }
        loaded = false;
    }
    
    public void snap(){
        FloatProcessor newProcessor = null;
        try {
            if (getCamera() == null) throw new NullPointerException(CAMERA_NOT_SET);
            else {
                driftCore.snapImage();
                driftCore.waitForDevice(getCamera());
                int width = (int) driftCore.getImageWidth();
                int height = (int) driftCore.getImageHeight();

                Object pixels = driftCore.getImage();

                if (driftCore.getImageBitDepth() == 8) {
                    byte[] array = (byte[]) pixels;
                    if (pixels == null || (array.length != (width*height)))
                        return;
                }
                else {
                    short[] array = (short[]) pixels;
                    if (pixels == null || (array.length != (width*height)))
                        return;
                }

                if (driftCore.getImageBitDepth() == 8) {
                    ByteProcessor bp = new ByteProcessor(width, height);
                    bp.setPixels(pixels);
                    newProcessor = bp.convertToFloatProcessor();
                } else {
                    ShortProcessor bp = new ShortProcessor(width, height);
                    bp.setPixels(pixels);
                    newProcessor = bp.convertToFloatProcessor();
                }

                latestImage = newProcessor;

            }
        } catch (Exception e) {
            ReportingUtils.showError(e, ACQUISITION_ERROR);
        }

        latestImage = newProcessor;
    }
    
    public Point2D.Double convertPixelsToMicrons(Point2D.Double originalCoordinates) throws NullPointerException{
        // For a given pixel coordinate corresponding to a relative translation
        // return the stage coordinates in microns required to achieve the
        // pixel coordinate movement.
        if (calibration == null)
            throw new NullPointerException(CALIBRATION_NOT_SET);
        double[] original = new double[] {
                originalCoordinates.getX(),
                originalCoordinates.getY()
        };
        double[] output = new double[]{0,0};
        calibration.transform(original,0,output,0,1);
        return new Point2D.Double(output[0], output[1]);
    }

    public double convertPixelsToMicrons(double value) throws NullPointerException{
        if (calibration == null)
            throw new NullPointerException(CALIBRATION_NOT_SET);
        return value*calibration.getScaleX();
    }

    public void run() {
        while(isAlive()) {
            if (isStreamImages()) {
                try {
                    snap();
                    setChanged();
                    notifyObservers(NEW_STREAM_IMAGE);
                } catch (Exception e) {
                    ReportingUtils.showError(e, ACQUISITION_ERROR);
                }
            }
            try {
                Thread.sleep(1); // Otherwise the compiler seems to think the loop is empty
            } catch (InterruptedException e) {
                ReportingUtils.logError(e);
            }
        }
    }

    //////////////////////////// Getters/Setters
    
    public FloatProcessor getImage() {
        return latestImage;
    }

    public CMMCore getDriftCore() {
        return driftCore;
    }

    public void setDriftCore(CMMCore driftCore) {
        this.driftCore = driftCore;
    }

    public CMMCore getMainCore() {
        return mainCore;
    }

    public void setMainCore(CMMCore mainCore) {
        this.mainCore = mainCore;
    }

    public String getCamera() {
        return camera;
    }
    
    public int getCameraWidth() {
        return cameraWidth;
    }
    
    public int getCameraHeight(){
        return cameraHeight;
    }
    
    // pixelSize in Microns
    public void setCamera(String camera) throws Exception {
        CMMCore core = determineCore(camera, DeviceType.CameraDevice);
        core.setCameraDevice(camera);
        if (core.getDeviceLibrary(camera).equals("DemoCamera")) {
            int depth = 16;
            core.setProperty(camera,"BitDepth", depth);
        }
        this.camera = camera;
        driftCore.clearROI();
        this.cameraWidth = (int) driftCore.getImageWidth();
        this.cameraHeight = (int) driftCore.getImageHeight();
    }
    
    public AffineTransform getCalibration() {
        return calibration;
    }
    
    public void setCalibration(AffineTransform calibration) { this.calibration = calibration; }

    public void setROI(int xPositionOfTopLeftCorner, int yPositionOfTopLeftCorner, int width, int height) throws Exception{
        driftCore.clearROI();
        driftCore.setROI(xPositionOfTopLeftCorner, yPositionOfTopLeftCorner, width, height);
    }
    
    public Rectangle getROI() throws Exception {
        return driftCore.getROI();
    }
    
    public double getExposureTime() {
        return exposureTime;
    }

    public void setExposureTime(double exposureTime) {
        this.exposureTime = exposureTime;
        try {
            driftCore.setExposure(getExposureTime());
        } catch (Exception e) {
            ReportingUtils.showError(e, EXPOSURE_TIME_ERROR);
        }
    }
    
    public double getStepSize() {
        return stepSize;
    }
    
    public void setStepSize(double stepSize) {
        this.stepSize = stepSize;
    }

    public String getXYStage() {
        return stageXY;
    }
    
    public void setXYStage(String stageXY) {
        if (determineCore(stageXY, DeviceType.XYStageDevice).equals(mainCore)) useMainXYAxis = true;
        else useMainXYAxis = false;

        this.stageXY = stageXY;
    }

    public String[] getSeparateXYStages() {
        return new String[]{stageXaxis, stageYAxis};
    }

    public void setSeparateXYStageDevices(String xAxis, String yAxis) {
        if (determineCore(xAxis, DeviceType.StageDevice).equals(mainCore)) useMainXAxis = true;
        else useMainXAxis = false;

        if (determineCore(yAxis, DeviceType.StageDevice).equals(mainCore)) useMainYAxis = true;
        else useMainYAxis = false;

        this.stageXaxis = xAxis;
        this.stageYAxis = yAxis;
    }
    
    public String getFocusDevice() {
        return focusDevice;
    }
    
    public void setFocusDevice(String focusDevice) {
        if (determineCore(focusDevice, DeviceType.StageDevice).equals(mainCore)) useMainFocus = true;
        else useMainFocus = false;

        this.focusDevice = focusDevice;
    }

    public String getConfigFileLocation() {
        return configFileLocation;
    }

    public void setConfigFileLocation(String configFileLocation) {
        this.configFileLocation = configFileLocation;
    }

    // Get whether or not to treat the X and Y axis as separate stage devices
    public boolean isSeparateXYStages() {
        return separate;
    }

    // Set whether or not to treat the X and Y axis as separate stage devices
    public void setIsSeparateXYStages(boolean truth) {
        this.separate = truth;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isStreamImages() {
        return streamImages;
    }

    public void setStreamImages(boolean liveImage) {
        this.streamImages = liveImage;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
