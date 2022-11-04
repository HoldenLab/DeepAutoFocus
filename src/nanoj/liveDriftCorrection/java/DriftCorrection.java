package nanoj.liveDriftCorrection.java;

import ij.ImageStack;
//import ij.measure.Calibration;
//import ij.measure.Measurements;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
//import ij.process.FloatStatistics;
import ij.process.ImageStatistics;
//import nanoj.core.java.image.drift.EstimateShiftAndTilt;
import java.lang.Math;
import nanoj.core.java.image.transform.CrossCorrelationMap;
import org.micromanager.internal.utils.ReportingUtils;

import java.awt.geom.Point2D;
import java.util.Observable;
import nanoj.core.java.image.analysis.CalculateImageStatistics;

public class DriftCorrection extends Observable implements Runnable {
    private boolean alive = true;
    private boolean runAcquisition = false;
    private boolean MoveSuccess = true;
    private DriftCorrectionHardware hardwareManager;
    private DriftCorrectionData driftData;
    private DriftCorrectionProcess processor;

    private int correctionMode = 0;
    
    // added 201230 kw
    private ImageStack resultStack = null;
    private ImageStack refStackCC = null; //from Kevin's version 220119 JE
    private ImageProcessor refCC = null;
    private ImageProcessor refTopTopCC = null; // 220131 JE
    private ImageProcessor refBottomBottomCC = null; // 220131 JE
    private ImageProcessor resultImage = null;
    private FloatProcessor ccSliceMiddle = null;

    private static final int XYZ = 0;
    private static final int Z = 1;
    private static final int XY = 2;
    
    private double imCentx = 0;
    private double imCenty = 0;

    // Settings
    private long sleep = 200; // time in between loops in milliseconds
    private double refUpdate = 0; // time between updates to reference images

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
    private boolean StartMDA = false;
    private long startTimeStamp;
    private double UpdateTime;
    private double oldTime;
    private double dt;
    private double threshold;
    private double Zp = 1; // Proportional gain. 190401 kw
    private double Zi = 0; // Integral gain. 220118 JE
    private double Lp = 1; // Lateral gain 220118 JE
    private double Li = 0; // Lateral gain 220118 JE
    private double SP = 0; // Z-correction setpoint
    private double PV = 0; // Z-correction process variable
    private double z_err = 0; // Z-correction error (for proportional gain)
    private double z_errSum = 0; // Z-correction error (for integral gain)
    double zDrift = 0;
    double x = 0;
    double y = 0;
    double t = 0;
    double missX = 0;
    double missY = 0;
    double missZ = 0;
    private double xErr = 0; // 220119 JE
    private double yErr = 0; // 220119 JE
    private double xErrSum = 0; // 220414 JE
    private double yErrSum = 0; // 220414 JE
    private double oldyErr = 0; // 220829 JE
    private double oldxErr = 0; // 220829 JE
    private double oldzErr = 0; // 220906 JE
    private double Top = 0; // 220131 JE
    private double Bottom = 0; // 220131 JE
    private double Middle = 0; // 220131 JE
    private double HeightRatio = 0; // 220131 JE
    
    private double refCCbottomMidMax = 0; // 220201 JE
    private double refCCtopMidMax = 0; // 220201 JE
    private double refCCmidMidMax = 0; // 220201 JE
    private double refCCTopTopMax = 0; // 220201 JE
    private double refCCBottomBottomMax = 0; // 220201 JE
    private double refmiddleMedian = 0; // 220916 JE
    float[] Peak = null; //220926 JE
    double[] currentCenter = null; //221012 JE


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

                    if (hardwareManager.getTrigger() != null && hardwareManager.getTriggerState()){ // Stops ImLock trying to correct for deliberate moves from MDA 221018 JE
                        StartMDA = true;
                        continue;
                    }

                    if (StartMDA && hardwareManager.getTrigger() != null && !hardwareManager.getTriggerState()) { // Can't return scanning stage to start position so update reference images for new position 221104
                        StartMDA = false;
                        driftData.setReferenceImage(null);
                        driftData.setReferenceStack(new ImageStack());
                    }
                    
                    if (refUpdate != 0 && getTimeElapsed() > UpdateTime) { // forces update to reference images 221021 JE
                        driftData.setReferenceImage(null);
                        driftData.setReferenceStack(new ImageStack());
                    }

                    // If we've just started, get the reference image
                    if (driftData.getReferenceImage() == null){                       
                        //Initialise variables for new run 220119 JE
                        xErr = 0;
                        yErr = 0;
                        xErrSum = 0;
                        yErrSum = 0;
                        oldyErr = 0;
                        oldxErr = 0;
                        oldTime = 0;
                        t=0;
                        HeightRatio = 0;
                        
                        driftData.setReferenceImage(snapAndProcess());
                        if (correctionMode == XY){
                            refCC = CrossCorrelationMap.calculateCrossCorrelationMap(driftData.getReferenceImage(), driftData.getReferenceImage(), true); // 220131 JE
                            Peak = CalculateImageStatistics.getMax(refCC); // 221012 JE
                            refCCmidMidMax = processor.CenterHeightFind3(refCC.convertToFloatProcessor(),Peak); // 221012 JE

                            currentCenter = processor.PeakFind2(refCC.convertToFloatProcessor(), Peak); // 221012 JE
                            imCentx = currentCenter[0];
                            imCenty = currentCenter[1];
                        }
                        UpdateTime = getTimeElapsed() +  refUpdate;
                    } 
                    
                    // If we've just started, get the reference stack (190401 kw)
                    if (correctionMode == Z || correctionMode == XYZ) {
                        if (driftData.getReferenceStack().size() == 0){
                            
                            //Initialise variables for new run 220118 JE
                            SP = 0; 
                            PV = 0;
                            z_err = 0;
                            z_errSum = 0;
                            oldzErr = 0;
                            
                            ImageStack refStack = new ImageStack(
                                driftData.getReferenceImage().getWidth(),
                                driftData.getReferenceImage().getHeight()
                            );
                            // Take picture at current position, filter, clip and add to image stack
                            hardwareManager.moveXYStage(new Point2D.Double(0,0)); // give xy stage oppertunity to reset 220908 JE
                            refStack.addSlice(MIDDLE, snapAndProcess());
                        
                            // Move one stepSize above focus, snap and add to image stack
                            hardwareManager.moveFocusStageInSteps(1);
                            refStack.addSlice(TOP, snapAndProcess(), 0);

                            // Move two stepSizes below the focus, snap and add to image stack
                            hardwareManager.moveFocusStageInSteps(-2);
                            refStack.addSlice(BOTTOM, snapAndProcess());

                            // Move back to original position
                            hardwareManager.moveFocusStageInSteps(1);
                            hardwareManager.moveXYStage(new Point2D.Double(0,0)); // give xy stage oppertunity to reset 220908 JE

                            //refStackCC = CrossCorrelationMap.calculateCrossCorrelationMap(snapAndProcess(), refStack, true);
                            refStackCC = CrossCorrelationMap.calculateCrossCorrelationMap(refStack.getProcessor(2), refStack, true); // 220131 JE
                            refTopTopCC = CrossCorrelationMap.calculateCrossCorrelationMap(refStack.getProcessor(1), refStack.getProcessor(1), true); // 220131 JE
                            refBottomBottomCC = CrossCorrelationMap.calculateCrossCorrelationMap(refStack.getProcessor(3), refStack.getProcessor(3), true); // 220131 JE

                            FloatProcessor refCCbottom = refStackCC.getProcessor(3).convertToFloatProcessor();
                            FloatProcessor refCCmiddle = refStackCC.getProcessor(2).convertToFloatProcessor();
                            FloatProcessor refCCtop = refStackCC.getProcessor(1).convertToFloatProcessor();
                            FloatProcessor refTopTopProc = refTopTopCC.convertToFloatProcessor(); // 220131 JE
                            FloatProcessor refBottomBottomProc = refBottomBottomCC.convertToFloatProcessor(); // 220131 JE
                            
                            refmiddleMedian = ImageStatistics.getStatistics(refStack.getProcessor(2)).median;
                            
                            //refCCbottomMidMax = refCCbottom.getMax();
                            //refCCtopMidMax = refCCtop.getMax();
                            //refCCmidMidMax = refCCmiddle.getMax();
                            
                            // offset maxima because minima usually not at zero
                            //refCCbottomMidMax = refCCbottomMiddle.getMax();
                            //refCCtopMidMax = refCCtopMiddle.getMax();
                            //refCCmidMidMax = refCCmidMid.getMax();*/
                            
                            Peak = processor.PickPlane(refStackCC);
                            
                            refCCbottomMidMax = processor.CenterHeightFind3(refCCbottom, Peak); // 220131 JE
                            refCCtopMidMax = processor.CenterHeightFind3(refCCtop, Peak); // 220131 JE
                            refCCmidMidMax = processor.CenterHeightFind3(refCCmiddle, Peak); // 220131 JE
                            refCCTopTopMax = processor.CenterHeightFind3(refTopTopProc, Peak); // 220131 JE
                            refCCBottomBottomMax = processor.CenterHeightFind3(refBottomBottomProc, Peak); // 220131 JE
                            
                            Top = (refCCtopMidMax/refCCTopTopMax); // 220131 JE
                            Bottom = (refCCbottomMidMax/refCCBottomBottomMax); // 220131 JE
                            Middle = (refCCmidMidMax/refCCmidMidMax); // 220131 JE
                              
                            SP = (Top - Bottom) / (Middle + 0.6); // Z-correction setpoint // 220131 JE
                                                        
                            //SP = (refCCtopMidMax - refCCbottomMidMax) / refCCmidMidMax; // Z-correction setpoint
                        
                            driftData.setReferenceStack(refStack);
                            
                            int Plane = (int) Peak[2];
                            currentCenter = processor.PeakFind2(refStackCC.getProcessor(Plane).convertToFloatProcessor(), Peak); // 221012 JE
                            
                            t = 0;
                            imCentx = currentCenter[0];
                            imCenty = currentCenter[1];
                            UpdateTime = getTimeElapsed() +  refUpdate;
                        }
                        
                        ImageProcessor ImageT = snapAndProcess();
                        resultStack = CrossCorrelationMap.calculateCrossCorrelationMap(ImageT, driftData.getReferenceStack(), true);
                        driftData.setResultMap(resultStack);

                        // Measure XYZ drift
                        FloatProcessor ccSliceBottom = resultStack.getProcessor(3).convertToFloatProcessor();
                        ccSliceMiddle = resultStack.getProcessor(2).convertToFloatProcessor();
                        FloatProcessor ccSliceTop = resultStack.getProcessor(1).convertToFloatProcessor();
                        
                        double MedianT = ImageStatistics.getStatistics(ImageT).median;
                        
                        // offset maxima because minima not at zero
                        //double ccSliceBottomMax = ccSliceBottom.getMax();
                        //double ccSliceTopMax = ccSliceTop.getMax();
                        //double ccSliceMiddleMax = ccSliceMiddle.getMax();

                        //double ccSliceBottomMax = processor.CenterHeightFind2(ccSliceBottom); // 220131 JE
                        //double ccSliceTopMax = processor.CenterHeightFind2(ccSliceTop); // 220131 JE
                        //double ccSliceMiddleMax = processor.CenterHeightFind2(ccSliceMiddle); // 220131 JE

                        Peak = processor.PickPlane(resultStack);

                        double ccSliceBottomMax = processor.CenterHeightFind3(ccSliceBottom, Peak); // 220926 JE
                        double ccSliceTopMax = processor.CenterHeightFind3(ccSliceTop, Peak); // 220926 JE
                        double ccSliceMiddleMax = processor.CenterHeightFind3(ccSliceMiddle, Peak); // 220926 JE

                        Top = (ccSliceTopMax/refCCTopTopMax); // 220131 JE
                        Bottom = (ccSliceBottomMax/refCCBottomBottomMax); // 220131 JE
                        Middle = (ccSliceMiddleMax/refCCmidMidMax); // 220131 JE
                        
                        PV = (Top - Bottom) / (Middle + 0.6);//(MedianT/refmiddleMedian); // eq 5 in McGorty et al. 2013 // 220131 JE

                        //PV = (ccSliceTopMax - ccSliceBottomMax) / ccSliceMiddleMax; // eq 5 in McGorty et al. 2013
                        
                        int Plane = (int) Peak[2];
                        currentCenter = processor.PeakFind2(resultStack.getProcessor(Plane).convertToFloatProcessor(), Peak); // 221012 JE
                                               
                        HeightRatio = Math.max(Top,Math.max(Middle,Bottom));
                        
                    }
                    
                    // XY drift correction ONLY 201230 kw
                    else {
                        resultImage =  CrossCorrelationMap.calculateCrossCorrelationMap(snapAndProcess(), driftData.getReferenceImage(), true);
                        driftData.setResultMap(resultImage);

                        ccSliceMiddle = resultImage.convertToFloatProcessor();
                        
                        Peak = CalculateImageStatistics.getMax(ccSliceMiddle); // 221012 JE
                        currentCenter =  processor.PeakFind2(ccSliceMiddle, Peak); // 221012 JE
                        double ccSliceMiddleMax = processor.CenterHeightFind3(ccSliceMiddle, Peak); // 220926 JE
                        HeightRatio = ccSliceMiddleMax / refCCmidMidMax;
                        
                        //double[] currentCenter = processor.PeakFind(ccSliceMiddle);
                        //imCentx = currentCenter[0];
                        //imCenty = currentCenter[1];
                       
                    }
                    
                    
                    //float[] currentCenter= EstimateShiftAndTilt.getMaxFindByOptimization(ccSliceMiddle);
                    
                    // The getMaxFindByOptimization can generate NaN's so we protect against that
                    //if ( Double.isNaN(currentCenter[0]) || Double.isNaN(currentCenter[1]) || Double.isNaN(currentCenter[2]) ) continue;
                    if ( Double.isNaN(currentCenter[0]) || Double.isNaN(currentCenter[1]) ) continue;

                    // A static image will have it's correlation map peak in the exact center of the image
                    // A moving image will have the peak shifted in relation to the center
                    // We subtract the rawCenter from the image center to obtain the drift
                    

                    xErr = currentCenter[0]  - imCentx;
                    yErr = currentCenter[1]  - imCenty;
                    dt = (getTimeElapsed() - oldTime)/1000;
                    t = t + dt;
                    missX = x+(oldxErr-xErr);
                    missY = y+(oldyErr-yErr);
                    //if (MoveSuccess && ((dt*1000)<10*sleep) && (dt*1000 > 1000)){
                        //xErrSum = xErrSum + missX*dt;
                        //yErrSum = yErrSum + missY*dt;
                        xErrSum = xErrSum + xErr*dt;
                        yErrSum = yErrSum + yErr*dt;
                    //}
                    
                    x = 0;
                    y = 0;

                    if (correctionMode == XY || correctionMode == XYZ){
                        x = Lp*xErr + Li*xErrSum;
                        y = Lp*yErr + Li*yErrSum;
                    }
                    
                    oldTime = getTimeElapsed(); // time of current loop (store for next loop iteration)
                    oldxErr = xErr;
                    oldyErr = yErr;
                    
                    //ReportingUtils.showMessage(Boolean.toString(driftData.getflipY()) + " " + Boolean.toString(driftData.getflipX()) + " " + Boolean.toString(driftData.getSwitchXY()));
                    
                    
                    Point2D.Double xyDrift = new Point2D.Double(x,-y);
                    
                    if (driftData.getSwitchXY()){
                        xyDrift.x = y;
                        xyDrift.y = -x;
                    }
                    if (driftData.getflipX()) xyDrift.x = -xyDrift.x;
                    if (driftData.getflipY()) xyDrift.y = -xyDrift.y;
                    
                    //Point2D.Double xyDrift = new Point2D.Double(y,-x); //Not sure why the switch of x and y is needed but it seems to work for now 290922 JE @ CAIRN

                    // Convert from pixel units to microns
                    xyDrift = hardwareManager.convertPixelsToMicrons(xyDrift);
                    
                    //LatMag = Math.sqrt(Math.pow(xErr,2) + Math.pow(yErr,2));
                    
                    // Check if detected movement is within bounds
                    if (((correctionMode == XY || correctionMode == XYZ) && (Math.abs(xyDrift.x) > threshold || Math.abs(xyDrift.y) > threshold) || HeightRatio < 0.2) 
                            || (correctionMode == Z && HeightRatio < 0.2)) {
                        runAcquisition(false);
                        setChanged();
                        notifyObservers(OUT_OF_BOUNDS_ERROR);
                        driftData.setReferenceImage(null);
                        driftData.setReferenceStack(new ImageStack()); // 190401 kw
                        driftData.clearResultMap(); // 190412 kw
                        break;
                    }
                    
                    missZ = zDrift+(oldzErr-z_err);
                    // Move Z stage to more appropriate position. We get zDrift in microns instead of steps to save later to Data. (added 190403 kw)
                    // Now using PI controller instead of equation in McGorty 2013 paper (220110 kw)
                    if (isRunning() && (correctionMode == Z || correctionMode == XYZ) ) {
                        z_err = SP - PV; // Z-correction error 220110
                        z_errSum = z_errSum + z_err*dt;
                        zDrift = Zp*z_err + Zi*z_errSum;
                        if(Zp!=0 || Zi!=0) hardwareManager.moveFocusStage(zDrift);
                        oldzErr = z_err;
                        
                    }

                    // Move XY stage
                    if (isRunning() && (correctionMode == XY || correctionMode == XYZ) ){
                        /*
                        Point2D.Double xyDriftCorr = new Point2D.Double(x,-y);
                        xyDriftCorr = hardwareManager.convertPixelsToMicrons(xyDriftCorr);
                        if (Math.abs(xyDriftCorr.x) < 5.023) xyDriftCorr.x=0;
                        if (Math.abs(xyDriftCorr.y) < 5.023) xyDriftCorr.y=0;
                        if(Lp!=0 | Li!=0) hardwareManager.moveXYStage(xyDriftCorr);
                        */
                        //if (Math.abs(xyDrift.x) < 0.023) xyDrift.x=0;
                        //if (Math.abs(xyDrift.y) < 0.023) xyDrift.y=0;
                        if(Lp!=0 || Li!=0) MoveSuccess = hardwareManager.moveXYStage(xyDrift);
                    }

                    // Add data //changed to switch statement from ifs 220128 JE
                    switch(correctionMode){
                        case Z:
                             driftData.addZShift(zDrift, z_err, getTimeElapsed());
                             break;
                        case XY:
                            if (driftData.Tune){
                                driftData.addXYshift(xErr, yErr, getTimeElapsed());
                            }
                            else driftData.addXYshift((xyDrift.x), (xyDrift.y), getTimeElapsed());
                            break;
                        case XYZ:
                            if (driftData.Tune){
                                driftData.addXYZshift(xErr, yErr, zDrift, z_err, getTimeElapsed());
                                //driftData.addXYZshift(xErr, yErr, zDrift, (xyDrift.x), getTimeElapsed());
                            }
                            else driftData.addXYZshift((xyDrift.x), (xyDrift.y), zDrift, z_err, getTimeElapsed());
                            break;
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

    public void setRefUpdate(double refUpdate) {
        this.refUpdate = refUpdate*60000; //convert box value in mins to useful value in ms 221021 JE
    }
    
    // added 190404 kw
    public void setZp(double Zp){
        this.Zp = Zp;
    }
    
    // added 220110 kw
    public void setZi(double Zi){
        this.Zi = Zi;
    }
    
    // added 220118 JE
    public void setLp(double Lp){
        this.Lp = Lp;
    }
    
    // added 220118 JE
    public void setLi(double Li){
        this.Li = Li;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }
}