package nanoj.liveDriftCorrection.java;

import ij.IJ;
import ij.ImageStack;
import ij.measure.Measurements;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import nanoj.core.java.image.calculator.FloatProcessorCalculator;
import nanoj.core.java.image.analysis.CalculateImageStatistics;
import nanoj.core.java.image.drift.EstimateShiftAndTilt;
import ij.gui.OvalRoi;
//import ij.process.ImageStatistics;
//import org.micromanager.internal.utils.ReportingUtils;

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
        image.setRoi(edgeClip,edgeClip, width-1, height-1);
        return image.crop().convertToFloatProcessor();
    }

    public double[] PeakFind(FloatProcessor CCmap) {
        //CCmap.setRoi(0,0, CCmap.getWidth()-1, CCmap.getHeight()-1);
        //CCmap.crop();
        int x;
        int y;
        float[] peak = CalculateImageStatistics.getMax(CCmap);
        int peakX = (int) peak[0];
        int peakY = (int) peak[1];
        int xcenter = CCmap.getWidth()/2;
        int ycenter = CCmap.getHeight()/2;
        int offset = 5;
        int size = 10;
        //if (CCmap.getWidth()%2 == 0); offset = 5;
        if (CCmap.getWidth()%2 == 0); size = size + 1;
        if (Math.abs(peakX-xcenter)>3 || Math.abs(peakY-ycenter)>3){
            //x = peakX - offset;
            //y = peakY - offset;
            x = xcenter - offset;
            y = ycenter - offset;
        }
        else{
            x = xcenter - offset;
            y = ycenter - offset;
        }
        
        
        CCmap.setRoi(x,y, size,size);
        FloatProcessor region = CCmap.crop().convertToFloatProcessor();
        
        double xCM = 0;
        double yCM = 0;
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
        
        return new double[] {xCM, yCM};       
    }
    
    public double[] PeakFind2(FloatProcessor CCmap, float[] Peak) {
        if (Peak == null) {
            Peak[0] =  CCmap.getWidth()/2;
            Peak[1] =  CCmap.getHeight()/2;
        }
        int x;
        int y;
        int PeakX = (int) Peak[0];
        int PeakY = (int) Peak[1];
        int xcenter = CCmap.getWidth()/2;
        int ycenter = CCmap.getHeight()/2;
        if (Math.abs(PeakX-xcenter) < 3)PeakX = xcenter;
        if (Math.abs(PeakY-ycenter) < 3)PeakY = ycenter;
        int offset = 5;
        int size = 11;
        
        //if (Math.abs(PeakX-xcenter)>3 || Math.abs(PeakY-ycenter)>3){
        x = PeakX - offset;
        y = PeakY - offset;
            //x = xcenter - offset;
            //y = ycenter - offset;
        //}
        //else{
        //    x = xcenter - offset;
        //    y = ycenter - offset;
        //}
        
        
        //x = PeakX - offset;
        //y = PeakY - offset;
        
        CCmap.setRoi(x,y, size,size);
        FloatProcessor region = CCmap.crop().convertToFloatProcessor();
        /*
        float[] xyFit = EstimateShiftAndTilt.getMaxFindByOptimization(region);
        double xFit = (double) xyFit[0];
        double yFit = (double) xyFit[1];
        xFit += x; yFit += y;
        return new double[] {xFit, yFit};
        */
        double xCM = 0;
        double yCM = 0;
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
        
        return new double[] {xCM, yCM};        
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
        int offset = 2;
        int size = 4;
        if (image.getWidth()%2 == 0); offset = 1;
        if (image.getWidth()%2 == 0); size = 3;
        int x = image.getWidth()/2 - offset;
        int y = image.getHeight()/2 - offset;
        image.setRoi(x,y, size, size);
        FloatProcessor region = image.crop().convertToFloatProcessor();
        float[] pixels = (float[]) region.getPixels();
        double sum = 0;
        for (int n=0; n<pixels.length; n++) {
            sum += pixels[n];
        }
        double mean = sum/pixels.length;
        return mean;
    }
    
    public double CenterHeightFind3(FloatProcessor image, float[] Center){ // 220131 JE
        int CenterX = (int) Center[0];
        int CenterY = (int) Center[1];
        if (Math.abs(CenterX-image.getWidth()/2) < 2) CenterX = image.getWidth()/2;
        if (Math.abs(CenterY-image.getHeight()/2) < 2) CenterY = image.getHeight()/2;
        int offset = 1;
        int size = 3;
        int x = CenterX - offset;
        int y = CenterY - offset;
        image.setRoi(x,y, size, size);
        FloatProcessor region = image.crop().convertToFloatProcessor();
        float[] pixels = (float[]) region.getPixels();
        double sum = 0;
        for (int n=0; n<pixels.length; n++) {
            sum += pixels[n];
        }
        double mean = sum/pixels.length;
        return mean;
    }
    
    public float[] PickPlane(ImageStack ccMapStack){ // choses which plane of the ccMap stack has the highest peak to center algorithm on 220926 JE
        float[] Peaks = new float[3];
        float vMax = -Float.MAX_VALUE;
        int pMax = 0;

        float[] TopPeak = CalculateImageStatistics.getMax(ccMapStack.getProcessor(1));
        float[] MiddlePeak = CalculateImageStatistics.getMax(ccMapStack.getProcessor(2));
        float[] BottomPeak = CalculateImageStatistics.getMax(ccMapStack.getProcessor(3));

        Peaks[0] = TopPeak[2];
        Peaks[1] = MiddlePeak[2];
        Peaks[2] = BottomPeak[2];

        for (int p=0; p<ccMapStack.size(); p++) {
            float v = Peaks[p];
            if (v > vMax) {
                vMax = v;
                pMax = p;
            }
        }

        switch(pMax){
            case 0:
                TopPeak[2] = 1f;
                return TopPeak;
            case 1:
                MiddlePeak[2] = 2f;
                return MiddlePeak;
            case 2:
                BottomPeak[2] = 3f;
                return BottomPeak;
        }
        return null;
    }

    public double CenterHeightFind4(FloatProcessor image){ // 220131 JE
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

