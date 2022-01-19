package nanoj.liveDriftCorrection.java;

import ij.ImageStack;
//import ij.measure.Calibration;
//import ij.measure.Measurements;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
//import ij.process.FloatStatistics;
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
    
    // added 201230 kw
    private ImageStack resultStack = null;
    private ImageProcessor resultImage = null;
    private FloatProcessor ccSliceMiddle = null;

    private static final int XYZ = 0;
    private static final int Z = 1;
    private static final int XY = 2;
    
    private double imCentx = 0;
    private double imCenty = 0;

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
    private double oldTime;
    private double dt;
    private double threshold;
    private double Kp = 0; // Proportional gain. 190401 kw
    private double Kt = 0; // Trend gain. 220118 JE
    private double Kl = 1; // Lateral gain 220118 JE
    private double SP = 0; // Z-correction setpoint
    private double PV = 0; // Z-correction process variable
    private double z_err = 0; // Z-correction error (for proportional gain)
    private double xErr = 0; // 220119 JE
    private double yErr = 0; // 220119 JE
    private double err_int = 0; // Z-correction error sum (for integral gain)
    private int Delay = 100;


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
                    if (driftData.getReferenceImage() == null){
                        driftData.setReferenceImage(snapAndProcess());
                        //Initialise variables for new run 220119 JE
                        xErr = 0;
                        yErr = 0;
                    } 
                    
                    // If we've just started, get the reference stack (190401 kw)
                    if (correctionMode == Z || correctionMode == XYZ) {
                        if (driftData.getReferenceStack().size() == 0){
                            
                            //Initialise variables for new run 220118 JE
                            SP = 0; 
                            PV = 0;
                            z_err = 0;
                            err_int = 0;
                            
                            ImageStack refStack = new ImageStack(
                                driftData.getReferenceImage().getWidth(),
                                driftData.getReferenceImage().getHeight()
                            );
                            // Take picture at current position, filter, clip and add to image stack
                            refStack.addSlice(MIDDLE, snapAndProcess());
                        
                            // Move one stepSize above focus, snap and add to image stack
                            hardwareManager.moveFocusStageInSteps(1);
                            refStack.addSlice(TOP, snapAndProcess(), 0);

                            // Move two stepSizes below the focus, snap and add to image stack
                            hardwareManager.moveFocusStageInSteps(-2);
                            refStack.addSlice(BOTTOM, snapAndProcess());

                            // Move back to original position
                            hardwareManager.moveFocusStageInSteps(1);

                            refStackCC = CrossCorrelationMap.calculateCrossCorrelationMap(snapAndProcess(), refStack, true);
                            
                            FloatProcessor refCCbottom = refStackCC.getProcessor(3).convertToFloatProcessor();
                            FloatProcessor refCCmiddle = refStackCC.getProcessor(2).convertToFloatProcessor();
                            FloatProcessor refCCtop = refStackCC.getProcessor(1).convertToFloatProcessor();
                            
                            double refCCbottomMidMax = refCCbottom.getMax();
                            double refCCtopMidMax = refCCtop.getMax();
                            double refCCmidMidMax = refCCmiddle.getMax();

                            /* added 190401 kw
                            FloatProcessor refSliceBottom = refStack.getProcessor(3).convertToFloatProcessor();
                            FloatProcessor refSliceMiddle = refStack.getProcessor(2).convertToFloatProcessor();
                            FloatProcessor refSliceTop = refStack.getProcessor(1).convertToFloatProcessor();

                            ImageStack refBottomMiddle = CrossCorrelationMap.calculateCrossCorrelationMap(refSliceBottom, refStack, true);
                            ImageStack refTopMiddle = CrossCorrelationMap.calculateCrossCorrelationMap(refSliceTop, refStack, true);
                            ImageStack refMidMid = CrossCorrelationMap.calculateCrossCorrelationMap(refSliceMiddle, refStack, true);

                            FloatProcessor refCCbottomMiddle = refBottomMiddle.getProcessor(2).convertToFloatProcessor();
                            FloatProcessor refCCtopMiddle = refTopMiddle.getProcessor(2).convertToFloatProcessor();
                            FloatProcessor refCCmidMid = refMidMid.getProcessor(2).convertToFloatProcessor();
                        
                            // offset maxima because minima usually not at zero
                            double refCCbottomMidMax = refCCbottomMiddle.getMax();
                            double refCCtopMidMax = refCCtopMiddle.getMax();
                            double refCCmidMidMax = refCCmidMid.getMax();*/
                    
                            // overriding this alpha for now, just using user input value.
                            //alpha = (2*refCCmidMidMax - refCCtopMidMax - refCCbottomMidMax) * hardwareManager.getStepSize() / 2; // eq 6 in McGorty et al. 2013. corrected so that stepsize is in numerator!
                            SP = (refCCtopMidMax - refCCbottomMidMax) / refCCmidMidMax; // Z-correction setpoint
                        
                            driftData.setReferenceStack(refStack);
                        }           

                        resultStack =
                            CrossCorrelationMap.calculateCrossCorrelationMap(
                                    snapAndProcess(), driftData.getReferenceStack(), true);
                        driftData.setResultMap(resultStack);

                        // Measure XYZ drift
                        FloatProcessor ccSliceBottom = resultStack.getProcessor(3).convertToFloatProcessor();
                        ccSliceMiddle = resultStack.getProcessor(2).convertToFloatProcessor();
                        FloatProcessor ccSliceTop = resultStack.getProcessor(1).convertToFloatProcessor();
                        
                        // offset maxima because minima not at zero
                        double ccSliceBottomMax = ccSliceBottom.getMax();
                        double ccSliceTopMax = ccSliceTop.getMax();
                        double ccSliceMiddleMax = ccSliceMiddle.getMax();
                        
                        PV = (ccSliceTopMax - ccSliceBottomMax) / ccSliceMiddleMax; // eq 5 in McGorty et al. 2013
                        
                        imCentx = resultStack.getWidth()/2;
                        imCenty = resultStack.getHeight()/2;
                    }
                    
                    // XY drift correction ONLY 201230 kw
                    else {
                        resultImage =
                            CrossCorrelationMap.calculateCrossCorrelationMap(
                                    snapAndProcess(), driftData.getReferenceImage(), true);
                        driftData.setResultMap(resultImage);

                        ccSliceMiddle = resultImage.convertToFloatProcessor();
                        
                        imCentx = resultImage.getWidth()/2;
                        imCenty = resultImage.getHeight()/2;
                    }
                    
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
                    dt = getTimeElapsed() - oldTime;
                    xErr = (double) rawCenter[0]  - imCentx;
                    yErr = (double) rawCenter[1]  - imCenty;
                    
                    double x = 0;
                    double y = 0;
                    
                    if(driftData.getLenLDrift()<Delay){
                        x  = Kl*(xErr); // updated with gain parameter 220118 JE
                        y  = Kl*(yErr); // updated with gain parameter 220118 JE
                        }
                    else{
                        x = (Kl * xErr) - Kt*dt*((driftData.getDelayedXDrift(Delay)-driftData.getLatestXDrift())/(driftData.getDelayedTimeStamp(Delay)-driftData.getLatestTimeStamp()));
                        y = (Kl * yErr) - Kt*dt*((driftData.getDelayedYDrift(Delay)-driftData.getLatestYDrift())/(driftData.getDelayedTimeStamp(Delay)-driftData.getLatestTimeStamp()));
                    }
                    
                    
                    
                    if (driftData.getflipY()) y = -y; // 201229 kw
                    
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
                    // Now using PI controller instead of equation in McGorty 2013 paper (220110 kw)
                    if (isRunning() && (correctionMode == Z || correctionMode == XYZ) ) {
                        err_int = err_int + z_err*dt; // Z-correction error integral (use previous error value before calculating one for this loop) 220110
                        z_err = SP - PV; // Z-correction error 220110
                        
                        if(driftData.getLenZDrift()<Delay){
                            zDrift = (Kp * z_err);
                        }
                        else zDrift = (Kp * z_err) - Kt*dt*((driftData.getDelayedZDrift(Delay)-driftData.getLatestZDrift())/(driftData.getDelayedTimeStamp(Delay)-driftData.getLatestTimeStamp())); // updated with predictive term 220117 JE // may want to add some averaging of points in the future
                        
                        
                        
                        hardwareManager.moveFocusStage(zDrift);
                    }
                    
                    oldTime = getTimeElapsed(); // time of current loop (store for next loop iteration)

                    // Move XY stage
                    if (isRunning() && (correctionMode == XY || correctionMode == XYZ) ){
                        Point2D.Double xyDriftCorr = new Point2D.Double(x,-y);
                        xyDriftCorr = hardwareManager.convertPixelsToMicrons(xyDriftCorr);
                        
                        hardwareManager.moveXYStage(xyDriftCorr);
                    }

                    // Add data
                    if (isRunning()) {
                        if (correctionMode == Z) driftData.addZShift(zDrift, getTimeElapsed());
                        //if (correctionMode == Z) driftData.addPIshift(SP, PV, zDrift, getTimeElapsed()); // for debugging/tuning PI controller parameters
                        else if (correctionMode == XY) driftData.addXYshift((xyDrift.x)/Kl, (xyDrift.y)/Kl, getTimeElapsed());
                        else if (correctionMode == XYZ) driftData.addXYZshift((xyDrift.x)/Kl, (xyDrift.y)/Kl, zDrift, getTimeElapsed());
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
            java.lang.Thread.sleep(500);
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
    public void setKp(double Kp){
        this.Kp = Kp;
    }
    
    // added 220110 kw
    public void setKt(double Kt){
        this.Kt = Kt;
    }
    
    // added 220118 JE
    public void setKl(double Kl){
        this.Kl = Kl;
    }
    
    // added 220117 JE
    public void setDelay(int Delay){
        this.Delay = Delay;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }
}
