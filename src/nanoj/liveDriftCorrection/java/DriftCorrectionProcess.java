package nanoj.liveDriftCorrection.java;

import ij.IJ;
import ij.measure.Measurements;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import nanoj.core.java.image.calculator.FloatProcessorCalculator;
import nanoj.core.java.image.analysis.CalculateImageStatistics;
import ij.gui.OvalRoi;
import ij.process.ImageStatistics;

import java.awt.*;

/**
 *
 * The original ij.plugin.filter.FTFilter class wasn't versatile enough to have it's methods and
 * settings called from other plugins, so this is a cut down version of the filter that does the
 * same job, but with:
 *  - Default values that are better suited to the drift correction package
 *  - Addressable Settings
 *  - Removed unnecessary methods, variables and PlugInFilter implementation
 *  - Works with FloatProcessors
 *
 * NOTES FROM THE ORIGINAL ij.plugin.filter.FTFilter CLASS:
 * This class implements the Process/FFT/Bandpass Filter command. It is based on
 * Joachim Walter's FFT Filter plugin at "http://imagej.nih.gov/ij/plugins/fft-filter.html".
 * 2001/10/29: First Version (JW)
 * 2003/02/06: 1st bugfix (works in macros/plugins, works on stacks, overwrites image(=>filter)) (JW)
 * 2003/07/03: integrated into ImageJ, added "Display Filter" option (WSR)
 * 2007/03/26: 2nd bugfix (Fixed incorrect calculation of filter from structure sizes, which caused
 * the real structure sizes to be wrong by a factor of 0.75 to 1.5 depending on the image size.)
 *
 */
public class DriftCorrectionProcess implements Measurements {

    private static double filterLargeDia = 40.0;
    private static double filterSmallDia = 2.0;
    private static int choiceIndex = 2;
    private static double toleranceDia = 5.0;
    private int edgeClip;
    private DriftCorrectionData data;

    DriftCorrectionProcess(DriftCorrectionData theData) {
        this.data = theData;
    }

    public static final String MISMATCHED_IMAGE_SIZES = "Mismatched image sizes!";

    public static FloatProcessor filter(FloatProcessor inputImage) {
        FloatProcessor outputImage;
        if (inputImage == null) return null;

        int maxN = Math.max(inputImage.getWidth(), inputImage.getHeight());
        double sharpness = (100.0 - toleranceDia) / 100.0;

		/* 	tile mirrored image to power of 2 size
			first determine smallest power 2 >= 1.5 * image width/height
		  	factor of 1.5 to avoid wrap-around effects of Fourier Trafo */

        int i = 2;
        while (i < 1.5 * maxN) i *= 2;

        // Calculate the inverse of the 1/e frequencies for large and small structures.
        double filterLarge = 2.0 * filterLargeDia / (double) i;
        double filterSmall = 2.0 * filterSmallDia / (double) i;

        // fit image into power of 2 size
        Rectangle fitRect = new Rectangle();
        fitRect.x = (int) Math.round((i - inputImage.getWidth()) / 2.0);
        fitRect.y = (int) Math.round((i - inputImage.getHeight()) / 2.0);
        fitRect.width = inputImage.getWidth();
        fitRect.height = inputImage.getHeight();

        // put image (ROI) into power 2 size image and do
        // mirroring to avoid wrap around effects
        outputImage = tileMirror(inputImage, i, i, fitRect.x, fitRect.y);

        // transform forward
        FHT fht = new FHT(outputImage.duplicate());
        fht.transform();
        // filter out large and small structures
        filterLargeSmall(fht, filterLarge, filterSmall, choiceIndex, sharpness);

        // transform backward
        fht.inverseTransform();

        // crop to original size and do scaling if selected
        fht.setRoi(fitRect);
        outputImage = fht.crop().convertToFloatProcessor();

        return outputImage;
    }

    public FloatProcessor clip(FloatProcessor image) {
        int width = image.getWidth() - edgeClip*2;
        int height = image.getHeight() - edgeClip*2;
        image.setRoi(edgeClip,edgeClip, width, height);
        return image.crop().convertToFloatProcessor();
    }

        public float[] PeakFind(FloatProcessor CCmap) {
        float[] peak = CalculateImageStatistics.getMax(CCmap);
        int peakX = (int) peak[0];
        int peakY = (int) peak[1];
        int x = peakX - 2;
        int y = peakY - 2;
        //int x = CCmap.getWidth()/2 - 2;
        //int y = CCmap.getHeight()/2 - 2;
        CCmap.setRoi(x,y, 5, 5);
        FloatProcessor region = CCmap.crop().convertToFloatProcessor();
        //return EstimateShiftAndTilt.getMaxFindByOptimization(region);
        //return EstimateShiftAndTilt.getCenterOfMass(region);

        float xCM = 0;
        float yCM = 0;
        double v = 0;
        double sSum = 0;
        
        for (int j = 0; j < region.getHeight(); j++) {
            for (int i = 0; i < region.getWidth(); i++) {
                v = region.getf(i, j);
                //if (v < 0) continue;
                xCM += i * v;
                yCM += j * v;
                sSum += v;
            }
        }
        xCM /= sSum; yCM /= sSum;
        xCM += x; yCM += y;
        
        return new float[] {xCM, yCM};
    }
        
    public double[] PhasorPeakFind(FloatProcessor CCmap) {
        float[] peak = CalculateImageStatistics.getMax(CCmap);
        int peakX = (int) peak[0];
        int peakY = (int) peak[1];
        int x = peakX - 2;
        int y = peakY - 2;
        CCmap.setRoi(x,y, 5, 5);
        FloatProcessor region = CCmap.crop().convertToFloatProcessor();
        double[] fit = Phasorfit(region);
        fit[0] = fit[0] + peakX
        fit[1] = fit[1] + peakY
        return fit
    }
    
    public double CenterHeightFind(FloatProcessor image){ // 220131 JE
        int x = image.getWidth()/2 - 1;
        int y = image.getHeight()/2 - 1;
        image.setRoi(x,y, 3, 3);
        FloatProcessor region = image.crop().convertToFloatProcessor();
        region.smooth();
        FloatProcessor regionabs = region;
        regionabs.abs();
        float[] max = CalculateImageStatistics.getMax(regionabs);
        int xMax = (int) max[0];
        int yMax = (int) max[1];
        double PeakPix = region.getPixelValue(xMax,yMax);
        
        return PeakPix;
    }

    public double CenterHeightFind2(FloatProcessor image){ // 220131 JE
        int x = image.getWidth()/2 - 1;
        int y = image.getHeight()/2 - 1;
        image.setRoi(x,y, 3, 3);
        FloatProcessor region = image.crop().convertToFloatProcessor();
        float[] pixels = (float[]) region.getPixels();
        double sum = 0;
        for (int n=0; n<pixels.length; n++) {
            sum += pixels[n];
        }
        double mean = sum/pixels.length;
        return mean;
    }

    public double CenterHeightFind3(FloatProcessor image){ // 220131 JE
        int x = image.getWidth()/2 - 12;
        int y = image.getHeight()/2 - 12;
        image.setRoi(x,y, 25, 25);
        FloatProcessor region = image.crop().convertToFloatProcessor();
        OvalRoi roi = new OvalRoi((region.getWidth()/2)-9, (region.getHeight()/2)-9, 19, 19);
        region.setColor(0);
        region.setRoi(roi);
        region.fillOutside(roi);
        //double mean = ImageStatistics.getStatistics(region).mean;
        float[] pixels = (float[]) image.getPixels();
        double sum = 0;
        int pixnum = 0;
        for (int n=0; n<pixels.length; n++) {
            sum += pixels[n];
            if (pixels[n] != 0) pixnum = pixnum + 1;
        }
        double mean = sum/pixnum;
        return mean;
    }

    public double[] Phasorfit(FloatProcessor img) { // adapted from https://github.com/kjamartens/thunderstorm/blob/phasor-intensity-1/src/main/java/cz/cuni/lf1/lge/ThunderSTORM/estimators/PhasorFitter.java by JE 220419
    //long startTime = System.nanoTime();
    
        float[] pixels = (float[]) img.getPixels();
    
        double[] fitomega = new double[pixels.length];
        double[] fitcos = new double[pixels.length];
        double[] fitsin = new double[pixels.length];
        for (int indi = 0; indi < pixels.length; indi++){
            fitomega[indi] = (indi/pixels.length)*2*Math.PI;
            fitcos[indi] = Math.cos(fitomega[indi]);
            fitsin[indi] = Math.sin(fitomega[indi]);
        }
        
        //Variable initiation  
        double omega = 2.0 * Math.PI / img.getWidth();
        double axy[][] = new double[img.getWidth()][img.getHeight()];
        double totalint = 0;
        //Loop through all positions in array but in an X/Y manner:
        //First calculate total intensity of matrix, then set each point, normalized by total intensity
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                totalint += pixels[x+y*img.getWidth()];
            }
	}
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                axy[x][y] = pixels[x+y*img.getWidth()]/totalint;
            }
	}
        //Do the partial Fourier transformation (only acquiring first order harmonics)
        double[] totResult = twoDfft(axy,fitcos,fitsin);
        //Re-assign variables to something readable
        double FirstHarmonicXRe = totResult[0];
        double FirstHarmonicXIm = totResult[1];
        double AmplitudeX = totResult[2];
        double FirstHarmonicYRe = totResult[3];
        double FirstHarmonicYIm = totResult[4];
        double AmplitudeY = totResult[5];
        //Backup next line from change 19/3/2018
        //double angY=(Math.PI-Math.atan(FirstHarmonicYIm/FirstHarmonicYRe))*-1;
        //New line from change 19/3/2018 - atan2 resembles matlab more
        double angY=Math.atan2(FirstHarmonicYIm,FirstHarmonicYRe);
        //Removed strange part when 3x3 matrix is used at 19/3/2018
        //This used to be adding 1 PI when angle was below -PI
        if (angY>0){
            angY=angY-2*Math.PI;
        }
        //Backup next line from change 19/3/2018
        //double angX=(Math.PI-Math.atan(FirstHarmonicXIm/FirstHarmonicXRe))*-1;
        //New line from change 19/3/2018 - atan2 resembles matlab more
        double angX=Math.atan2(FirstHarmonicXIm,FirstHarmonicXRe);
        //Removed strange part when 3x3 matrix is used at 19/3/2018
        //This used to be adding 1 PI when angle was below -PI
        if (angX>0){
            angX=angX-2*Math.PI;
        }
        
        //Calculate X and Y positions
        double xpos = (Math.abs(angY)/omega) - (img.getWidth()-1)/2;// -0.125;//+0.25;
        double ypos = (Math.abs(angX)/omega) - (img.getHeight()-1)/2;// +0.125*1.5;
        double[] positions = new double[2];
        positions[0] = xpos;
        positions[1] = ypos;
        return positions;
    }

    public static double[] twoDfft(double[][] inputData, double[] cos, double[] sin) {
        //Initialize variables
        double height = inputData.length;
        double width = inputData[0].length;
        double[] totxloopRe = new double[inputData.length];
        double[] totxloopIm = new double[inputData.length];
        double FirstHarmonicXRe = 0;
        double FirstHarmonicXIm = 0;
        double[] totyloopRe = new double[inputData.length];
        double[] totyloopIm = new double[inputData.length];
        double FirstHarmonicYRe = 0;
        double FirstHarmonicYIm = 0;
        double[] totalArray = new double[6];
        //loop over the rows of the input data, multiply by real and imag parts of omega1 column
        for (int rowloop = 0; rowloop < height; rowloop++) 
        {
            for (int colloop = 0; colloop < width; colloop++) 
            {
                //Calculate real (cos) and imag (-sin) parts of the current row.
                totxloopRe[colloop] += cos[colloop]*inputData[rowloop][colloop];
                totxloopIm[colloop] -= sin[colloop]*inputData[rowloop][colloop];
           }
        }
        //Sum all the rows to get the first order harmonic in X
        for (int colloop = 0; colloop < width; colloop++) {
            FirstHarmonicXRe += totxloopRe[colloop];
            FirstHarmonicXIm += totxloopIm[colloop];
        }
        //Calculate amplitude
        double AmplitudeX = Math.sqrt(FirstHarmonicXRe/height*FirstHarmonicXRe/height+FirstHarmonicXIm/height*FirstHarmonicXIm/height);
        
        //loop over the columnss of the input data, multiply by real and imag parts of omega1 row       
        for (int colloop = 0; colloop < width; colloop++) 
        {
            for (int rowloop = 0; rowloop < height; rowloop++) 
            {
                //Calculate real (cos) and imag (-sin) parts of the current column.
                totyloopRe[rowloop] += cos[rowloop]*inputData[rowloop][colloop];
                totyloopIm[rowloop] -= sin[rowloop]*inputData[rowloop][colloop];
            }
        }
        //Sum all the rows to get the first order harmonic in Y 
        for (int rowloop = 0; rowloop < width; rowloop++) {
            FirstHarmonicYRe += totyloopRe[rowloop];
            FirstHarmonicYIm += totyloopIm[rowloop];
        } 
        //Calculate amplitude
        double AmplitudeY = Math.sqrt(FirstHarmonicYRe/height*FirstHarmonicYRe/height+FirstHarmonicYIm/height*FirstHarmonicYIm/height);
 
        //Placing the 6 variables in a array to be provided to the user
        totalArray[0]=FirstHarmonicXRe;
        totalArray[1]=FirstHarmonicXIm;
        totalArray[2]=AmplitudeX;
        totalArray[3]=FirstHarmonicYRe;
        totalArray[4]=FirstHarmonicYIm;
        totalArray[5]=AmplitudeY;
        
        //Return the values
        return totalArray;
    }

    public FloatProcessor backgroundCorrect(FloatProcessor image) throws Exception {
        FloatProcessor backgroundImage = data.getBackgroundImage();
        if (backgroundImage == null)
            return null;
        if ((image.getWidth() != backgroundImage.getWidth()) || (image.getHeight() != backgroundImage.getHeight()))
            throw new Exception(MISMATCHED_IMAGE_SIZES);
        return FloatProcessorCalculator.divide(image, backgroundImage);
    }

    public FloatProcessor process(FloatProcessor image) throws Exception{
        image = clip(image);
        if (data.getBackgroundImage() != null)
            image = backgroundCorrect(image);
        return image;
    }

    /**
     * Puts FloatProcessor (ROI) into a new FloatProcessor of size width x height y at position (x,y).
     * The image is mirrored around its edges to avoid wrap around effects of the FFT.
     */
    public static FloatProcessor tileMirror(FloatProcessor ip, int width, int height, int x, int y) {
        if (IJ.debugMode) IJ.log("FFT.tileMirror: " + width + "x" + height + " " + ip);
        if (x < 0 || x > (width - 1) || y < 0 || y > (height - 1)) {
            IJ.error("Image to be tiled is out of bounds.");
            return null;
        }

        FloatProcessor ipout = ip.createProcessor(width, height).convertToFloatProcessor();

        FloatProcessor ip2 = ip.crop().convertToFloatProcessor();
        int w2 = ip2.getWidth();
        int h2 = ip2.getHeight();

        //how many times does ip2 fit into ipout?
        int i1 = (int) Math.ceil(x / (double) w2);
        int i2 = (int) Math.ceil((width - x) / (double) w2);
        int j1 = (int) Math.ceil(y / (double) h2);
        int j2 = (int) Math.ceil((height - y) / (double) h2);

        //tile
        if ((i1 % 2) > 0.5)
            ip2.flipHorizontal();
        if ((j1 % 2) > 0.5)
            ip2.flipVertical();

        for (int i = -i1; i < i2; i += 2) {
            for (int j = -j1; j < j2; j += 2) {
                ipout.insert(ip2, x - i * w2, y - j * h2);
            }
        }

        ip2.flipHorizontal();
        for (int i = -i1 + 1; i < i2; i += 2) {
            for (int j = -j1; j < j2; j += 2) {
                ipout.insert(ip2, x - i * w2, y - j * h2);
            }
        }

        ip2.flipVertical();
        for (int i = -i1 + 1; i < i2; i += 2) {
            for (int j = -j1 + 1; j < j2; j += 2) {
                ipout.insert(ip2, x - i * w2, y - j * h2);
            }
        }

        ip2.flipHorizontal();
        for (int i = -i1; i < i2; i += 2) {
            for (int j = -j1 + 1; j < j2; j += 2) {
                ipout.insert(ip2, x - i * w2, y - j * h2);
            }
        }

        return ipout;
    }

    /*
    filterLarge: down to which size are large structures suppressed?
    filterSmall: up to which size are small structures suppressed?
    filterLarge and filterSmall are given as fraction of the image size
                in the original (untransformed) image.
    stripesHorVert: filter out: 0) nothing more  1) horizontal  2) vertical stripes
                (i.e. frequencies with x=0 / y=0)
    scaleStripes: width of the stripe filter, same unit as filterLarge
    */
    public static void filterLargeSmall(ImageProcessor ip, double filterLarge, double filterSmall, int stripesHorVert, double scaleStripes) {

        int maxN = ip.getWidth();

        float[] fht = (float[]) ip.getPixels();
        float[] filter = new float[maxN * maxN];
        for (int i = 0; i < maxN * maxN; i++)
            filter[i] = 1f;

        int row;
        int backrow;
        float rowFactLarge;
        float rowFactSmall;

        int col;
        int backcol;
        float factor;
        float colFactLarge;
        float colFactSmall;

        float factStripes;

        // calculate factor in exponent of Gaussian from filterLarge / filterSmall

        double scaleLarge = filterLarge * filterLarge;
        double scaleSmall = filterSmall * filterSmall;
        scaleStripes = scaleStripes * scaleStripes;
        //float FactStripes;

        // loop over rows
        for (int j = 1; j < maxN / 2; j++) {
            row = j * maxN;
            backrow = (maxN - j) * maxN;
            rowFactLarge = (float) Math.exp(-(j * j) * scaleLarge);
            rowFactSmall = (float) Math.exp(-(j * j) * scaleSmall);


            // loop over columns
            for (col = 1; col < maxN / 2; col++) {
                backcol = maxN - col;
                colFactLarge = (float) Math.exp(-(col * col) * scaleLarge);
                colFactSmall = (float) Math.exp(-(col * col) * scaleSmall);
                factor = (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall;
                switch (stripesHorVert) {
                    case 1:
                        factor *= (1 - (float) Math.exp(-(col * col) * scaleStripes));
                        break;// hor stripes
                    case 2:
                        factor *= (1 - (float) Math.exp(-(j * j) * scaleStripes)); // vert stripes
                }

                fht[col + row] *= factor;
                fht[col + backrow] *= factor;
                fht[backcol + row] *= factor;
                fht[backcol + backrow] *= factor;
                filter[col + row] *= factor;
                filter[col + backrow] *= factor;
                filter[backcol + row] *= factor;
                filter[backcol + backrow] *= factor;
            }
        }

        //process meeting points (maxN/2,0) , (0,maxN/2), and (maxN/2,maxN/2)
        int rowmid = maxN * (maxN / 2);
        rowFactLarge = (float) Math.exp(-(maxN / 2) * (maxN / 2) * scaleLarge);
        rowFactSmall = (float) Math.exp(-(maxN / 2) * (maxN / 2) * scaleSmall);
        factStripes = (float) Math.exp(-(maxN / 2) * (maxN / 2) * scaleStripes);

        fht[maxN / 2] *= (1 - rowFactLarge) * rowFactSmall; // (maxN/2,0)
        fht[rowmid] *= (1 - rowFactLarge) * rowFactSmall; // (0,maxN/2)
        fht[maxN / 2 + rowmid] *= (1 - rowFactLarge * rowFactLarge) * rowFactSmall * rowFactSmall; // (maxN/2,maxN/2)
        filter[maxN / 2] *= (1 - rowFactLarge) * rowFactSmall; // (maxN/2,0)
        filter[rowmid] *= (1 - rowFactLarge) * rowFactSmall; // (0,maxN/2)
        filter[maxN / 2 + rowmid] *= (1 - rowFactLarge * rowFactLarge) * rowFactSmall * rowFactSmall; // (maxN/2,maxN/2)

        switch (stripesHorVert) {
            case 1:
                fht[maxN / 2] *= (1 - factStripes);
                fht[rowmid] = 0;
                fht[maxN / 2 + rowmid] *= (1 - factStripes);
                filter[maxN / 2] *= (1 - factStripes);
                filter[rowmid] = 0;
                filter[maxN / 2 + rowmid] *= (1 - factStripes);
                break; // hor stripes
            case 2:
                fht[maxN / 2] = 0;
                fht[rowmid] *= (1 - factStripes);
                fht[maxN / 2 + rowmid] *= (1 - factStripes);
                filter[maxN / 2] = 0;
                filter[rowmid] *= (1 - factStripes);
                filter[maxN / 2 + rowmid] *= (1 - factStripes);
                break; // vert stripes
        }

        //loop along row 0 and maxN/2
        rowFactLarge = (float) Math.exp(-(maxN / 2) * (maxN / 2) * scaleLarge);
        rowFactSmall = (float) Math.exp(-(maxN / 2) * (maxN / 2) * scaleSmall);
        for (col = 1; col < maxN / 2; col++) {
            backcol = maxN - col;
            colFactLarge = (float) Math.exp(-(col * col) * scaleLarge);
            colFactSmall = (float) Math.exp(-(col * col) * scaleSmall);

            switch (stripesHorVert) {
                case 0:
                    fht[col] *= (1 - colFactLarge) * colFactSmall;
                    fht[backcol] *= (1 - colFactLarge) * colFactSmall;
                    fht[col + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall;
                    fht[backcol + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall;
                    filter[col] *= (1 - colFactLarge) * colFactSmall;
                    filter[backcol] *= (1 - colFactLarge) * colFactSmall;
                    filter[col + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall;
                    filter[backcol + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall;
                    break;
                case 1:
                    factStripes = (float) Math.exp(-(col * col) * scaleStripes);
                    fht[col] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
                    fht[backcol] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
                    fht[col + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall * (1 - factStripes);
                    fht[backcol + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall * (1 - factStripes);
                    filter[col] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
                    filter[backcol] *= (1 - colFactLarge) * colFactSmall * (1 - factStripes);
                    filter[col + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall * (1 - factStripes);
                    filter[backcol + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall * (1 - factStripes);
                    break;
                case 2:
                    factStripes = (float) Math.exp(-(maxN / 2) * (maxN / 2) * scaleStripes);
                    fht[col] = 0;
                    fht[backcol] = 0;
                    fht[col + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall * (1 - factStripes);
                    fht[backcol + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall * (1 - factStripes);
                    filter[col] = 0;
                    filter[backcol] = 0;
                    filter[col + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall * (1 - factStripes);
                    filter[backcol + rowmid] *= (1 - colFactLarge * rowFactLarge) * colFactSmall * rowFactSmall * (1 - factStripes);
            }
        }

        // loop along column 0 and maxN/2
        colFactLarge = (float) Math.exp(-(maxN / 2) * (maxN / 2) * scaleLarge);
        colFactSmall = (float) Math.exp(-(maxN / 2) * (maxN / 2) * scaleSmall);
        for (int j = 1; j < maxN / 2; j++) {
            row = j * maxN;
            backrow = (maxN - j) * maxN;
            rowFactLarge = (float) Math.exp(-(j * j) * scaleLarge);
            rowFactSmall = (float) Math.exp(-(j * j) * scaleSmall);

            switch (stripesHorVert) {
                case 0:
                    fht[row] *= (1 - rowFactLarge) * rowFactSmall;
                    fht[backrow] *= (1 - rowFactLarge) * rowFactSmall;
                    fht[row + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall;
                    fht[backrow + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall;
                    filter[row] *= (1 - rowFactLarge) * rowFactSmall;
                    filter[backrow] *= (1 - rowFactLarge) * rowFactSmall;
                    filter[row + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall;
                    filter[backrow + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall;
                    break;
                case 1:
                    factStripes = (float) Math.exp(-(maxN / 2) * (maxN / 2) * scaleStripes);
                    fht[row] = 0;
                    fht[backrow] = 0;
                    fht[row + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall * (1 - factStripes);
                    fht[backrow + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall * (1 - factStripes);
                    filter[row] = 0;
                    filter[backrow] = 0;
                    filter[row + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall * (1 - factStripes);
                    filter[backrow + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall * (1 - factStripes);
                    break;
                case 2:
                    factStripes = (float) Math.exp(-(j * j) * scaleStripes);
                    fht[row] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
                    fht[backrow] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
                    fht[row + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall * (1 - factStripes);
                    fht[backrow + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall * (1 - factStripes);
                    filter[row] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
                    filter[backrow] *= (1 - rowFactLarge) * rowFactSmall * (1 - factStripes);
                    filter[row + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall * (1 - factStripes);
                    filter[backrow + maxN / 2] *= (1 - rowFactLarge * colFactLarge) * rowFactSmall * colFactSmall * (1 - factStripes);
            }
        }
    }

    //////////////////////////// Getters/Setters

    public int getEdgeClip() {
        return edgeClip;
    }

    public void setEdgeClip(int edgeClip) {
        this.edgeClip = edgeClip;
    }

    public static double getFilterLargeDia() {
        return filterLargeDia;
    }

    public static void setFilterLargeDia(double filterLargeDia) {
        DriftCorrectionProcess.filterLargeDia = filterLargeDia;
    }

    public static double getFilterSmallDia() {
        return filterSmallDia;
    }

    public static void setFilterSmallDia(double filterSmallDia) {
        DriftCorrectionProcess.filterSmallDia = filterSmallDia;
    }

    public static int getChoiceIndex() {
        return choiceIndex;
    }

    public static void setChoiceIndex(int choiceIndex) {
        DriftCorrectionProcess.choiceIndex = choiceIndex;
    }

    public static double getToleranceDia() {
        return toleranceDia;
    }

    public static void setToleranceDia(double toleranceDia) {
        DriftCorrectionProcess.toleranceDia = toleranceDia;
    }

}

