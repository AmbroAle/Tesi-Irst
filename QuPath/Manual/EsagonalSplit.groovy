import qupath.lib.objects.PathObjects
import qupath.lib.roi.*
import qupath.lib.regions.ImagePlane
import qupath.lib.geom.Point2
import java.awt.geom.Area
import java.awt.geom.PathIterator
import org.slf4j.LoggerFactory

/**
 * HexGridFromBlobs
 *
 * This class generates a hexagonal grid over detection objects (blobs)
 * belonging to a specific class (channel). Each blob is subdivided into
 * hexagonal regions, intersected with the original blob shape.
 * Disconnected regions resulting from the intersection are automatically split.
 *
 * The original blob detections are removed and replaced by the generated
 * hexagonal detections.
 */
class HexGridFromBlobs {

    /** Name of the detection class to process */
    String channelName

    /** Diameter of the hexagon (in PIXELS) */
    double diametro

    /** Logger for structured output */
    def logger = LoggerFactory.getLogger("HexGridFromBlobs")

    // ---------- Derived geometric parameters ----------

    /** Distance from hexagon center to vertex */
    double a

    /** Horizontal spacing between hexagon centers */
    double stepX

    /** Vertical spacing between hexagon centers */
    double stepY

    /** Image plane used for ROI creation */
    ImagePlane plane

    /**
     * Constructor
     *
     * @param channelName name of the detection class
     * @param diametroMicron diameter of the hexagons in MICRONS
     */
    HexGridFromBlobs(String channelName, double diametroMicron) {
        this.channelName = channelName
        
        // --- CORREZIONE: Converti Micron -> Pixel ---
        // Recupera la calibrazione dell'immagine corrente
        def cal = qupath.lib.gui.QuPathGUI.getInstance().getImageData().getServer().getPixelCalibration()
        double pxSize = cal.getPixelWidthMicrons()
        
        if (Double.isNaN(pxSize) || pxSize == 0) {
            pxSize = 1.0 // Fallback se non calibrata
            print "⚠️ Warning: Pixel size not found, assuming 1.0"
        }
        
        // Converti il diametro target in pixel
        double diametroPixel = diametroMicron / pxSize
        
        this.diametro = diametroPixel // Aggiorna per il log se serve, o usa una var locale

        // Compute geometric parameters of the hexagonal grid (USING PIXELS)
        this.a = diametro / 2.0
        this.stepX = 1.5 * a
        this.stepY = Math.sqrt(3) * a

        // Use the default image plane
        this.plane = ImagePlane.getDefaultPlane()
        
        print "🔧 DEBUG: Diametro richiesto: ${diametroMicron} µm -> Applicato: ${String.format('%.2f', diametroPixel)} px"
    }

    /**
     * Executes the hexagonal subdivision process.
     * All detections of the specified class are processed independently.
     */
    void run() {

        // Retrieve detection objects belonging to the selected class
        def blobs = getDetectionObjects().findAll {
            it.getPathClass() == getPathClass(channelName)
        }

        // Stop execution if no blobs are found
        if (blobs.isEmpty()) {
            print " Nessun blob trovato per ${channelName}"
            return
        }

        print "INFO: Blob trovati: ${blobs.size()}"

        // List that will contain all generated hexagonal detections
        def allHexes = []

        // Process each blob independently
        blobs.each { blob ->

            // Extract the blob ROI and convert it to an Area object
            def roi = blob.getROI()
            def blobArea = new Area(roi.getShape())

            // Bounding box of the blob
            double xMin = roi.getBoundsX()
            double yMin = roi.getBoundsY()
            double w = roi.getBoundsWidth()
            double h = roi.getBoundsHeight()

            // Number of hexagon centers needed to cover the blob area
            int nCols = Math.ceil(w / stepX) as int
            int nRows = Math.ceil(h / stepY) as int

            // Iterate over the hexagonal grid
            for (int col = 0; col < nCols; col++) {
                for (int row = 0; row < nRows; row++) {

                    // Compute hexagon center position
                    double cx = xMin + col * stepX
                    double cy = yMin + row * stepY

                    // Offset every other column to obtain a hexagonal tiling
                    if (col % 2 == 1)
                        cy += stepY / 2

                    // Hexagon construction
                    def pts = []
                    for (int k = 0; k < 6; k++) {
                        double angle = Math.toRadians(60 * k)
                        pts << new Point2(
                                cx + a * Math.cos(angle),
                                cy + a * Math.sin(angle)
                        )
                    }

                    // Create the hexagon ROI
                    def hexROI = new PolygonROI(pts, plane)

                    // Intersection with blob
                    def hexArea = new Area(hexROI.getShape())
                    hexArea.intersect(blobArea)

                    // Skip hexagons that do not intersect the blob
                    if (hexArea.isEmpty())
                        continue

                    // Split disconnected regions
                    def splitAreas = []
                    def pi = hexArea.getPathIterator(null)
                    def coords = new double[6]
                    def currentPoly = []

                    // Extract individual polygons from the intersection area
                    while (!pi.isDone()) {
                        switch (pi.currentSegment(coords)) {

                            case PathIterator.SEG_MOVETO:
                                if (!currentPoly.isEmpty()) {
                                    splitAreas << new PolygonROI(new ArrayList(currentPoly), plane)
                                    currentPoly.clear()
                                }
                                currentPoly << new Point2(coords[0], coords[1])
                                break

                            case PathIterator.SEG_LINETO:
                                currentPoly << new Point2(coords[0], coords[1])
                                break

                            case PathIterator.SEG_CLOSE:
                                if (!currentPoly.isEmpty()) {
                                    splitAreas << new PolygonROI(new ArrayList(currentPoly), plane)
                                    currentPoly.clear()
                                }
                                break
                        }
                        pi.next()
                    }

                    // Handle any remaining open polygon
                    if (!currentPoly.isEmpty()) {
                        splitAreas << new PolygonROI(new ArrayList(currentPoly), plane)
                        currentPoly.clear()
                    }

                    if (splitAreas.isEmpty())
                        continue

                    // Create detection objects
                    splitAreas.each { polyROI ->

                        // Create a new detection with the same class as the original blob
                        def obj = PathObjects.createDetectionObject(polyROI, blob.getPathClass())
                        
                        allHexes << obj
                    }
                }
            }
        }

        // Remove original blob detections
        removeObjects(blobs, true)

        // Add the newly created hexagonal detections
        addObjects(allHexes)

        logger.info(" Creati ${allHexes.size()} esagoni per ${channelName}")
    }
}

/**
 * 
 * FUNCTION: Calculate Modal Diameter
 * 
 * * Analyzes the distribution of object areas to find the mode (most frequent size).
 * Converts the modal area into the equivalent diameter of a regular hexagon.
 * * @param channelName       Name of the PathClass to analyze (e.g., "Magenta").
 * @param minAreaThreshold  Minimum area (µm²) to consider valid. Objects below this 
 * are treated as debris/noise and excluded from statistics.
 * @return                  The calculated diameter (µm) for the hexagonal grid.
 */
double calculateModalDiameter(String channelName, double minAreaThreshold) {
    print "\n--- STATISTICAL ANALYSIS: ${channelName} ---"

    def pathClass = getPathClass(channelName)
    
    // 1. Retrieve objects
    def detections = getDetectionObjects().findAll { it.getPathClass() == pathClass }
    
    if (detections.isEmpty()) {
        print "WARNING: No objects found for ${channelName}. Defaulting to 15.0 µm."
        return 15.0
    }

    // 2. Collect Area measurements (Filtering out debris)
    def areas = detections.collect { det -> 
        // Retrieve the pre-calculated measurement
        return det.getMeasurementList().get("Area µm^2")
    }.findAll { val -> 
        // Filter: Must be a valid number AND greater than the threshold
        val != null && !Double.isNaN(val) && val >= minAreaThreshold 
    }

    print "   Total objects found: ${detections.size()}"
    print "   Valid objects (> ${minAreaThreshold} µm²): ${areas.size()}"

    if (areas.isEmpty()) {
        print "WARNING: No valid objects found after filtering. Using safe default: 10.0 µm."
        return 10.0
    }

    // 3. Construct Histogram
    double binSize = 5.0 // Bin size in µm² (Controls resolution)
    double maxArea = areas.max()
    int numBins = (int)Math.ceil(maxArea / binSize) + 1
    if (numBins < 1) numBins = 1
    double[] histogram = new double[numBins]

    areas.each { area ->
        int bin = (int)(area / binSize)
        if (bin < numBins) histogram[bin]++
    }

    // 4. Moving Average Smoothing
    // Reduces noise in the histogram to find the true population peak.
    double[] smoothed = new double[numBins]
    if (numBins > 2) {
        for (int i=1; i<numBins-1; i++) smoothed[i] = (histogram[i-1] + histogram[i] + histogram[i+1]) / 3.0
    } else {
        smoothed = histogram
    }

    // 5. Identify Peak (Mode)
    double maxCount = 0
    int peakIndex = 0
    
    for (int i=0; i<numBins; i++) {
        if (smoothed[i] > maxCount) {
            maxCount = smoothed[i]
            peakIndex = i
        }
    }

    double modeArea = peakIndex * binSize + (binSize / 2.0)
    
    // 6. Geometric Conversion: Area -> Hexagon Diameter
    // Formula derived from Hexagon Area: A = (3 * sqrt(3) / 2) * R^2
    // Where R is the circumradius (half diameter).
    double exactHexDiameter = 2 * Math.sqrt( (2 * modeArea) / (3 * Math.sqrt(3)) )
    
    print "   -> Modal Area (Peak): ${String.format('%.2f', modeArea)} µm²"
    print "   -> Calculated Hex Diameter: ${String.format('%.2f', exactHexDiameter)} µm"
    
    // 7. Safety Lower Bound
    // Prevents the generation of extremely small hexagons that could cause 
    // computational issues or represent noise.
    if (exactHexDiameter < 5.0) {
        print "Calculated diameter too small (< 5 µm). Enforcing safety limit: 5.0 µm."
        return 5.0
    }

    return exactHexDiameter
}

// SCRIPT EXECUTION

// 1. Process "Yellow" Channel
// Filter out debris smaller than 15 µm²
double diametroYellow = calculateModalDiameter("Yellow", 15.0)
new HexGridFromBlobs("Yellow", diametroYellow).run()

// 2. Process "Magenta" Channel
// Filter out debris smaller than 30 µm² (Adjusted for larger cells)
double diametroMagenta = calculateModalDiameter("Magenta", 40)
new HexGridFromBlobs("Magenta", diametroMagenta).run()

// 3. Final Measurements Update
selectDetections()
addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")

print "Script execution completed successfully."