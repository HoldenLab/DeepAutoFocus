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
    private int i = 0;
    private double AMM = 0;
    private double LMM = 0;
    private double xErr = 0; // 220119 JE
    private double yErr = 0; // 220119 JE
    private double xErrSum = 0; // 220414 JE
    private double yErrSum = 0; // 220414 JE
    private double oldzErr = 0; // 220906 JE
    private double Top = 0; // 220131 JE
    private double Bottom = 0; // 220131 JE
    private double Middle = 0; // 220131 JE
    private double HeightRatio = 0; // 220131 JE
    private double Bias = 0; // 221025 JE
    private final boolean NanoJNormalize = false;
    
    private double refCCbottomMidMax = 0; // 220201 JE
    private double refCCtopMidMax = 0; // 220201 JE
    private double refCCmidMidMax = 0; // 220201 JE
    private double refCCTopTopMax = 0; // 220201 JE
    private double refCCBottomBottomMax = 0; // 220201 JE
    private double refmiddleMedian = 0; // 220916 JE
    private float[] Peak = null; //220926 JE
    private double[] currentCenter = null; //221012 JE
    private int[] Offsets = null; // 230209 JE
    private float[] VerticalOffsets = null;


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
                        oldTime = 0;
                        t=0;
                        HeightRatio = 0;
                        driftData.clearResultMap(); // 190412 kw
                        i=-1;
                        /* // for simulations JE
                        ImagePlus LoadedImage = opener.openImage("C:\\Users\\joshe\\Documents\\GitHub\\LifeHackDevelopment\\Imlock\\SubPixelShiftOut\\Images\\" + "Middle.tif");
                        FloatProcessor image = processor.Normalize(LoadedImage.getProcessor().convertToFloatProcessor());
                        driftData.setReferenceImage(image);
                        */
                        driftData.setReferenceImage(processor.Normalize(snapAndProcess()));
                        if (correctionMode == XY){
                            refCC = CrossCorrelationMap.calculateCrossCorrelationMap(driftData.getReferenceImage(), driftData.getReferenceImage(), NanoJNormalize); // 220131 JE
                            Peak = CalculateImageStatistics.getMax(refCC); // 221012 JE
                            refCCmidMidMax = processor.CenterHeightFind3(refCC.convertToFloatProcessor(),Peak); // 221012 JE

                            imCentx = refCC.getWidth()/2f; 
                            imCenty = refCC.getHeight()/2f;
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
                            
                            hardwareManager.moveXYStage(0, 0);
                            hardwareManager.stopXYStage();
                            // Take picture at current position, filter, clip and add to image stack
                            refStack.addSlice(MIDDLE, processor.Normalize(snapAndProcess()));
                            /* //for simulations JE
                            ImagePlus LoadedImage = opener.openImage("C:\\Users\\joshe\\Documents\\GitHub\\LifeHackDevelopment\\Imlock\\SubPixelShiftOut\\Images\\" + "Middle.tif");
                            //FloatProcessor image = LoadedImage.getProcessor().convertToFloatProcessor();
                            FloatProcessor image = processor.Normalize(LoadedImage.getProcessor().convertToFloatProcessor());
                            refStack.addSlice(MIDDLE, image);
                            */
                            // Move one stepSize above focus, snap and add to image stack
                            hardwareManager.moveFocusStageInSteps(1);
                            refStack.addSlice(TOP, processor.Normalize(snapAndProcess()), 0);
                            /* //for simulations JE 
                            LoadedImage = opener.openImage("C:\\Users\\joshe\\Documents\\GitHub\\LifeHackDevelopment\\Imlock\\SubPixelShiftOut\\Images\\" + "Top.tif");
                            //image = LoadedImage.getProcessor().convertToFloatProcessor();
                            image = processor.Normalize(LoadedImage.getProcessor().convertToFloatProcessor());
                            refStack.addSlice(TOP, image, 0);
                            */
                            // Move two stepSizes below the focus, snap and add to image stack
                            hardwareManager.moveFocusStageInSteps(-2);
                            refStack.addSlice(BOTTOM, processor.Normalize(snapAndProcess()));
                            /* //for simulations JE
                            LoadedImage = opener.openImage("C:\\Users\\joshe\\Documents\\GitHub\\LifeHackDevelopment\\Imlock\\SubPixelShiftOut\\Images\\" + "Bottom.tif");
                            //image = LoadedImage.getProcessor().convertToFloatProcessor();
                            image = processor.Normalize(LoadedImage.getProcessor().convertToFloatProcessor());
                            refStack.addSlice(BOTTOM, image);
                            */
                            // Move back to original position
                            hardwareManager.moveFocusStageInSteps(1);
                            
                            //refStackCC = CrossCorrelationMap.calculateCrossCorrelationMap(snapAndProcess(), refStack, NanoJNormalize);
                            refStackCC = CrossCorrelationMap.calculateCrossCorrelationMap(refStack.getProcessor(2), refStack, NanoJNormalize); // 220131 JE
                            refTopTopCC = CrossCorrelationMap.calculateCrossCorrelationMap(refStack.getProcessor(1), refStack.getProcessor(1), NanoJNormalize); // 220131 JE
                            refBottomBottomCC = CrossCorrelationMap.calculateCrossCorrelationMap(refStack.getProcessor(3), refStack.getProcessor(3), NanoJNormalize); // 220131 JE

                            FloatProcessor refCCbottom = refStackCC.getProcessor(3).convertToFloatProcessor();
                            FloatProcessor refCCmiddle = refStackCC.getProcessor(2).convertToFloatProcessor();
                            FloatProcessor refCCtop = refStackCC.getProcessor(1).convertToFloatProcessor();
                            FloatProcessor refTopTopProc = refTopTopCC.convertToFloatProcessor(); // 220131 JE
                            FloatProcessor refBottomBottomProc = refBottomBottomCC.convertToFloatProcessor(); // 220131 JE
                            
                            Offsets = processor.FWTM(refCCmiddle); // 230209 JE
                            //ReportingUtils.showMessage(Integer.toString(Offsets[0]) + " ," + Integer.toString(Offsets[1]));
                            
                            //refmiddleMean = ImageStatistics.getStatistics(refStack.getProcessor(2)).mean;
                            
                            //refCCbottomMidMax = refCCbottom.getMax();
                            //refCCtopMidMax = refCCtop.getMax();
                            //refCCmidMidMax = refCCmiddle.getMax();
                            
                            // offset maxima because minima usually not at zero
                            //refCCbottomMidMax = refCCbottomMiddle.getMax();
                            //refCCtopMidMax = refCCtopMiddle.getMax();
                            //refCCmidMidMax = refCCmidMid.getMax();*/
                            
                            Peak = processor.PickPlane(refStackCC);
                            VerticalOffsets = processor.OffsetCenters(refStackCC);
                            
                            
                            refCCbottomMidMax = processor.CenterHeightFind3(refCCbottom, Peak); // 220131 JE
                            refCCtopMidMax = processor.CenterHeightFind3(refCCtop, Peak); // 220131 JE
                            refCCmidMidMax = processor.CenterHeightFind3(refCCmiddle, Peak); // 220131 JE
                            refCCTopTopMax = processor.CenterHeightFind3(refTopTopProc, Peak); // 220131 JE
                            refCCBottomBottomMax = processor.CenterHeightFind3(refBottomBottomProc, Peak); // 220131 JE
                            
                            /*
                            refCCbottomMidMax = processor.CenterHeightFind4(refCCbottom, Peak, VerticalOffsets[2], VerticalOffsets[3]); // 230301 JE
                            refCCtopMidMax = processor.CenterHeightFind4(refCCtop, Peak, VerticalOffsets[0], VerticalOffsets[1]); // 230301 JE
                            refCCmidMidMax = processor.CenterHeightFind4(refCCmiddle, Peak, 0, 0); // 230301 JE
                            refCCTopTopMax = processor.CenterHeightFind4(refTopTopProc, Peak, 0, 0); // 230301 JE
                            refCCBottomBottomMax = processor.CenterHeightFind4(refBottomBottomProc, Peak, 0, 0); // 230301 JE
                            */
                            
                            Top = (refCCtopMidMax/refCCTopTopMax); // 220131 JE
                            Bottom = (refCCbottomMidMax/refCCBottomBottomMax); // 220131 JE
                            Middle = (refCCmidMidMax/refCCmidMidMax); // 220131 JE
                              
                            SP = (Top - Bottom) / (Middle + 0.6); // Z-correction setpoint // 220131 JE
                                                        
                            //SP = (refCCtopMidMax - refCCbottomMidMax) / refCCmidMidMax; // Z-correction setpoint
                        
                            driftData.setReferenceStack(refStack);
                            
                            int Plane = (int) Peak[2];
                            currentCenter = processor.PeakFind2(refStackCC.getProcessor(Plane).convertToFloatProcessor(), Peak); // 221012 JE
                            //currentCenter = processor.PeakFind3(refStackCC.getProcessor(Plane).convertToFloatProcessor(), Peak, Offsets); // 230209 JE
                            
                            t = 0;
                            //imCentx = currentCenter[0];
                            //imCenty = currentCenter[1];
                            //ReportingUtils.showMessage("centroid, " + Double.toString(imCentx) + ", " + Double.toString(imCenty));
                            
                            imCentx = refCCmiddle.getWidth()/2f;
                            imCenty = refCCmiddle.getHeight()/2f;
                            //ReportingUtils.showMessage("Half Image, " + Double.toString(imCentx) + ", " + Double.toString(imCenty));
                            UpdateTime = getTimeElapsed() + refUpdate;
                        }

                        PV = 0;
                        currentCenter[0] = 0;
                        currentCenter[1] = 0;
                        
                        ImageProcessor Image = processor.Normalize(snapAndProcess());
                        resultStack = CrossCorrelationMap.calculateCrossCorrelationMap(Image, driftData.getReferenceStack(), NanoJNormalize);
                        driftData.setResultMap(resultStack);
                        /* //for simulations JE
                        i=i+1;
                        try{
                            //ReportingUtils.showMessage(String.format("%04d", i));
                            ImagePlus LoadedImage = opener.openImage("C:\\Users\\joshe\\Documents\\GitHub\\LifeHackDevelopment\\Imlock\\SubPixelShiftOut\\Images\\" + "ShiftedImage" + String.format("%04d", i) + ".tif");
                            //FloatProcessor image = LoadedImage.getProcessor().convertToFloatProcessor();
                            FloatProcessor image = processor.Normalize(LoadedImage.getProcessor().convertToFloatProcessor());
                            resultStack =  CrossCorrelationMap.calculateCrossCorrelationMap(image, driftData.getReferenceStack(), NanoJNormalize);
                        }
                            catch(Exception e){
                            driftData.setReferenceImage(null);
                            driftData.setReferenceStack(new ImageStack());
                            runAcquisition(false);
                            break;
                            }
                        driftData.setResultMap(resultStack);
                        */

                        // Measure XYZ drift
                        FloatProcessor ccSliceBottom = resultStack.getProcessor(3).convertToFloatProcessor();
                        ccSliceMiddle = resultStack.getProcessor(2).convertToFloatProcessor();
                        FloatProcessor ccSliceTop = resultStack.getProcessor(1).convertToFloatProcessor();
                        
                        //double MedianT = ImageStatistics.getStatistics(ImageT).median;
                        
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
                        
                        /*
                        double ccSliceBottomMax = processor.CenterHeightFind4(ccSliceBottom, Peak, VerticalOffsets[2], VerticalOffsets[3]); // 230301 JE
                        double ccSliceTopMax = processor.CenterHeightFind4(ccSliceTop, Peak, VerticalOffsets[0], VerticalOffsets[1]); // 230301 JE
                        double ccSliceMiddleMax = processor.CenterHeightFind4(ccSliceMiddle, Peak, 0, 0); // 230301 JE
                        */
                        Top = (ccSliceTopMax/refCCTopTopMax); // 220131 JE
                        Bottom = (ccSliceBottomMax/refCCBottomBottomMax); // 220131 JE
                        Middle = (ccSliceMiddleMax/refCCmidMidMax); // 220131 JE
                        
                        PV = (Top - Bottom) / (Middle + 0.6);//(MedianT/refmiddleMedian); // eq 5 in McGorty et al. 2013 // 220131 JE

                        //PV = (ccSliceTopMax - ccSliceBottomMax) / ccSliceMiddleMax; // eq 5 in McGorty et al. 2013
                        
                        int Plane = (int) Peak[2];
                        //currentCenter = processor.PeakFind2(resultStack.getProcessor(Plane).convertToFloatProcessor(), Peak); // 221012 JE
                        currentCenter = processor.PeakFind3(resultStack.getProcessor(Plane).convertToFloatProcessor(), Peak, Offsets); // 230209 JE
                        
                        HeightRatio = Math.max(Top,Math.max(Middle,Bottom));
                        
                    }
                    
                    // XY drift correction ONLY 201230 kw
                    else {
                        //if (MDA.isAcquisitionRunning() && !MDA.isPaused() && (MDA.getNextWakeTime() < 1000 || MDA.getNextWakeTime() < getSleep())) {
                        //    ReportingUtils.showMessage("blocked by MDA");
                        //    continue;
                        //}
                        /*
                        i=i+1;
                        //if (i == 49) runAcquisition(false);
                        if (i == 50) i=-49;
                        ImagePlus LoadedImage = opener.openImage("D:\\software\\github\\LifeHackDevelopment\\Imlock\\Jumpy_Reference\\SubPixelShiftOut\\" + "ShiftedImage_" + Integer.toString(i) + ".tif");
                        //ImagePlus LoadedImage = opener.openImage("F:\\NoOptosplit_MainCamBeads_2_2\\Default\\" + "img_channel000_position000_time" + String.format("%09d", i) + "_z000.tif");
                        //ImagePlus LoadedImage = opener.openImage("F:\\ImLock_AddedPost_2\\Default\\" + "img_channel000_position000_time" + String.format("%09d", i) + "_z000.tif");
                        //ImagePlus LoadedImage = opener.openImage("F:\\ImLock_AddedPost_Unscrewed_1\\Default\\" + "img_channel000_position000_time" + String.format("%09d", i) + "_z000.tif");
                        //ImagePlus LoadedImage = opener.openImage("F:\\ImLock_AddedPost_Unscrewed_matchedFP_1\\Default\\" + "img_channel000_position000_time" + String.format("%09d", i) + "_z000.tif");
                        //ImagePlus LoadedImage = opener.openImage("F:\\ImLock_AddedPost_Unscrewed_matchedFP_2\\Default\\" + "img_channel000_position000_time" + String.format("%09d", i) + "_z000.tif");
                        //ImagePlus LoadedImage = opener.openImage("F:\\ImLock_TwinCam_Covered_1\\Default\\" + "img_channel000_position000_time" + String.format("%09d", i) + "_z000.tif");
                        FloatProcessor image = LoadedImage.getProcessor().convertToFloatProcessor();
                        resultImage =  CrossCorrelationMap.calculateCrossCorrelationMap(image, driftData.getReferenceImage(), NanoJNormalize);
                        */

                        resultImage =  CrossCorrelationMap.calculateCrossCorrelationMap(processor.Normalize(snapAndProcess()), driftData.getReferenceImage(), NanoJNormalize);
                        driftData.setResultMap(resultImage);

                        ccSliceMiddle = resultImage.convertToFloatProcessor();
                        
                        Peak = CalculateImageStatistics.getMax(ccSliceMiddle); // 221012 JE
                        //currentCenter =  processor.PeakFind2(ccSliceMiddle, Peak); // 221012 JE
                        currentCenter =  processor.PeakFind3(ccSliceMiddle, Peak, Offsets); // 230209 JE
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
                    
                    Point2D.Double xyError = new Point2D.Double(xErr,-yErr); // -y because of top to bottom image indexing
                    
                    if (driftData.getSwitchXY()){
                        xyError.x = yErr;
                        xyError.y = -xErr;
                    }
                    if (driftData.getflipX()) xyError.x = -xyError.x;
                    if (driftData.getflipY()) xyError.y = -xyError.y;

                    // Convert from pixel units to microns
                    xyError = hardwareManager.convertPixelsToMicrons(xyError);
                    
                    if (MoveSuccess && ((dt*1000)<10*sleep)){
                        xErrSum = xErrSum + xyError.x*dt;
                        yErrSum = yErrSum + xyError.y*dt;
                    }
                    
                    x = 0;
                    y = 0;

                    if (correctionMode == XY || correctionMode == XYZ){
                        x = (1+Bias)*(Lp*xyError.x + Li*xErrSum);
                        y = (1-Bias)*(Lp*xyError.y + Li*yErrSum);
                    }
                    //double zPos = hardwareManager.getMainCore().getPosition();
                    oldTime = getTimeElapsed(); // time of current loop (store for next loop iteration)
                    
                    Point2D.Double xyMove = new Point2D.Double(x,y);
                    
                    // Check if detected movement is within bounds
                    if (((correctionMode == XY || correctionMode == XYZ) && (Math.abs(xyError.x) > threshold || Math.abs(xyError.y) > threshold) || HeightRatio < 0.2) || (correctionMode == Z && HeightRatio < 0.2)) {
                        runAcquisition(false);
                        setChanged();
                        notifyObservers(OUT_OF_BOUNDS_ERROR);
                        driftData.setReferenceImage(null);
                        driftData.setReferenceStack(new ImageStack()); // 190401 kw
                        break;
                    }
                    
                    // Move Z stage to more appropriate position. We get zDrift in microns instead of steps to save later to Data. (added 190403 kw)
                    // Now using PI controller instead of equation in McGorty 2013 paper (220110 kw)
                    if (isRunning() && (correctionMode == Z || correctionMode == XYZ) ) {
                        z_err = SP - PV; // Z-correction error 220110
                        z_errSum = z_errSum + z_err*dt;
                        zDrift = Zp*z_err + Zi*z_errSum;
                        if((Zp!=0 || Zi!=0) && (Math.abs(zDrift)<AMM)) hardwareManager.moveFocusStage(zDrift);
                        oldzErr = z_err;
                        
                    }
                    
                    //Point2D.Double PrePos = hardwareManager.PollStage();
                    // Move XY stage
                    if (isRunning() && (correctionMode == XY || correctionMode == XYZ)){
                        if (Math.abs(xyMove.x)<LMM) xyMove.x = 0;
                        if (Math.abs(xyMove.y)<LMM) xyMove.y = 0;
                        if(xyMove.x!=0 || xyMove.y!=0) MoveSuccess = hardwareManager.moveXYStage(xyMove);
                        //if((xyMove.x !=0 || xyMove.y !=0)) MoveSuccess = hardwareManager.moveXYStage(xyMove);
                        else MoveSuccess = true;
                    }
                    //Point2D.Double PostPos = hardwareManager.PollStage();
                    
                    // Add data //changed to switch statement from ifs 220128 JE
                    switch(correctionMode){
                        case Z:
                             driftData.addZShift(zDrift, z_err, getTimeElapsed(), Zp, Zi, refUpdate);
                             break;
                        case XY:
                            driftData.addXYshift(xyError.x, xyError.y, getTimeElapsed(), Lp, Li, refUpdate);
                            //driftData.addXYshift(xErr, yErr, getTimeElapsed(), Lp, Li, refUpdate);
                            break;
                        case XYZ:
                            driftData.addXYZshift(xyError.x, xyError.y, zDrift, z_err, getTimeElapsed(), Zp, Zi, Lp, Li, refUpdate);
                            //driftData.addXYZshift(xyError.x, xyMove.x, zDrift, z_err, getTimeElapsed(), Zp, Zi, Lp, Li, refUpdate);
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
    
    // added 221208 JE
    public void setBias(double Bias){
        this.Bias = Bias;
    }
    
    // added 230404 JE
    public void setAMM(double AMM){
        this.AMM = AMM/1000;
    }
    
    // added 230404 JE
    public void setLMM(double LMM){
        this.LMM = LMM/1000;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }
}