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
import java.util.ArrayList;
import java.util.Observable;
import nanoj.core.java.image.analysis.CalculateImageStatistics;
import org.micromanager.internal.MMStudio;
import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.acquisition.AcquisitionManager;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.PositionListManager;
import org.micromanager.PositionList;
import org.micromanager.MultiStagePosition;
import org.micromanager.StagePosition;
import nanoj.core.java.image.calculator.ImageCalculator;

public class DriftCorrection extends Observable implements Runnable {
    private boolean alive = true;
    private boolean runAcquisition = false;
    private boolean MoveSuccess = true;
    private DriftCorrectionHardware hardwareManager;
    private DriftCorrectionData driftData;
    private DriftCorrectionProcess processor;
    private MMStudio studio;
    private AcquisitionWrapperEngine MDA;
    private AcquisitionManager MDAManager;
    private SequenceSettings SeqSettings;
    private PositionListManager PositionsManager;
    private PositionList Positions;
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
    private double xErr = 0; // 220119 JE
    private double yErr = 0; // 220119 JE
    private double xErrSum = 0; // 220414 JE
    private double yErrSum = 0; // 220414 JE
    private double oldzErr = 0; // 220906 JE
    private double Top = 0; // 220131 JE
    private double Bottom = 0; // 220131 JE
    private double Middle = 0; // 220131 JE
    private double HeightRatio = 0; // 220131 JE 
    private boolean StartMDA = false; // 221025 JE
    private double WaitLeft = 0; // 221025 JE
    private int MeasNum = 3; // 221130 JE
    
    private double refCCbottomMidMax = 0; // 220201 JE
    private double refCCtopMidMax = 0; // 220201 JE
    private double refCCmidMidMax = 0; // 220201 JE
    private double refCCTopTopMax = 0; // 220201 JE
    private double refCCBottomBottomMax = 0; // 220201 JE
    //private double refmiddleMedian = 0; // 220916 JE
    float[] Peak = null; //220926 JE
    double[] currentCenter = null; //221012 JE


    public DriftCorrection(DriftCorrectionHardware manager, DriftCorrectionData data, DriftCorrectionProcess processor) {
        hardwareManager = manager;
        driftData = data;
        this.processor = processor;
        studio = MMStudio.getInstance();
        MDA = studio.getAcquisitionEngine();
        MDAManager = studio.getAcquisitionManager();
        SeqSettings = MDAManager.getAcquisitionSettings();
        PositionsManager = studio.getPositionListManager();
        Positions = PositionsManager.getPositionList();
    }

    @Override
    public void run() {
        try {
            while (itIsAlive()) {
                while (isRunning()) {
                    long startRun = System.currentTimeMillis();

                    if (MDA.isAcquisitionRunning() && !MDA.isPaused() && ((SeqSettings.usePositionList() && Positions.getNumberOfPositions() > 0) || SeqSettings.useSlices())){ // Stops ImLock trying to correct for deliberate moves from MDA 221018 JE
                        if (!StartMDA){
                            SeqSettings = MDAManager.getAcquisitionSettings();
                            PositionsManager = studio.getPositionListManager();
                            Positions = PositionsManager.getPositionList();
                        }
                        WaitLeft = (MDA.getNextWakeTime() - System.nanoTime() / 1000000.0);
                        if(hardwareManager.getMainCoreBusy() || WaitLeft < 1200) {
                            continue;
                        }
                    }
                    
                    if (MDA.isAcquisitionRunning()){
                        StartMDA = true;
                        driftData.setStartMDA(1);
                    }
                    
                    if (StartMDA && !MDA.isAcquisitionRunning()) { // Returns stage to start position after MDA 
                        StartMDA = false;
                        driftData.setStartMDA(0);
                        if (SeqSettings.usePositionList() && Positions.getNumberOfPositions() > 0) {
                            x = Positions.getPosition(0).getX();
                            y = Positions.getPosition(0).getY();
                            MultiStagePosition.goToPosition(Positions.getPosition(0), hardwareManager.getMainCore());
                            continue;
                        }
                        else if (SeqSettings.useSlices() && SeqSettings.relativeZSlice()) {
                            hardwareManager.AbsMoveFocusStage(SeqSettings.zReference()); 
                        }
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
                            
                            hardwareManager.moveFocusStageInSteps(0.2);
                            ImageProcessor MidTop = snapAndProcess();
                        
                            // Move one stepSize above focus, snap and add to image stack
                            hardwareManager.moveFocusStageInSteps(0.8);
                            refStack.addSlice(TOP, snapAndProcess(), 0);
                            
                            hardwareManager.moveFocusStageInSteps(-1.2);
                            ImageProcessor MidBottom = snapAndProcess();

                            // Move two stepSizes below the focus, snap and add to image stack
                            hardwareManager.moveFocusStageInSteps(-0.8);
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
                            
                            //refmiddleMedian = ImageStatistics.getStatistics(refStack.getProcessor(2)).median;
                            
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
                            UpdateTime = getTimeElapsed() + refUpdate;
                            
                            //////////////////////////////////////////////////
                            ImageStack MidTopStackCC = CrossCorrelationMap.calculateCrossCorrelationMap(MidTop, refStack, true);
                            ImageStack MidBottomStackCC = CrossCorrelationMap.calculateCrossCorrelationMap(MidBottom, refStack, true);
                            
                            Peak = processor.PickPlane(MidTopStackCC);
                            currentCenter = processor.PeakFind2(MidTopStackCC.getProcessor(Plane).convertToFloatProcessor(), Peak); // 221202 JE
                            double MidTopShift = (currentCenter[0] - imCentx) / (0.2*hardwareManager.getStepSize());
                                                       
                            Peak = processor.PickPlane(MidBottomStackCC);
                            currentCenter = processor.PeakFind2(MidBottomStackCC.getProcessor(Plane).convertToFloatProcessor(), Peak); // 221202 JE
                            double MidBottomShift = (currentCenter[0] - imCenty) / (0.2*hardwareManager.getStepSize());
                            
                            //////////////////////////////////////////////////
                        }
                        
                        PV = 0;
                        currentCenter[0] = 0;
                        currentCenter[1] = 0;
                        for(int i = 0; i < MeasNum; i++) { // Loop to average multiple position measurements for each correction 221130 JE
                            
                            ImageProcessor ImageT = snapAndProcess();
                            resultStack = CrossCorrelationMap.calculateCrossCorrelationMap(ImageT, driftData.getReferenceStack(), true);
                            driftData.setResultMap(resultStack);

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

                            Top = (ccSliceTopMax/refCCTopTopMax); // 220131 JE
                            Bottom = (ccSliceBottomMax/refCCBottomBottomMax); // 220131 JE
                            Middle = (ccSliceMiddleMax/refCCmidMidMax); // 220131 JE
                        
                            double imPV = (Top - Bottom) / (Middle + 0.6);//(MedianT/refmiddleMedian); // eq 5 in McGorty et al. 2013 // 220131 JE
                            PV = PV + imPV;

                            //PV = (ccSliceTopMax - ccSliceBottomMax) / ccSliceMiddleMax; // eq 5 in McGorty et al. 2013
                        
                            int Plane = (int) Peak[2];
                            double[] imageCenter = processor.PeakFind2(resultStack.getProcessor(Plane).convertToFloatProcessor(), Peak); // 221012 JE
                            currentCenter[0] = currentCenter[0] + imageCenter[0];
                            currentCenter[1] = currentCenter[1] + imageCenter[1];
                        }
                        
                        PV = PV/MeasNum;
                        currentCenter[0] = currentCenter[0]/MeasNum;
                        currentCenter[1] = currentCenter[1]/MeasNum;
                        
                        HeightRatio = Math.max(Top,Math.max(Middle,Bottom));
                        
                        
                    }
                    
                    // XY drift correction ONLY 201230 kw
                    else {
                        //if (MDA.isAcquisitionRunning() && !MDA.isPaused() && (MDA.getNextWakeTime() < 1000 || MDA.getNextWakeTime() < getSleep())) {
                        //    ReportingUtils.showMessage("blocked by MDA");
                        //    continue;
                        //}
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
                    
                    Point2D.Double xyError = new Point2D.Double(xErr,-yErr);
                    
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
                        x = Lp*xyError.x + Li*xErrSum;
                        y = Lp*xyError.y + Li*yErrSum;
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
                        driftData.clearResultMap(); // 190412 kw
                        break;
                    }
                    
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
                        //if (Math.abs(xyMove.x) < 0.001) xyMove.x=0;
                        //if (Math.abs(xyMove.y) < 0.001) xyMove.y=0;
                        //if((Lp!=0 || Li!=0) && (Math.abs(xyMove.x)>=0.001 || Math.abs(xyMove.y)>=0.001)) MoveSuccess = hardwareManager.moveXYStage(xyMove);
                        if((Lp!=0 || Li!=0)) MoveSuccess = hardwareManager.moveXYStage(xyMove);
                        else MoveSuccess = false;
                    }

                    // Updates Position list used for Multi Dimentional Acquisition
                    if (MDA.isAcquisitionRunning() && SeqSettings.usePositionList() && Positions.getNumberOfPositions() > 1) {
                        PositionList posList = studio.uiManager().getPositionList(); // workaround from https://forum.image.sc/t/micro-manager-mda-doesnt-use-updated-positionlist-during-acquisition/48095/5 221101 JE
                        MultiStagePosition[] list = posList.getPositions();
                        double ZoffsetPos = hardwareManager.getMainCore().getPosition(hardwareManager.getFocusDevice()) - Positions.getPosition(0).getZ();
                        double[] xpos = new double[1];
                        double[] ypos = new double[1];
                        hardwareManager.getMainCore().getXYPosition(hardwareManager.getXYStage(), xpos, ypos);
                        double Xoffset = xpos[0] - Positions.getPosition(0).getX();
                        double Yoffset = ypos[0] - Positions.getPosition(0).getY();
                        for (MultiStagePosition msp : list) {
                            StagePosition spz = msp.get(hardwareManager.getFocusDevice());
                            StagePosition spxy = msp.get(hardwareManager.getXYStage());
                            spz.set1DPosition(hardwareManager.getFocusDevice(), spz.get1DPosition() + ZoffsetPos);
                            spxy.set2DPosition(hardwareManager.getXYStage(), spxy.get2DPositionX() + Xoffset, spxy.get2DPositionY() + Yoffset);
                        }
                        Positions.setPositions(list);
                        posList.setPositions(list);
                        MDA.setPositionList(Positions);
                        PositionsManager.setPositionList(Positions);
                    }
                    if (MDA.isAcquisitionRunning() && SeqSettings.useSlices() && SeqSettings.relativeZSlice()) {
                        double ZoffsetSlice = hardwareManager.getMainCore().getPosition(hardwareManager.getFocusDevice()) - SeqSettings.zReference();
                        SeqSettings.zReference = hardwareManager.getMainCore().getPosition(hardwareManager.getFocusDevice());
                        //SeqSettings.Builder.zReference(hardwareManager.getMainCore().getPosition(hardwareManager.getFocusDevice()));
                        ArrayList<Double> Slices = SeqSettings.slices();
                        for (int i = 0; i < Slices.size()-1; i++) {
                            Slices.set(i,Slices.get(i) + ZoffsetSlice);
                        }
                    }
                    
                    // Add data //changed to switch statement from ifs 220128 JE
                    switch(correctionMode){
                        case Z:
                             driftData.addZShift(zDrift, z_err, getTimeElapsed());
                             break;
                        case XY:
                            if (driftData.Tune){
                                driftData.addXYshift(xyError.x, xyError.y, getTimeElapsed());
                            }
                            else driftData.addXYshift((xyMove.x), (xyMove.y), getTimeElapsed());
                            break;
                        case XYZ:
                            if (driftData.Tune){
                                driftData.addXYZshift(xyError.x, xyError.y, zDrift, z_err, getTimeElapsed());
                                //driftData.addXYZshift(xyError.x, zPos, zDrift, z_err, getTimeElapsed());
                            }
                            else driftData.addXYZshift((xyMove.x), (xyMove.y), zDrift, z_err, getTimeElapsed());
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
    
    // added 220118 JE
    public void setMeasNum(int MeasNum){
        this.MeasNum = MeasNum;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }  
}