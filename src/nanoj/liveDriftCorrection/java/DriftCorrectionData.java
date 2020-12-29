package nanoj.liveDriftCorrection.java;

import com.sun.corba.se.impl.io.TypeMismatchException;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Plot;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import nanoj.core.java.array.ArrayCasting;
import org.apache.commons.io.FilenameUtils;
import org.micromanager.internal.utils.ReportingUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class DriftCorrectionData {
    private boolean showMap = false;
    private boolean showPlot = false;
    private boolean savePlots = false;
    private boolean showLatest = false;
    private boolean flipY = false;
    private File dataFile;
    private ImagePlus resultMap = new ImagePlus();
    private ImagePlus plotsImage = new ImagePlus();
    private ImagePlus latestImage = new ImagePlus();
    private FloatProcessor referenceImage = null;
    private FloatProcessor backgroundImage = null;
    private ImageStack referenceStack = new ImageStack();
    private Plot plot;

    // Data
    private ArrayList<Double> xDrift = new ArrayList<Double>();
    private ArrayList<Double> yDrift = new ArrayList<Double>();
    private ArrayList<Double> zDrift = new ArrayList<Double>();
    private ArrayList<Double> timeStamps = new ArrayList<Double>();

    private int dataType = 0;

    private static final int XYZ = 0;
    private static final int Z = 1;
    private static final int XY = 2;

    // Labels
    private static final String MAP_NAME = "Drift Correction Correlation Map";
    private static final String PLOTS_NAME = "Plot of the measured drift over time";
    private static final String LIVE_WINDOW_NAME = "Drift Correction Camera";

    // Errors
    public static final String DATA_FILE_NOT_SET_ERROR = "Data file has not been created yet.";
    public static final String CREATE_FILE_ERROR = "Error when trying to create file for drift data.";
    public static final String APPENDING_ERROR = "Error when trying to add data to drift data file.";
    public static final String CLOSING_FILE_ERROR = "Error when trying to close drift data file.";
    public static final String DATA_MISMATCH_ERROR = "Data is not of the correct type, it is actually: ";
    private static final String procedure_succeeded = "The procedure has succeeded!";

    DriftCorrectionData() {
        resultMap.setTitle(MAP_NAME);
        plotsImage.setTitle(PLOTS_NAME);
        latestImage.setTitle(LIVE_WINDOW_NAME);
    }

    // Create file
    void createFile(String location) {
        File savePath = new File(location);
        try {
            savePath.getParentFile().mkdirs();
            if(savePath.getAbsolutePath().endsWith(".csv")) {
                String path = savePath.getAbsolutePath();
                savePath = new File(path.substring(0, path.length()-4));
            }
            // Get just the filename
            String newFileName = FilenameUtils.getBaseName(savePath.toString());

            // Get and format current data
            String date = new SimpleDateFormat("yyyy_MM_dd_HH.mm.ss").format(new Date());
            // Concatenate everything
            newFileName = savePath.getParentFile() + File.separator + newFileName + "_" + date + ".csv";
            setDataFile(new File(newFileName));
            getDataFile().createNewFile();
        } catch (IOException e) {
            ReportingUtils.showError(CREATE_FILE_ERROR);
            ReportingUtils.logError(e);
        }
    }

    // Add data to file
    private void appendDataToFile(String data) {
        if (getDataFile() == null) {
            ReportingUtils.showError(DATA_FILE_NOT_SET_ERROR);
            return;
        }
        if (timeStamps.size() < 2) return;
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(dataFile, true));
            bw.write(data);
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            ReportingUtils.showError(APPENDING_ERROR);
            ReportingUtils.logError(e);
        } finally {
            if (bw != null) try {
                bw.close();
            } catch (IOException e2) {
                ReportingUtils.showError(CLOSING_FILE_ERROR);
                ReportingUtils.logError(e2);
            }
        }
    }

    // Show plots
    private void showPlots() {
        if (timeStamps.size() < 2) return;
        try {
            ArrayList<ImageProcessor> plots = new ArrayList<ImageProcessor>();
            double[] xShift = ArrayCasting.toArray(this.xDrift, 1d);
            double[] yShift = ArrayCasting.toArray(this.yDrift, 1d);
            double[] zShift = ArrayCasting.toArray(this.zDrift, 1d);
            double[] timeStamps = ArrayCasting.toArray(this.timeStamps, 1d);

            if (dataTypeIs() == Z) {
                plot = new Plot("Z-drift", "Time (min.)", "Z-Drift (microns)", timeStamps, zShift, Plot.LINE);
                plots.add(plot.getProcessor());

            } else if (dataTypeIs() == XY) {
                plot = new Plot("X-drift", "Time (min.)", "X-Drift (microns)", timeStamps, xShift, Plot.LINE);
                plots.add(plot.getProcessor());
                plot = new Plot("Y-drift", "Time (min.)", "Y-Drift (microns)", timeStamps, yShift, Plot.LINE);
                plots.add(plot.getProcessor());

            } else if (dataTypeIs() == XYZ) {
                plot = new Plot("X-drift", "Time (min.)", "X-Drift (microns)", timeStamps, xShift, Plot.LINE);
                plots.add(plot.getProcessor());

                plot = new Plot("Y-drift", "Time (min.)", "Y-Drift (microns)", timeStamps, yShift, Plot.LINE);
                plots.add(plot.getProcessor());

                plot = new Plot("Z-drift", "Time (min.)", "Z-Drift (microns)", timeStamps, zShift, Plot.LINE);
                plots.add(plot.getProcessor());
            }

            if (plots.size() > 0) {
                int width = plots.get(0).getWidth();
                int height = plots.get(0).getHeight();
                ImageProcessor ipPlots = new ByteProcessor(width, height * plots.size());
                for (int p = 0; p < plots.size(); p++) {
                    ipPlots.copyBits(plots.get(p), 0, p * height, Blitter.COPY_INVERTED);
                }

                plotsImage.setProcessor(ipPlots);
                plotsImage.show();
            }
        } catch (Exception e)
        {
            ReportingUtils.showError(e);
        }
    }

    //////////////////////////// Getters/Setters

    FloatProcessor getReferenceImage() {
        return referenceImage;
    }
    
    // added 190401 by kw
    ImageStack getReferenceStack() {
        return referenceStack;
    }

    void setReferenceImage(FloatProcessor referenceImage) {
        this.referenceImage = referenceImage;
    }
    
    // added 190401 kw
    void setReferenceStack(ImageStack referenceStack) {
        this.referenceStack = referenceStack;
    }

    FloatProcessor getBackgroundImage() {
        return backgroundImage;
    }

    void setBackgroundImage(FloatProcessor backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    private synchronized boolean isShowMapTrue() {
        return showMap;
    }

    synchronized void setShowMap(boolean showMap) {
        this.showMap = showMap;
    }

    private synchronized boolean isShowPlotTrue() {
        return showPlot;
    }

    synchronized void setShowPlot(boolean showPlot) {
        this.showPlot = showPlot;
    }

    private synchronized boolean isSavePlotsTrue() {
        return savePlots;
    }

    synchronized void setSavePlots(boolean savePlots) {
        this.savePlots = savePlots;
    }

    private synchronized boolean isShowLatestTrue() {
        return showLatest;
    }
    
    synchronized void setflipY(boolean flipY) { // 201229 kw
        this.flipY = flipY;
    }
    
    synchronized boolean getflipY() { // 201229 kw
        return flipY;
    }

    synchronized void setShowLatest(boolean showLatest) {
        this.showLatest = showLatest;
    }

    private synchronized File getDataFile() { return dataFile; }

    synchronized void setDataFile(File dataFile) { this.dataFile = dataFile; }

    synchronized void setResultMap(ImageStack imageStack) {
        if (imageStack.getSize() < 1) return;
        boolean keepStacks = true;

        if (isShowMapTrue()) {

            if (!keepStacks)
                resultMap.setStack(imageStack);
            else {
                if (resultMap.getStackSize() < 2) {
                    resultMap.setStack(imageStack);
                }
                else {
                    ImageStack stack = resultMap.getStack();

                    for (int i = 1; i <= imageStack.getSize(); i++) {
                        stack.addSlice(imageStack.getProcessor(i));
                    }
                    resultMap.setStack(stack);
                }
            }
            resultMap.show();
        }
    }
    
    // added kw 190412
    synchronized void clearResultMap(){
        resultMap.close();
    }

    synchronized void setLatestImage(FloatProcessor image) {
        
        if (flipY) image.flipVertical(); // 201229 kw
        
        latestImage.setProcessor(image);
        
        if (isShowLatestTrue())
            latestImage.show();
    }

    private synchronized void setXShiftData(ArrayList<Double> xShift) {
        this.xDrift = xShift;
    }

    private synchronized void setYShiftData(ArrayList<Double> yShift) {
        this.yDrift = yShift;
    }

    private synchronized void setZShiftData(ArrayList<Double> zShift) {
        this.zDrift = zShift;
    }

    synchronized void addZShift(double zShiftPoint, double timeStamp) {
        if (dataTypeIs() != Z) throw new TypeMismatchException(DATA_MISMATCH_ERROR + dataTypeIs());
        if ( Double.isNaN(zShiftPoint) ) return;
        addTimeStamp(timeStamp);
        if (zDrift.size() == 0) zDrift.add(zShiftPoint);
        else zDrift.add(zShiftPoint + zDrift.get(zDrift.size()-1));
        if (!zDrift.isEmpty()) {
            if (isShowPlotTrue()) showPlots();
            if (isSavePlotsTrue()) {
                double z = zDrift.get(zDrift.size()-1);
                appendDataToFile(timeStamp + "," + z);
            }
        }
    }

    synchronized void addXYshift(double xShiftPoint, double yShiftPoint, double timeStamp) {
        if (dataTypeIs() != XY) throw new TypeMismatchException(DATA_MISMATCH_ERROR + dataTypeIs());
        if ( Double.isNaN(xShiftPoint) || Double.isNaN(yShiftPoint) ) return;
        addTimeStamp(timeStamp);

        addXYPoint(xShiftPoint, yShiftPoint);

        if (!xDrift.isEmpty()) {
            if (isShowPlotTrue()) showPlots();
            if (isSavePlotsTrue()) {
                double x = xDrift.get(xDrift.size() - 1);
                double y = yDrift.get(yDrift.size() - 1);
                appendDataToFile(timeStamp + "," + x + ", " + y);
            }
        }
    }

    synchronized void addXYZshift(double xShiftPoint, double yShiftPoint, double zShiftPoint, double timeStamp) {
        if (dataTypeIs() != XYZ) throw new TypeMismatchException(DATA_MISMATCH_ERROR + dataTypeIs());
        if ( Double.isNaN(xShiftPoint) || Double.isNaN(yShiftPoint) || Double.isNaN(zShiftPoint) ) return;
        addTimeStamp(timeStamp);

        addXYPoint(xShiftPoint, yShiftPoint);

        if (zDrift.size() == 0) zDrift.add(zShiftPoint);
        else zDrift.add(zShiftPoint + zDrift.get(zDrift.size()-1));

        if (!xDrift.isEmpty()) {
            if (isShowPlotTrue()) showPlots();
            if (isSavePlotsTrue()) {
                double x = xDrift.get(xDrift.size() - 1);
                double y = yDrift.get(yDrift.size() - 1);
                double z = zDrift.get(zDrift.size() - 1);
                appendDataToFile(timeStamp + "," + x + ", " + y + "," + z);
            }
        }
    }

    private void addXYPoint(double x, double y) {
        if (xDrift.size() == 0) {
            xDrift.add(x);
            yDrift.add(y);
        }
        else {
            xDrift.add(x + xDrift.get(xDrift.size()-1));
            yDrift.add(y + yDrift.get(yDrift.size()-1));
        }
    }

    private synchronized void addTimeStamp(double timeStamp) {
        timeStamps.add(timeStamp/60000);
    }

    private synchronized void setTimeStamps(ArrayList<Double> timeStamps) {
        this.timeStamps = timeStamps;
    }

    private synchronized int dataTypeIs() {
        return dataType;
    }

    synchronized void setDataType(int dataType) {
        clearData();
        this.dataType = dataType;
    }

    synchronized void clearData() {
        setXShiftData(xDrift = new ArrayList<Double>());
        setYShiftData(yDrift = new ArrayList<Double>());
        setZShiftData(zDrift = new ArrayList<Double>());
        setTimeStamps(timeStamps = new ArrayList<Double>());
    }

}
