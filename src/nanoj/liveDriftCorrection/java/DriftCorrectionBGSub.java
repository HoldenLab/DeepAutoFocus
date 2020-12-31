package nanoj.liveDriftCorrection.java;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.ZProjector;
import ij.process.FloatProcessor;
import nanoj.core.java.array.ArrayCasting;
import nanoj.core.java.image.drift.EstimateShiftAndTilt;
import nanoj.core.java.image.transform.CrossCorrelationMap;
import nanoj.core.java.image.analysis.CalculateImageStatistics;
import org.micromanager.internal.utils.ReportingUtils;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Observable;

/* Author: Kevin Whitley
Date created: 201231

This class is meant to separate DriftCorrectionCalibration.java into two parts: one for calibration
and one for background subtraction. The goal is to make both 'runnable'.
*/

public class DriftCorrectionBGSub extends Observable implements Runnable {
    private DriftCorrectionHardware hardwareManager;
    private DriftCorrectionProcess processor;
    private DriftCorrectionData driftData;
    
    private boolean alive = true;
    private boolean runAcquisition = false;
    private double backgroundStep = 50;
    private FloatProcessor bgImage = null;
    
    public static final String BGSUB_ERROR = "Background subtraction routine interrupted.";
    private static final String procedure_succeeded = "The procedure has succeeded!";
    
    public DriftCorrectionBGSub(DriftCorrectionHardware hardware, DriftCorrectionProcess processor, DriftCorrectionData driftData) {
        hardwareManager = hardware;
        this.processor = processor;
        this.driftData = driftData;
    }
    
    @Override
    public void run() {
        try {
            while(itIsAlive()) {
                while(isRunning()) {
                    ImageStack stack = new ImageStack(hardwareManager.getROI().width,  hardwareManager.getROI().height);
            
                    double fieldSize = -backgroundStep; // hack 201223
                    int halfStep = 2;
            
                    hardwareManager.moveXYStage(-fieldSize*halfStep,-fieldSize*halfStep);
                
                    for (int i = 0; i<=halfStep*2; i++) {

                        for (int j = 0; j<=halfStep*2; j++) {
                        hardwareManager.snap();
                        stack.addSlice(hardwareManager.getImage());
                        hardwareManager.moveXYStage(fieldSize, 0);
                        }
                    hardwareManager.moveXYStage(-fieldSize*(halfStep*2+1), fieldSize);
                    }
                    hardwareManager.moveXYStage(halfStep*fieldSize, -(halfStep+1)*fieldSize);
                
                    // set newly acquired background image
                    setBackgroundImage(stack);
                    driftData.setBackgroundImage(processor.clip(bgImage));

                    ReportingUtils.showMessage(procedure_succeeded);

                    runAcquisition(false);
                }
                java.lang.Thread.sleep(500);
            }
        } catch (Exception e) {
            ReportingUtils.showError(e, BGSUB_ERROR);
        } finally {
            notifyObservers();
            runAcquisition(false);
        }
    }
            
    public void runAcquisition(boolean run) {
        this.runAcquisition = run;
    }
    
    public boolean isRunning() {
        return runAcquisition;
    }
    
    public boolean itIsAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }
    
    public void setBackgroundImage(ImageStack stack) {

        ZProjector projector = new ZProjector(new ImagePlus("Background Image",stack));
        projector.setMethod(ZProjector.MEDIAN_METHOD);
        projector.doProjection();

        this.bgImage = projector.getProjection().getProcessor().convertToFloatProcessor();
    }
    
    public FloatProcessor getBackgroundImage() {
        return bgImage;
    }

    public double getBackgroundStep(){
        return backgroundStep;
    }
    
    public void setBackgroundStep(double backgroundStep){
        this.backgroundStep = backgroundStep;
    }
}
