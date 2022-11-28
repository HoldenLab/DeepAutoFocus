package nanoj.liveDriftCorrection.java;

//import ij.ImagePlus;
//import ij.ImageStack;
//import ij.plugin.ZProjector;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import mmcorej.DeviceType;
import org.apache.commons.lang.ArrayUtils;
import org.micromanager.internal.utils.ReportingUtils;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Observable;

public class DriftCorrectionHardware extends Observable implements Runnable {
    CMMCore mainCore;
    private String[] loadedDevices;
    private String camera;
    private String stageXY;
    private String trigger; // 221103 JE
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
    public static final String X_STAGE_NOT_SET = "Single axis X stage device not set.";
    public static final String Y_STAGE_NOT_SET = "Single axis Y stage device not set.";
    public static final String XY_STAGE_NOT_SET = "XY stage device not set.";
    public static final String Z_STAGE_NOT_SET = "Z axis (Focus) stage device not set.";
    public static final String CAMERA_NOT_SET = "Camera device not set.";
    public static final String CALIBRATION_NOT_SET = "Calibration not set. Please set before moving XY stage.";
    public static final String CORE_DEVICE_NOT_SET = "Asynchronous Micro-Manager instance not set.";
    public static final String CONFIG_NOT_SET = "Config file not set.";
    public static final String ERROR_LOADING_DEVICES = "Error while loading devices";
    public static final String EXPOSURE_TIME_ERROR = "Error while setting exposure time.";
    public static final String ACQUISITION_ERROR = "Error while acquiring image.";
    
    public void moveFocusStageInSteps(int target) throws Exception {
        moveFocusStage(target*getStepSize());
    }
    
    public void moveFocusStage(double target) throws Exception {
        if (target == 0.0) return;

        CMMCore core = mainCore;
        if (getFocusDevice() == null) throw new NullPointerException(Z_STAGE_NOT_SET);
        else {
            core.waitForDevice(focusDevice); // In case there the user moved the stage while processing
            core.setRelativePosition(focusDevice, target);
            core.waitForDevice(getFocusDevice());
        }
    }

    // Move XY stage in microns without having to know if the XY stage is a single or two devices
    public boolean moveXYStage(Point2D.Double target) throws Exception {
        boolean Success = moveXYStage(target.x, target.y);
        return Success;
    }

    // Move XY stage in microns without having to know if the XY stage is a single or two devices
    public boolean moveXYStage(double xTarget, double yTarget) throws Exception {
        //if (xTarget == 0 && yTarget == 0) return;

        CMMCore core;
        CMMCore Xcore;
        CMMCore Ycore;

        if ( !isSeparateXYStages() )
            if (getXYStage() == null) throw new NullPointerException(XY_STAGE_NOT_SET);
            else {
                core = mainCore;
                core.waitForDevice(stageXY);
                try{
                    core.setRelativeXYPosition(stageXY, xTarget, yTarget);
                    core.waitForDevice(getXYStage());
                }
                catch(Exception e){ // Guards against timeout hard-fails 22106 JE
                    core.stop(stageXY);
                    return(false);
                }
                //core.setRelativeXYPosition(stageXY, 0, 0);
                //core.stop(stageXY); //Designed to make ASI stages use actual position rather than intended position of last move 220922 JE
                //core.waitForDevice(getXYStage());
            }
        else {
            if (getSeparateXYStages()[0] == null) throw new NullPointerException(X_STAGE_NOT_SET);
            else if (getSeparateXYStages()[1] == null) throw new NullPointerException(Y_STAGE_NOT_SET);
            else {
                Xcore = mainCore;
                Ycore = mainCore;
                Xcore.waitForDevice(stageXaxis); // In case there the user moved the stage while processing
                Xcore.setRelativePosition(stageXaxis, xTarget);
                Ycore.waitForDevice(stageYAxis); // In case there the user moved the stage while processing
                Ycore.setRelativePosition(stageYAxis, yTarget);
                Xcore.waitForDevice(getSeparateXYStages()[0]);
                Ycore.waitForDevice(getSeparateXYStages()[1]);
            }
        }
        return(true);
    }

    public String[] getLoadedDevices() {
        loadedDevices = mainCore.getLoadedDevices().toArray();
        return loadedDevices;
    }

    public String[] getLoadedDevicesOfType(DeviceType type) {
        return mainCore.getLoadedDevicesOfType(type).toArray();
    }

    // Load devices from micromanager core
    public void load() throws NullPointerException {
        getLoadedDevices();
        this.setChanged();
        this.notifyObservers(LOADED);
        
        loaded = true;
    }
    
    public void snap(){
        FloatProcessor newProcessor = null;
        try {
            if (getCamera() == null) throw new NullPointerException(CAMERA_NOT_SET);
            else {
                mainCore.waitForDevice(getCamera());
                mainCore.snapImage();
                
                int width = (int) mainCore.getImageWidth();
                int height = (int) mainCore.getImageHeight();

                Object pixels = mainCore.getImage();

                if (mainCore.getImageBitDepth() == 8) {
                    byte[] array = (byte[]) pixels;
                    if (pixels == null || (array.length != (width*height)))
                        return;
                }
                else {
                    short[] array = (short[]) pixels;
                    if (pixels == null || (array.length != (width*height)))
                        return;
                }

                if (mainCore.getImageBitDepth() == 8) {
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
        //return new Point2D.Double(output[0], 0); // why 0? 201228 kw
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
        CMMCore core = mainCore;
        core.setCameraDevice(camera);
        if (core.getDeviceLibrary(camera).equals("DemoCamera")) {
            int depth = 16;
            core.setProperty(camera,"BitDepth", depth);
        }
        this.camera = camera;
        //core.clearROI();
        this.cameraWidth = (int) core.getImageWidth();
        this.cameraHeight = (int) core.getImageHeight();
    }
    
    public String getTrigger() {
        return trigger;
    }
    
    public void setTrigger(String trigger) throws Exception {

        this.trigger = trigger;
    }

    public boolean getTriggerState() throws Exception {
        CMMCore core = mainCore;
        String property = core.getProperty(trigger,"DigitalInput");
        int state =  Integer.parseInt(property);
        return state == 1;
    }
    
    public AffineTransform getCalibration() {
        return calibration;
    }
    
    public void setCalibration(AffineTransform calibration) { this.calibration = calibration; }
    
    public Rectangle getROI() throws Exception {
        return mainCore.getROI();
    }
    
    public double getExposureTime() {
        return exposureTime;
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
        useMainXYAxis = true;

        this.stageXY = stageXY;
    }

    public String[] getSeparateXYStages() {
        return new String[]{stageXaxis, stageYAxis};
    }

    public void setSeparateXYStageDevices(String xAxis, String yAxis) {
        useMainXAxis = true;
        useMainYAxis = true;

        this.stageXaxis = xAxis;
        this.stageYAxis = yAxis;
    }
    
    public String getFocusDevice() {
        return focusDevice;
    }
    
    public void setFocusDevice(String focusDevice) {
        useMainFocus = true;

        this.focusDevice = focusDevice;
    }

    public String getConfigFileLocation() {
        return configFileLocation;
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
