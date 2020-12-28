package nanoj.liveDriftCorrection.java;

import ij.ImageStack;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import nanoj.core.java.image.drift.EstimateShiftAndTilt;
import nanoj.core.java.image.transform.CrossCorrelationMap;
import org.micromanager.internal.utils.ReportingUtils;

import java.awt.geom.Point2D;
import java.util.Observable;

public class DriftCorrection extends Observable implements Runnable {
    private boolean alive = true;
    private boolean runAcquisition = false;
    private DriftCorrectionHardware hardwareManager;
    private DriftCorrectionData driftData;
    private DriftCorrectionProcess processor;

    private int correctionMode = 0;

    private static final int XYZ = 0;
    private static final int Z = 1;
    private static final int XY = 2;

    // Settings
    private long sleep = 200; // time in between loops in milliseconds

    // Stack labels
    private static final String TOP = "Top";
    private static final String MIDDLE = "Middle";
    private static final String BOTTOM = "Bottom";

    // Error messages
    public static final String ACQUISITION_ERROR = "Error during drift correction acquisition.";
    public static final String INTERRUPTION_ERROR = "Drift correction interrupted.";
    public static final String OUT_OF_BOUNDS_ERROR = "Drift correction stopped!\nDetected movement is outside threshold.";
    private static final String procedure_succeeded = "The procedure has succeeded!";

    // Running variables
    private long startTimeStamp;
    private double threshold;
    private double alpha = 0; // 190401 kw
    private double xi_0 = 0;
    private double xi_n = 0;


    public DriftCorrection(DriftCorrectionHardware manager, DriftCorrectionData data, DriftCorrectionProcess processor) {
        hardwareManager = manager;
        driftData = data;
        this.processor = processor;
    }

    @Override
    public void run() {
        try {
            while (itIsAlive()) {
                while (isRunning()) {
                    long startRun = System.currentTimeMillis();
                    // If we've just started, get the reference image
                    if (driftData.getReferenceImage() == null) driftData.setReferenceImage(snapAndProcess());
                    
                    // If we've just started, get the reference stack (190401 kw)
                    if (driftData.getReferenceStack().size() == 0){
                        ImageStack refStack = new ImageStack(
                                driftData.getReferenceImage().getWidth(),
                                driftData.getReferenceImage().getHeight()
                        );
                        // Take picture at current position, filter, clip and add to image stack
                        refStack.addSlice(MIDDLE, snapAndProcess());
                        
                        //if (correctionMode == Z || correctionMode == XYZ) {
                        // Move one stepSize above focus, snap and add to image stack
                        hardwareManager.moveFocusStageInSteps(1);
                        refStack.addSlice(TOP, snapAndProcess(), 0);

                        // Move two stepSizes below the focus, snap and add to image stack
                        hardwareManager.moveFocusStageInSteps(-2);
                        refStack.addSlice(BOTTOM, snapAndProcess());

                        // Move back to original position
                        hardwareManager.moveFocusStageInSteps(1);
                        //}
                        
                        driftData.setReferenceStack(refStack);
                        
                        // added 190401 kw
                        FloatProcessor refSliceBottom = driftData.getReferenceStack().getProcessor(3).convertToFloatProcessor();
                        FloatProcessor refSliceMiddle = driftData.getReferenceStack().getProcessor(2).convertToFloatProcessor();
                        FloatProcessor refSliceTop = driftData.getReferenceStack().getProcessor(1).convertToFloatProcessor();
                    
                        ImageStack refBottomMiddle = CrossCorrelationMap.calculateCrossCorrelationMap(refSliceBottom, driftData.getReferenceStack(), true);
                        ImageStack refTopMiddle = CrossCorrelationMap.calculateCrossCorrelationMap(refSliceTop, driftData.getReferenceStack(), true);
                        ImageStack refMidMid = CrossCorrelationMap.calculateCrossCorrelationMap(refSliceMiddle, driftData.getReferenceStack(), true);

                        FloatProcessor refCCbottomMiddle = refBottomMiddle.getProcessor(2).convertToFloatProcessor();
                        FloatProcessor refCCtopMiddle = refTopMiddle.getProcessor(2).convertToFloatProcessor();
                        FloatProcessor refCCmidMid = refMidMid.getProcessor(2).convertToFloatProcessor();
                    
                        // overriding this alpha for now, just using user input value.
                        //alpha = (2*refCCmidMid.getMax() - refCCtopMiddle.getMax() - refCCbottomMiddle.getMax()) / (2*hardwareManager.getStepSize()); // eq 6 in McGorty et al. 2013
                        xi_0 = (refCCtopMiddle.getMax() - refCCbottomMiddle.getMax()) / refCCmidMid.getMax();
                    }

                    /* Deprecated 190404
                    // This is the stack where all the images are placed. Images will be clipped
                    // so we create the stack with the same dimensions as the reference image.
                    ImageStack stack = new ImageStack(
                            driftData.getReferenceImage().getWidth(),
                            driftData.getReferenceImage().getHeight()
                    );

                    // First, take the three images to test the correlation against.
                    // Each image should be processed to remove background.
                    // We also clip the images as the edges in some cameras can include blooming.
                    // (eg, Hamamatsu Flash4 cropped to 512*512)

                    // Take picture at current position, filter, clip and add to image stack
                    stack.addSlice(MIDDLE, snapAndProcess());

                    if (correctionMode == Z || correctionMode == XYZ) {
                        // Move one stepSize above focus, snap and add to image stack
                        hardwareManager.moveFocusStageInSteps(1);
                        stack.addSlice(TOP, snapAndProcess(), 0);

                        // Move two stepSizes below the focus, snap and add to image stack
                        hardwareManager.moveFocusStageInSteps(-2);
                        stack.addSlice(BOTTOM, snapAndProcess());

                        // Move back to original position
                        hardwareManager.moveFocusStageInSteps(1);
                    }
                    
                    // Calculate the Cross Correlation maps
                    ImageStack resultStack =
                            CrossCorrelationMap.calculateCrossCorrelationMap(
                                    driftData.getReferenceImage(), stack, true);
                    driftData.setResultMap(resultStack);
                    */                    

                    ImageStack resultStack =
                            CrossCorrelationMap.calculateCrossCorrelationMap(
                                    snapAndProcess(), driftData.getReferenceStack(), true);
                    driftData.setResultMap(resultStack);

                    // Measure XYZ drift
                    FloatProcessor ccSliceBottom = resultStack.getProcessor(3).convertToFloatProcessor();
                    FloatProcessor ccSliceMiddle = resultStack.getProcessor(2).convertToFloatProcessor();
                    FloatProcessor ccSliceTop = resultStack.getProcessor(1).convertToFloatProcessor();
                    xi_n = (ccSliceTop.getMax() - ccSliceBottom.getMax()) / ccSliceMiddle.getMax(); // eq 5 in McGorty et al. 2013
                    
                    float[] rawCenter = new float[3];
                    float[] currentCenter = EstimateShiftAndTilt.getMaxFindByOptimization(ccSliceMiddle);
                    rawCenter = currentCenter;
                        
                    /* Deprecated 190404
                    double max = 0;
                    int index = 2;
                    for (int i = 1; i <= resultStack.getSize(); i ++) {
                        FloatProcessor currentSlice = resultStack.getProcessor(i).convertToFloatProcessor();
                        float[] currentCenter = EstimateShiftAndTilt.getMaxFindByOptimization(currentSlice);
                        
                        FloatStatistics stats = new FloatStatistics(currentSlice, Measurements.KURTOSIS, new Calibration());

                        double score = Math.abs(currentSlice.getMax()*stats.kurtosis);

                        if (score >= max) {
                            max = score;
                            index = i;
                            rawCenter = currentCenter;
                        }
                    }
                    */
                    
                    // The getMaxFindByOptimization can generate NaN's so we protect against that
                    if ( Double.isNaN(rawCenter[0]) || Double.isNaN(rawCenter[1]) || Double.isNaN(rawCenter[2]) ) continue;

                    // A static image will have it's correlation map peak in the exact center of the image
                    // A moving image will have the peak shifted in relation to the center
                    // We subtract the rawCenter from the image center to obtain the drift
                    double x  = (double) rawCenter[0]  - (resultStack.getWidth()/2);
                    double y  = (double) rawCenter[1]  - (resultStack.getHeight()/2);
                    Point2D.Double xyDrift = new Point2D.Double(x,y);

                    // Convert from pixel units to microns
                    xyDrift = hardwareManager.convertPixelsToMicrons(xyDrift);

                    // Check if detected movement is within bounds
                    if ((correctionMode == XY || correctionMode == XYZ) &&
                            (Math.abs(xyDrift.x) > threshold || Math.abs(xyDrift.y) > threshold)) {
                        runAcquisition(false);
                        setChanged();
                        notifyObservers(OUT_OF_BOUNDS_ERROR);
                        driftData.setReferenceImage(null);
                        driftData.setReferenceStack(new ImageStack()); // 190401 kw
                        driftData.clearResultMap(); // 190412 kw
                        break;
                    }

                    double zDrift = 0;

                    /* deprecated 190401
                    // Move Z stage to winning position. We get zDrift in microns instead of steps to save later to Data
                    if (isRunning() && (correctionMode == Z || correctionMode == XYZ) ) {
                        if (index == 1) zDrift = hardwareManager.getStepSize();
                        else  if (index == 3) zDrift = -hardwareManager.getStepSize();

                        hardwareManager.moveFocusStage(zDrift);
                    }
                    */
                    
                    // Move Z stage to more appropriate position. We get zDrift in microns instead of steps to save later to Data. (added 190403 kw)
                    if (isRunning() && (correctionMode == Z || correctionMode == XYZ) ) {
                        zDrift = -alpha*(xi_n - xi_0); // eq 4 in McGorty et al. 2013
                        
                        hardwareManager.moveFocusStage(zDrift);
                    }

                    // Move XY stage
                    if (isRunning() && (correctionMode == XY || correctionMode == XYZ) ){
                        Point2D.Double xyDriftCorr = new Point2D.Double(x,-y);
                        xyDriftCorr = hardwareManager.convertPixelsToMicrons(xyDriftCorr);
                        
                        hardwareManager.moveXYStage(xyDriftCorr);
                    }

                    // Add data
                    if (isRunning()) {
                        if (correctionMode == Z) driftData.addZShift(zDrift, getTimeElapsed());
                        else if (correctionMode == XY) driftData.addXYshift(xyDrift.x, xyDrift.y, getTimeElapsed());
                        else if (correctionMode == XYZ) driftData.addXYZshift(xyDrift.x, xyDrift.y, zDrift, getTimeElapsed());
                    }

                    // If the acquisition was stopped, clear the reference image.
                    if (!isRunning()) {
                        driftData.setReferenceImage(null);
                        driftData.setReferenceStack(new ImageStack()); // 190401 kw
                        driftData.clearResultMap(); // 190412 kw
                    }
                    if ((System.currentTimeMillis()-startRun) < sleep && (System.currentTimeMillis()-startRun) > 0)
                        java.lang.Thread.sleep(sleep - (System.currentTimeMillis()-startRun));
                }
            java.lang.Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            ReportingUtils.showError(e, INTERRUPTION_ERROR);
        } catch (Exception e) {
            ReportingUtils.showError(e, ACQUISITION_ERROR);
        } finally {
            notifyObservers();
            runAcquisition(false);
        }
    }

    //////////////////////////// Methods

    public double getTimeElapsed() {
        return System.currentTimeMillis() - startTimeStamp;
    }

    public void runAcquisition(boolean run) {
        startTimeStamp = System.currentTimeMillis();
        driftData.clearData();
        this.runAcquisition = run;
    }

    public FloatProcessor snapAndProcess() throws Exception{
        FloatProcessor image;
        hardwareManager.snap();
        image = processor.process(hardwareManager.getImage());
        driftData.setLatestImage(image);
        return image;
    }

    //////////////////////////// Getters/Setters

    public boolean isRunning() {
        return runAcquisition;
    }

    public boolean itIsAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public int getCorrectionMode() {
        return correctionMode;
    }

    public void setCorrectionMode(int correctionMode) {
        driftData.setDataType(correctionMode);
        this.correctionMode = correctionMode;
    }

    public long getSleep() {
        return sleep;
    }

    public void setSleep(long sleep) {
        this.sleep = sleep;
    }
    
    // added 190404 kw
    public void setAlpha(double alpha){
        this.alpha = alpha;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }
}
