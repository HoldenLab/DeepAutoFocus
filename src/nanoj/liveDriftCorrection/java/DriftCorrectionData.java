package nanoj.liveDriftCorrection.java;

//import com.sun.corba.se.impl.io.TypeMismatchException;
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
    private boolean flipX = false;
    private boolean SwitchXY;
    private boolean Running = false;
    private File dataFile;
    private ImagePlus resultMap = new ImagePlus();
    private ImagePlus plotsImage = new ImagePlus();
    private ImagePlus latestImage = new ImagePlus();
    private FloatProcessor referenceImage = null;
    private FloatProcessor backgroundImage = null;
    private ImageStack referenceStack = new ImageStack();
    private ImagePlus driftImages = new ImagePlus();
    private Plot plot;

    // Data
    private ArrayList<Double> xDrift = new ArrayList<Double>();
    private ArrayList<Double> yDrift = new ArrayList<Double>();
    private ArrayList<Double> zDrift = new ArrayList<Double>();
    private ArrayList<Double> zPosition = new ArrayList<Double>(); // current Z position 220111 kw
    private ArrayList<Double> SPdrift = new ArrayList<Double>(); // set point for PI controller 220110
    private ArrayList<Double> PVdrift = new ArrayList<Double>(); // process variable for PI controller 220110
    private ArrayList<Double> OPdrift = new ArrayList<Double>(); // output for PI controller 220110
    private ArrayList<Double> timeStamps = new ArrayList<Double>();
    private double StartMDA;

    private int dataType = 0;

    private static final int XYZ = 0;
    private static final int Z = 1;
    private static final int XY = 2;
    private static final int PID = 3;

    // Labels
    private static final String MAP_NAME = "Drift Correction Correlation Map";
    private static final String PLOTS_NAME = "Plot of the measured drift over time";
    private static final String LIVE_WINDOW_NAME = "Drift Correction Camera";
    private String xUnit = ""; // 221017 JE
    private String yUnit = ""; // 221017 JE
    private String zUnit = ""; // 221017 JE
    private String tUnit = ""; // 221017 JE

    // Errors
    public static final String DATA_FILE_NOT_SET_ERROR = "Data file has not been created yet.";
    public static final String CREATE_FILE_ERROR = "Error when trying to create file for drift data.";
    public static final String APPENDING_ERROR = "Error when trying to add data to drift data file.";
    public static final String CLOSING_FILE_ERROR = "Error when trying to close drift data file.";
    public static final String DATA_MISMATCH_ERROR = "Data is not of the correct type, it is actually: ";
    private static final String procedure_succeeded = "The procedure has succeeded!";
    
    private DriftCorrectionHardware hardware;

    DriftCorrectionData(DriftCorrectionHardware hardwareManager) {
        resultMap.setTitle(MAP_NAME);
        plotsImage.setTitle(PLOTS_NAME);
        latestImage.setTitle(LIVE_WINDOW_NAME);
        hardware = hardwareManager;
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
            double[] zPos = ArrayCasting.toArray(this.zPosition, 1d);
            double[] timeStamps = ArrayCasting.toArray(this.timeStamps, 1d);
                
            xUnit = " (um)";
            yUnit = " (um)";
            zUnit = "";
            tUnit = " (ms)";            

            switch (dataTypeIs()) {
                case Z:
                    plot = new Plot("Z drift", "Time" + tUnit, "Z position (microns)", timeStamps, zShift, Plot.LINE);
                    plots.add(plot.getProcessor());
                
                    plot = new Plot("Z position", "Time" + tUnit, "Z position (microns)", timeStamps, zPos, Plot.LINE);
                    plots.add(plot.getProcessor());
                    
                    break;
                case XY:
                    plot = new Plot("X-drift", "Time" + tUnit, "X-Drift" + xUnit, timeStamps, xShift, Plot.LINE);
                    plots.add(plot.getProcessor());
                    
                    plot = new Plot("Y-drift", "Time" + tUnit, "Y-Drift" + yUnit, timeStamps, yShift, Plot.LINE);
                    plots.add(plot.getProcessor());

                    break;
                case XYZ:
                    plot = new Plot("X-drift", "Time" + tUnit, "X-Drift" + xUnit, timeStamps, xShift, Plot.LINE);
                    plots.add(plot.getProcessor());

                    plot = new Plot("Y-drift", "Time" + tUnit, "Y-Drift" + yUnit, timeStamps, yShift, Plot.LINE);
                    plots.add(plot.getProcessor());

                    plot = new Plot("Z-drift", "Time" + tUnit, "Z-Drift" + zUnit, timeStamps, zShift, Plot.LINE);
                    plots.add(plot.getProcessor());

                    break;
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
    
    synchronized void setflipX(boolean flipX) { // 220930 JE
        this.flipX = flipX;
    }
    
    synchronized boolean getflipX() { // 220930 JE
        return flipX;
    }

    synchronized void setShowLatest(boolean showLatest) {
        this.showLatest = showLatest;
    }

    private synchronized File getDataFile() { return dataFile; }

    synchronized void setDataFile(File dataFile) { this.dataFile = dataFile; }

    synchronized void setResultMap(ImageStack imageStack) {
        if (imageStack.getSize() < 1) return;
        boolean keepStacks = false; // I find it more helpful to see readout in real time. 201230 kw

        if (isShowMapTrue()) {
            if (!keepStacks) resultMap.setStack(imageStack);
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
    
    // added 201230 kw
    synchronized void setResultMap(ImageProcessor imageProc) {

        if (isShowMapTrue()) {

            resultMap.setProcessor(imageProc);

            resultMap.show();
        }
    }
    
    // added kw 190412
    synchronized void clearResultMap(){
        resultMap.close();
        driftImages.close();
    }

    synchronized void setLatestImage(FloatProcessor image) {
        latestImage.setProcessor(image);
        
        if (isShowLatestTrue()) latestImage.show();
        ImageStack stack = new ImageStack(image.getWidth(), image.getHeight());
        boolean keepImages = false;

        if (isShowLatestTrue() && keepImages && isRunning()) {
            if(driftImages.getStack().size()>=1) stack = driftImages.getStack();
            stack.addSlice(image);
            driftImages.setStack(stack);
            if (isShowLatestTrue()) driftImages.show();
        }
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
    
    private synchronized void setZPositionData(ArrayList<Double> zPosition) {
        this.zPosition = zPosition;
    }

    synchronized void addZShift(double zShiftPoint, double z_err, double timeStamp, double Zp, double Zi, double refUpdate) {
        //if (dataTypeIs() != Z) throw new TypeMismatchException(DATA_MISMATCH_ERROR + dataTypeIs());
        if ( Double.isNaN(zShiftPoint) ) return;
        addTimeStamp(timeStamp);
        if (zDrift.size() == 0){
            zDrift.add(zShiftPoint);
            zPosition.add(z_err);
        }
        else {
            zDrift.add(zShiftPoint + zDrift.get(zDrift.size()-1));
            zPosition.add(z_err);
        }
        if (!zDrift.isEmpty()) {
            if (isShowPlotTrue()) showPlots();
            if (isSavePlotsTrue()) {
                double z = zDrift.get(zDrift.size()-1);
                if (zDrift.size() <= 2) appendDataToFile("Zp" + "," + "Zi" + "," + "Step size (nm) for Z correction" + "," + "Time between reference updates (min)");
                if (zDrift.size() <= 2) appendDataToFile(Zp + "," + Zi + "," + hardware.getStepSize()*1000 + "," + refUpdate);
                if (zDrift.size() <= 2) appendDataToFile("timeStamp (ms)" + "," + "z" + "," + "MDA");
                appendDataToFile(timeStamp + "," + z + "," + getStartMDA());
            }
        }
    }

    synchronized void addXYshift(double xShiftPoint, double yShiftPoint, double timeStamp, double Lp, double Li, double refUpdate) {
        //if (dataTypeIs() != XY) throw new TypeMismatchException(DATA_MISMATCH_ERROR + dataTypeIs());
        if ( Double.isNaN(xShiftPoint) || Double.isNaN(yShiftPoint) ) return;
        addTimeStamp(timeStamp);
        addXYPoint(xShiftPoint, yShiftPoint);

        if (!xDrift.isEmpty()) {
            if (isShowPlotTrue()) showPlots();
            if (isSavePlotsTrue()) {
                double x = xDrift.get(xDrift.size() - 1);
                double y = yDrift.get(yDrift.size() - 1);
                if (xDrift.size() <= 2) appendDataToFile("Lp" + "," + "Li" + "," + "Step size (nm) for Z correction" + "," + "Time between reference updates (min)");
                if (xDrift.size() <= 2) appendDataToFile(Lp + "," + Li + "," + hardware.getStepSize()*1000 + "," + refUpdate);
                if (xDrift.size() <= 2) appendDataToFile("time (ms)" + "," + "x (microns)" + "," + "y (microns)" + "," + "MDA");
                appendDataToFile(timeStamp + "," + x + ", " + y + "," + getStartMDA());
            }
        }
    }

    synchronized void addXYZshift(double xShiftPoint, double yShiftPoint, double zShiftPoint, double z_err, double timeStamp, double Zp, double Zi, double Lp, double Li, double refUpdate) {
        //if (dataTypeIs() != XYZ) throw new TypeMismatchException(DATA_MISMATCH_ERROR + dataTypeIs());
        if ( Double.isNaN(xShiftPoint) || Double.isNaN(yShiftPoint) || Double.isNaN(zShiftPoint) ) return;
        addTimeStamp(timeStamp);

        addXYPoint(xShiftPoint, yShiftPoint);
        
        zDrift.add(z_err);
        
        if (!xDrift.isEmpty()) {
            if (isShowPlotTrue()) showPlots();
            if (isSavePlotsTrue()) {
                double x = xDrift.get(xDrift.size() - 1);
                double y = yDrift.get(yDrift.size() - 1);
                double z = zDrift.get(zDrift.size() - 1);
                if (xDrift.size() <= 2) appendDataToFile("Zp" + "," + "Zi" + "," + "Lp" + "," + "Li" + "," + "Step size (nm) for Z correction" + "," + "Time between reference updates (min)");
                if (xDrift.size() <= 2) appendDataToFile(Zp + "," + Zi + "," + Lp + "," + Li + "," + hardware.getStepSize()*1000 + "," + refUpdate);
                if (xDrift.size() <= 2) appendDataToFile("timeStamp (ms)" + "," + "x (um)" + "," + "y (um)" + "," + "z" + "," + "MDA");
                appendDataToFile(timeStamp + "," + x + "," + y + "," + z + "," + getStartMDA());
            }
        }
    }

    private void addXYPoint(double x, double y) {
        if (xDrift.size() == 0) {
            xDrift.add(x);
            yDrift.add(y);
        }
        else {
            xDrift.add(x);
            yDrift.add(y);            
        }
    }

    private synchronized void addTimeStamp(double timeStamp) {
        timeStamps.add(timeStamp);
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
    
    public boolean getSwitchXY() {
        return SwitchXY;
    }

    public void setSwitchXY(boolean SwitchXY) {
        this.SwitchXY = SwitchXY;
    }
    
    public double getStartMDA() {
        return StartMDA;
    }
    
    public void setStartMDA(double StartMDA) {
        this.StartMDA = StartMDA;
    }
    
    public boolean isRunning() {
        return Running;
    }
    
    public void setRunning(boolean run){
        this.Running = run;
    }

    synchronized void clearData() {
        setXShiftData(xDrift = new ArrayList<Double>());
        setYShiftData(yDrift = new ArrayList<Double>());
        setZShiftData(zDrift = new ArrayList<Double>());
        setZPositionData(zPosition = new ArrayList<Double>());
        setTimeStamps(timeStamps = new ArrayList<Double>());
    }
}
