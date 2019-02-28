package nanoj.liveDriftCorrection.java;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.ZProjector;
import ij.process.FloatProcessor;
import nanoj.core.java.array.ArrayCasting;
import nanoj.core.java.image.drift.EstimateShiftAndTilt;
import nanoj.core.java.image.transform.CrossCorrelationMap;
import org.micromanager.internal.utils.ReportingUtils;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collections;

public class DriftCorrectionCalibration {
    private DriftCorrectionHardware hardwareManager;
    private DriftCorrectionProcess processor;

    private double step = 0.5;
    private int totalSteps = 10;
    private double xMovement;
    private double yMovement;
    private double scale;
    private double angle;
    private boolean flipX;

    private AffineTransform calibration;

    public static final String NO_MOVEMENT_ERROR = "No movement Detected!";
    public static final String NO_X_MOVEMENT_ERROR = "No movement detected in the X axis!";
    public static final String NO_Y_MOVEMENT_ERROR = "No movement detected in the Y axis!";

    public DriftCorrectionCalibration(DriftCorrectionHardware hardware, DriftCorrectionProcess processor) {
        hardwareManager = hardware;
        this.processor = processor;
    }


    public boolean calibrate() throws Exception {
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
        calibration.invert();

        return true;
    }

    public FloatProcessor obtainBackgroundImage() throws Exception {
        ImageStack stack = new ImageStack(hardwareManager.getROI().width,  hardwareManager.getROI().height);

        double fieldSize = hardwareManager.getROI().getWidth();
        fieldSize = hardwareManager.convertPixelsToMicrons(fieldSize);

        int halfStep = 2;

        for (int i = -halfStep; i<=halfStep; i++) {
            for (int j = -halfStep; j<=halfStep; j++) {
                hardwareManager.moveXYStage(fieldSize*j, fieldSize*i);
                hardwareManager.snap();
                stack.addSlice(hardwareManager.getImage());
            }
        }
        hardwareManager.moveXYStage(-halfStep*fieldSize, -halfStep*fieldSize);

        ZProjector projector = new ZProjector(new ImagePlus("Background Image",stack));
        projector.setMethod(ZProjector.MEDIAN_METHOD);
        projector.doProjection();

        return projector.getProjection().getProcessor().convertToFloatProcessor();
    }

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

            float[] shift = EstimateShiftAndTilt.getMaxFindByOptimization(map);
            shift = new float[] {
                    (shift[0] - map.getWidth()/2),
                    (shift[1] - map.getHeight()/2)
            };
            xShift.add(shift[0]);
            yShift.add(shift[1]);
        }

        // Get Median values to minimize stage error
        Collections.sort(xShift);
        double x = median(ArrayCasting.toArray(xShift, 0f));
        Collections.sort(yShift);
        double y = median(ArrayCasting.toArray(yShift, 0f));

        xMovement = x;
        yMovement = y;
    }

    //////////////////////////// Getters/Setters

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
