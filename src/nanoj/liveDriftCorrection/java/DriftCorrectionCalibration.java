package nanoj.liveDriftCorrection.java;

import ij.ImagePlus;
import ij.ImageStack;
//import ij.plugin.ZProjector;
import ij.process.FloatProcessor;
import nanoj.core.java.array.ArrayCasting;
import nanoj.core.java.image.drift.EstimateShiftAndTilt;
import nanoj.core.java.image.transform.CrossCorrelationMap;
//import nanoj.core.java.image.analysis.CalculateImageStatistics;
import org.micromanager.internal.utils.ReportingUtils;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.Observable;
import java.text.DecimalFormat;

public class DriftCorrectionCalibration extends Observable implements Runnable {
    private DriftCorrectionHardware hardwareManager;
    private DriftCorrectionProcess processor;
    private DriftCorrectionData driftData;

    private boolean alive = true;
    private boolean runAcquisition = false;
    private double step = 10;
    //private double backgroundStep = 50;
    private int totalSteps = 20;
    private double xMovement;
    private double yMovement;
    private double scale;
    private double angle; 
    private boolean flipX;
    
    private ImageStack driftStack;
    private ImagePlus driftPlus = new ImagePlus();
    private ImageStack plainStack;
    private ImagePlus plainPlus = new ImagePlus();

    private AffineTransform calibration;

    public static final String NO_MOVEMENT_ERROR = "No movement Detected!";
    public static final String NO_X_MOVEMENT_ERROR = "No movement detected in the X axis!";
    public static final String NO_Y_MOVEMENT_ERROR = "No movement detected in the Y axis!";
    public static final String CAL_ERROR = "Background subtraction routine interrupted.";
    private static final String SCALING = "Scale: ";
    private static final String ANGLE = "Angle: ";
    private static final String FLIP = "Flip X: ";
    private static final String procedure_succeeded = "The procedure has succeeded!";
    DecimalFormat df = new DecimalFormat("#.##");

    public DriftCorrectionCalibration(DriftCorrectionHardware hardware, DriftCorrectionProcess processor, DriftCorrectionData driftData) {
        hardwareManager = hardware;
        this.processor = processor;
        this.driftData = driftData;
    }

    @Override
    public void run () {
        try {
            while(itIsAlive()) {
                while(isRunning()) {
                    ArrayList<FloatProcessor> images = new ArrayList<FloatProcessor>();

                    double travel = totalSteps * step;
                    // Move along X and Y in steps until reaching totalSteps range
                    // i = 0 ensures we have a picture at the starting position
                    // i <= travel ensures we reach the total travel range
                    for (double i = 0; i<= travel; i += step) {
                        hardwareManager.moveXYStage(step, step);
                        images.add(snapAndProcess());
                    }
                    
                    // Move stage back to original position
                    hardwareManager.moveXYStage(-travel, -travel);

                    // Calculate the median XY movement for each set of images and register it to xmovement and ymovement
                    calculateMovement(images);

                    // Make sure we detected something
                    if (xMovement == 0 && yMovement == 0) {
                        ReportingUtils.showError(NO_MOVEMENT_ERROR);
                        runAcquisition(false);
                    }
                    else if (xMovement == 0) {
                        ReportingUtils.showError(NO_X_MOVEMENT_ERROR);
                        runAcquisition(false);
                    }
                    else if (yMovement == 0) {
                        ReportingUtils.showError(NO_Y_MOVEMENT_ERROR);
                        runAcquisition(false);
                    }
                
                    // We now calculate the angle of the observed movement versus the original movement we told it to perform.
                    // Subtract from the expected angle (45° degrees or pi/4 radians) the observed angle
                    angle = (Math.PI/4) - Math.atan(yMovement/xMovement);

                    // We calculate the vector magnitudes and deduce the scale from that
                    scale = Math.sqrt((Math.pow(step, 2))+(Math.pow(step, 2)))/
                            Math.sqrt(Math.pow(xMovement, 2) + Math.pow(yMovement, 2));

                    // If we are working with negative xMovement values, then the coordinates need to be flipped 180 degrees
                    flipX = xMovement < 0;

                    // Create affine transform
                    calibration = createCalibration(scale , angle, flipX);
                    
                    hardwareManager.setCalibration(calibration);
                    
                    // tell GUI you're done with the cal routine 201231 kw
                    setChanged();
                    notifyObservers();
                    
                    runAcquisition(false);
                }
                java.lang.Thread.sleep(500);
            }
        } catch (Exception e) {
            ReportingUtils.showError(e, CAL_ERROR);
        } finally {
            notifyObservers();
            runAcquisition(false);
        }
    }
    
    /*public boolean calibrate() throws Exception {
        ArrayList<FloatProcessor> images = new ArrayList<FloatProcessor>();

        double travel = totalSteps * step;

        // Move along X and Y in steps until reaching totalSteps range
        // i = 0 ensures we have a picture at the starting position
        // i <= travel ensures we reach the total travel range
        for (double i = 0; i<= travel; i += step) {
            hardwareManager.moveXYStage(step, step);
            images.add(snapAndProcess());
        }

        // Move stage back to original position
        hardwareManager.moveXYStage(-travel, -travel);

        // Calculate the median XY movement for each set of images and register it to xmovement and ymovement
        calculateMovement(images);

        // Make sure we detected something
        if (xMovement == 0 && yMovement == 0) {
            ReportingUtils.showError(NO_MOVEMENT_ERROR);
            return false;
        }
        else if (xMovement == 0) {
            ReportingUtils.showError(NO_X_MOVEMENT_ERROR);
            return false;
        }
        else if (yMovement == 0) {
            ReportingUtils.showError(NO_Y_MOVEMENT_ERROR);
            return false;
        }

        // We now calculate the angle of the observed movement versus the original movement we told it to perform.
        // Subtract from the expected angle (45° degrees or pi/4 radians) the observed angle
        angle = (Math.PI/4) - Math.atan(yMovement/xMovement);

        // We calculate the vector magnitudes and deduce the scale from that
        scale = Math.sqrt((Math.pow(step, 2))+(Math.pow(step, 2)))/
                Math.sqrt(Math.pow(xMovement, 2) + Math.pow(yMovement, 2));

        // If we are working with negative xMovement values, then the coordinates need to be flipped 180 degrees
        flipX = xMovement < 0;

        // Create affine transform
        calibration = createCalibration(scale , angle, flipX);

        // So far, our transform takes stage units and converts to pixels, but we want the inverse of that process
        //calibration.invert();
        
        return true;
    }*/

    /*public FloatProcessor obtainBackgroundImage() throws Exception {

        driftData.setShowLatest(true);
        
        ImageStack stack = new ImageStack(hardwareManager.getROI().width,  hardwareManager.getROI().height);

        //double fieldSize = hardwareManager.getROI().getWidth();
        //fieldSize = -hardwareManager.convertPixelsToMicrons(fieldSize); // negative added 190418 kw
        double fieldSize = -backgroundStep; // hack 201223

        int halfStep = 2;
        
        for (int i = 0; i<=halfStep*2; i++) {
            for (int j = 0; j<=halfStep*2; j++) {
                hardwareManager.snap();
                stack.addSlice(hardwareManager.getImage());
                hardwareManager.moveXYStage(fieldSize, 0);
            }
            hardwareManager.moveXYStage(-fieldSize*(halfStep*2+1), fieldSize);
        }
        hardwareManager.moveXYStage(halfStep*fieldSize, -(halfStep+1)*fieldSize);

        ZProjector projector = new ZProjector(new ImagePlus("Background Image",stack));
        projector.setMethod(ZProjector.MEDIAN_METHOD);
        projector.doProjection();

        return projector.getProjection().getProcessor().convertToFloatProcessor();
    }*/

    // This method exists because the camera we are using sometimes doesn't respect the ROI size which clashes with
    // the background image
    private FloatProcessor snapAndProcess() {
        FloatProcessor snap = null;
        try {
            hardwareManager.snap();
            snap = processor.process(hardwareManager.getImage());
        } catch (Exception e) {
            if (e.getMessage().equals(DriftCorrectionProcess.MISMATCHED_IMAGE_SIZES))
                snap = snapAndProcess();
        }
        return snap;
    }

    public static double median(float[] m) {
        int middle = m.length/2;
        if (m.length%2 == 1) {
            return m[middle];
        } else {
            return (m[middle-1] + m[middle]) / 2d;
        }
    }

    public void clear() {
        xMovement = 0;
        yMovement = 0;
        calibration = new AffineTransform();
    }

    public static AffineTransform createCalibration(double scale, double angle, boolean rotate) {
        AffineTransform newCalibration = new AffineTransform();

        newCalibration.scale(scale, scale);
        newCalibration.rotate(-angle);

        // If we are working with negative xMovement values, then the coordinates need to be flipped 180 degrees
        if(rotate) newCalibration.quadrantRotate(2);

        newCalibration.quadrantRotate(1); // 190418 kw
        
        return newCalibration;
    }

    private void calculateMovement(ArrayList<FloatProcessor> images) {
        ArrayList<Float> xShift = new ArrayList<Float>();
        ArrayList<Float> yShift = new ArrayList<Float>();

        // Calculate XY pixel displacement for each translation
        for (int i = 0; i < images.size()-1; i++) {
            FloatProcessor map =
                    CrossCorrelationMap.calculateCrossCorrelationMap(
                            images.get(i),
                            images.get(i+1),
                            true)
                            .convertToFloatProcessor();

            if (map == null) {
                continue;
            }
            
            if (i==0){
                ImageStack dStack = new ImageStack(map.getWidth(),map.getHeight());
                dStack.addSlice(map);
                driftStack = dStack;
            }
            
            driftStack.addSlice(map);

            float[] shift = EstimateShiftAndTilt.getMaxFindByOptimization(map);
            shift = new float[] {
                    (shift[0] - map.getWidth()/2),
                    (shift[1] - map.getHeight()/2)
            };
            xShift.add(shift[0]);
            yShift.add(shift[1]);
        }
        
        driftPlus.setStack(driftStack);
        driftPlus.show();

        // Get Median values to minimize stage error
        //Collections.sort(xShift);
        double x = median(ArrayCasting.toArray(xShift, 0f));
        //Collections.sort(yShift);
        double y = median(ArrayCasting.toArray(yShift, 0f));

        xMovement = x;
        yMovement = y;
    }

    //////////////////////////// Getters/Setters

    // is thread alive 201231 kw
    public boolean itIsAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }
    
    // is cal routine running 201231 kw
    public void runAcquisition(boolean run) {
        this.runAcquisition = run;
    }
    
    public boolean isRunning() {
        return runAcquisition;
    }
    
    /*public double getBackgroundStep(){
        return backgroundStep;
    }
    
    public void setBackgroundStep(double backgroundStep){
        this.backgroundStep = backgroundStep;
    }*/
    
    public double getStep() {
        return step;
    }

    public void setStep(double step) {
        this.step = step;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int totalSteps){
        this.totalSteps = totalSteps;
    }

    public AffineTransform getCalibration() {
        return calibration;
    }

    public double getScale() {
        return scale;
    }

    public double getAngle() {
        return angle;
    }

    public boolean getFlipX() {
        return flipX;
    }
}
