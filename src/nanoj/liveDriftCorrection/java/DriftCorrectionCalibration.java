package nanoj.liveDriftCorrection.java;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import nanoj.core.java.array.ArrayCasting;
import nanoj.core.java.image.transform.CrossCorrelationMap;
import org.micromanager.internal.utils.ReportingUtils;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.Observable;
import java.text.DecimalFormat;
import nanoj.core.java.image.analysis.CalculateImageStatistics;

public class DriftCorrectionCalibration extends Observable implements Runnable {
    private DriftCorrectionHardware hardwareManager;
    private DriftCorrectionProcess processor;
    private DriftCorrectionData driftData;

    private boolean alive = true;
    private boolean runAcquisition = false;
    private double step = 10;
    //private double backgroundStep = 50;
    private int totalSteps = 20;
    private double xMovementXY;
    private double yMovementXY;
    private double xMovementX;
    private double yMovementX;
    private double xMovementY;
    private double yMovementY;
    private double scale;
    private double angle; 
    //private boolean flipX = false;
    //private boolean flipY = false;
    
    
    private ImageStack driftStackX;
    private ImageStack driftStackY;
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
                    driftData.setflipX(false);
                    driftData.setflipY(false);
                    driftData.setSwitchXY(false);
                    
                    double travel = totalSteps * step;
                    // Move along X and Y in steps until reaching totalSteps range
                    // i = 0 ensures we have a picture at the starting position
                    // i <= travel ensures we reach the total travel range
                    for (double i = 0; i<= travel; i += step) {
                        hardwareManager.moveXYStage(step, 0);
                        images.add(processor.Normalize(snapAndProcess()));
                        hardwareManager.moveXYStage(0, step);
                        images.add(processor.Normalize(snapAndProcess()));
                    }
                    
                    // Move stage back to original position
                    hardwareManager.moveXYStage(-travel, -travel);

                    // Calculate the median XY movement for each set of images and register it to xmovement and ymovement
                    calculateMovement(images);

                    // Make sure we detected something
                    if (xMovementXY == 0 && yMovementXY == 0) {
                        ReportingUtils.showError(NO_MOVEMENT_ERROR);
                        runAcquisition(false);
                    }
                    else if (xMovementXY == 0) {
                        ReportingUtils.showError(NO_X_MOVEMENT_ERROR);
                        runAcquisition(false);
                    }
                    else if (yMovementXY == 0) {
                        ReportingUtils.showError(NO_Y_MOVEMENT_ERROR);
                        runAcquisition(false);
                    }
                    
                    if (Math.abs(xMovementX)<Math.abs(yMovementX) && Math.abs(yMovementY)<Math.abs(xMovementY)){
                        driftData.setSwitchXY(true);
                        double temp = xMovementXY;
                        xMovementXY = yMovementXY;
                        yMovementXY = temp;
                        
                        temp = xMovementX;
                        xMovementX = yMovementX;
                        yMovementX = temp;
                        
                        temp = xMovementY;
                        xMovementY = yMovementY;
                        yMovementY = temp;
                        
                    }
                    //if ((!driftData.getSwitchXY() && xMovementX < 0) || (driftData.getSwitchXY() && xMovementX > 0)){
                    if (xMovementX < 0){
                        driftData.setflipX(true);
                        xMovementXY = -xMovementXY;
                        
                    }
                    //if ((!driftData.getSwitchXY() && yMovementY < 0) || (driftData.getSwitchXY() && xMovementY < 0)){
                    if (yMovementY < 0){
                        driftData.setflipY(true);
                        yMovementXY = -yMovementXY;
                        
                    }
                
                    // We now calculate the angle of the observed movement versus the original movement we told it to perform.
                    // Subtract from the expected angle (45° degrees or pi/4 radians) the observed angle
                    angle = (Math.PI/4) - Math.atan(yMovementXY/xMovementXY);
                    //angle = 0; //for simulations JE

                    // We calculate the vector magnitudes and deduce the scale from that
                    scale = Math.sqrt((Math.pow(step, 2))+(Math.pow(step, 2)))/
                            Math.sqrt(Math.pow(xMovementXY, 2) + Math.pow(yMovementXY, 2));
                    //scale = 0.07; //for simulations JE

                    // If we are working with negative xMovement values, then the coordinates need to be flipped 180 degrees
                    //flipX = xMovement < 0;

                    // Create affine transform
                    calibration = createCalibration(scale , angle);
                    
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

    public static double median(double[] m) {
        int middle = m.length/2;
        if (m.length%2 == 1) {
            return m[middle];
        } else {
            return (m[middle-1] + m[middle]) / 2d;
        }
    }

    public void clear() {
        xMovementXY = 0;
        yMovementXY = 0;
        xMovementX = 0;
        yMovementX = 0;
        xMovementY = 0;
        yMovementY = 0;
        calibration = new AffineTransform();
    }

    public static AffineTransform createCalibration(double scale, double angle) {
        AffineTransform newCalibration = new AffineTransform();

        newCalibration.scale(scale, scale);
        newCalibration.rotate(-angle);

        newCalibration.quadrantRotate(1); // 190418 kw
        
        return newCalibration;
    }

    private void calculateMovement(ArrayList<FloatProcessor> images) {
        ArrayList<Double> xShiftX = new ArrayList<>();
        ArrayList<Double> yShiftX = new ArrayList<>();
        ArrayList<Double> xShiftXY = new ArrayList<>();
        ArrayList<Double> yShiftXY = new ArrayList<>();
        ArrayList<Double> xShiftY = new ArrayList<>();
        ArrayList<Double> yShiftY = new ArrayList<>();

        // Calculate XY pixel displacement for each translation
        for (int i = 0; i < images.size()-2; i=i+2) {
            FloatProcessor mapX =
                    CrossCorrelationMap.calculateCrossCorrelationMap(
                            images.get(i),
                            images.get(i+1),
                            false)
                            .convertToFloatProcessor();
            FloatProcessor mapY =
                    CrossCorrelationMap.calculateCrossCorrelationMap(
                            images.get(i+1),
                            images.get(i+2),
                            false)
                            .convertToFloatProcessor();

            if (mapX == null) {
                continue;
            }
            
            if (i==0){
                ImageStack dStackX = new ImageStack(mapX.getWidth(),mapX.getHeight());
                dStackX.addSlice(mapX);
                driftStackX = dStackX;
                ImageStack dStackY = new ImageStack(mapY.getWidth(),mapY.getHeight());
                dStackY.addSlice(mapY);
                driftStackY = dStackY;
            }
            
            driftStackX.addSlice(mapX);
            driftStackY.addSlice(mapY);

            float[] peakX = CalculateImageStatistics.getMax(mapX);
            double[] shiftX =  processor.PeakFind2(mapX, peakX);
            float[] peakY = CalculateImageStatistics.getMax(mapY);
            double[] shiftY =  processor.PeakFind2(mapY, peakY);
            
            shiftX[0] = shiftX[0] - mapX.getWidth()/2;
            shiftX[1] = shiftX[1] - mapX.getHeight()/2;
            shiftY[0] = shiftY[0] - mapX.getWidth()/2;
            shiftY[1] = shiftY[1] - mapX.getHeight()/2;
            
            double[] shiftXY = new double[] {
                (shiftY[0] + shiftX[0]),
                (shiftY[1] + shiftX[1])
            };
            
            xShiftX.add(shiftX[0]);
            yShiftX.add(shiftX[1]);
            xShiftXY.add(shiftXY[0]);
            yShiftXY.add(shiftXY[1]);
            xShiftY.add(shiftY[0]);
            yShiftY.add(shiftY[1]);
        }
        
        driftPlus.setStack(driftStackY);
        driftPlus.show();

        // Get Median values to minimize stage error
        //Collections.sort(xShift);
        xMovementXY = median(ArrayCasting.toArray(xShiftXY, 0d));
        //Collections.sort(yShift);
        yMovementXY = median(ArrayCasting.toArray(yShiftXY, 0d));
        
        xMovementX = median(ArrayCasting.toArray(xShiftX, 0d));
        yMovementX = median(ArrayCasting.toArray(yShiftX, 0d));
        xMovementY = median(ArrayCasting.toArray(xShiftY, 0d));
        yMovementY = median(ArrayCasting.toArray(yShiftY, 0d));

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
}
