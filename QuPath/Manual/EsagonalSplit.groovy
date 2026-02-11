import qupath.lib.objects.PathObjects
import qupath.lib.roi.*
import qupath.lib.regions.ImagePlane
import qupath.lib.geom.Point2
import java.awt.geom.Area
import java.awt.geom.PathIterator
import org.slf4j.LoggerFactory

/**
 * HexGridFromBlobs
 * * Splits blobs into hexagons ONLY if area >= 300 µm².
 * Executes Tile-by-Tile to ensure correct hierarchy and performance.
 */
class HexGridFromBlobs {

    String channelName
    double diametro
    def logger = LoggerFactory.getLogger("HexGridFromBlobs")

    // Geometric parameters
    double a
    double stepX
    double stepY
    ImagePlane plane
    double pxSize 

    // MINIMUM THRESHOLD
    double minBlobAreaToSplit = 300.0 

    HexGridFromBlobs(String channelName, double diametroMicron) {
        this.channelName = channelName
        
        // Retrieve pixel calibration
        def cal = qupath.lib.gui.QuPathGUI.getInstance().getImageData().getServer().getPixelCalibration()
        this.pxSize = cal.getPixelWidthMicrons()
        
        if (Double.isNaN(pxSize) || pxSize == 0) {
            this.pxSize = 1.0
            print "Warning: Pixel size not found, assuming 1.0"
        }
        
        double diametroPixel = diametroMicron / pxSize
        this.diametro = diametroPixel

        this.a = diametro / 2.0
        this.stepX = 1.5 * a
        this.stepY = Math.sqrt(3) * a
        this.plane = ImagePlane.getDefaultPlane()
    }

    /**
     * Executes subdivision searching for blobs ONLY inside the 'parentObject' (the Tile).
     */
    void runOnParent(def parentObject) {

        // Find child blobs of the current Tile belonging to the specific channel
        def blobs = parentObject.getChildObjects().findAll {
            it.isDetection() && it.getPathClass() == getPathClass(channelName)
        }

        if (blobs.isEmpty()) {
            return
        }

        def allHexes = []
        def blobsToRemove = [] 

        blobs.each { blob ->

            // 1. READ AREA
            def areaMicrons = blob.getMeasurementList().get("Area µm^2")

            // If measurement is missing, skip
            if (areaMicrons == null || Double.isNaN(areaMicrons)) return 

            // 2. FILTER: If small (< 300), SKIP and leave intact
            if (areaMicrons < minBlobAreaToSplit) return 

            // 3. IF LARGE (>= 300): PROCEED TO SPLIT
            blobsToRemove << blob
            
            def roi = blob.getROI()
            def blobArea = new Area(roi.getShape())

            double xMin = roi.getBoundsX()
            double yMin = roi.getBoundsY()
            double w = roi.getBoundsWidth()
            double h = roi.getBoundsHeight()

            int nCols = Math.ceil(w / stepX) as int
            int nRows = Math.ceil(h / stepY) as int

            for (int col = 0; col < nCols; col++) {
                for (int row = 0; row < nRows; row++) {

                    double cx = xMin + col * stepX
                    double cy = yMin + row * stepY
                    if (col % 2 == 1) cy += stepY / 2

                    def pts = []
                    for (int k = 0; k < 6; k++) {
                        double angle = Math.toRadians(60 * k)
                        pts << new Point2(cx + a * Math.cos(angle), cy + a * Math.sin(angle))
                    }

                    def hexROI = new PolygonROI(pts, plane)
                    def hexArea = new Area(hexROI.getShape())
                    hexArea.intersect(blobArea)

                    if (hexArea.isEmpty()) continue

                    def splitAreas = []
                    def pi = hexArea.getPathIterator(null)
                    def coords = new double[6]
                    def currentPoly = []

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
                    if (!currentPoly.isEmpty()) {
                        splitAreas << new PolygonROI(new ArrayList(currentPoly), plane)
                    }

                    if (splitAreas.isEmpty()) continue

                    splitAreas.each { polyROI ->
                        def obj = PathObjects.createDetectionObject(polyROI, blob.getPathClass())
                        allHexes << obj
                    }
                }
            }
        }

        // APPLY CHANGES 
        if (!blobsToRemove.isEmpty()) {
            
            // 1. Remove old large blobs
            // Using generic removeObjects, QuPath handles hierarchy
            removeObjects(blobsToRemove, true)
            
            // 2. Add new hexagons
            // Using generic addObjects. Since they are geometrically INSIDE the tile,
            // resolveHierarchy() at the end will place them in the right spot.
            addObjects(allHexes)
        }
    }
}

/**
 * Function: Calculate Modal Diameter (Global)
 */
double calculateModalDiameter(String channelName, double minAreaThreshold) {
    print "\n--- GLOBAL STATISTICS: ${channelName} ---"
    def pathClass = getPathClass(channelName)
    def detections = getDetectionObjects().findAll { it.getPathClass() == pathClass }
    
    if (detections.isEmpty()) return 15.0

    def areas = detections.collect { det -> 
        return det.getMeasurementList().get("Area µm^2")
    }.findAll { val -> 
        val != null && !Double.isNaN(val) && val >= minAreaThreshold 
    }

    if (areas.isEmpty()) return 10.0

    double binSize = 5.0
    double maxArea = areas.max()
    int numBins = (int)Math.ceil(maxArea / binSize) + 1
    if (numBins < 1) numBins = 1
    double[] histogram = new double[numBins]

    areas.each { area ->
        int bin = (int)(area / binSize)
        if (bin < numBins) histogram[bin]++
    }
    
    double[] smoothed = new double[numBins]
    if (numBins > 2) {
        for (int i=1; i<numBins-1; i++) smoothed[i] = (histogram[i-1] + histogram[i] + histogram[i+1]) / 3.0
    } else { smoothed = histogram }

    double maxCount = 0
    int peakIndex = 0
    for (int i=0; i<numBins; i++) {
        if (smoothed[i] > maxCount) { maxCount = smoothed[i]; peakIndex = i }
    }

    double modeArea = peakIndex * binSize + (binSize / 2.0)
    double exactHexDiameter = 2 * Math.sqrt( (2 * modeArea) / (3 * Math.sqrt(3)) )
    
    print "   -> Modal Area: ${String.format('%.2f', modeArea)} µm² | Hex Diameter: ${String.format('%.2f', exactHexDiameter)} µm"
    
    if (exactHexDiameter < 5.0) return 5.0
    return exactHexDiameter
}

// SCRIPT EXECUTION

// 1. Find Tiles
def tiles = getAnnotationObjects().findAll { it.getPathClass() == null && it.getName() != null && it.getName().startsWith("Tile") }
if (tiles.isEmpty()) {
    print "Error: No 'Tile' found."
    return
}
print "Found ${tiles.size()} Tiles. Starting processing..."

// 2. Pre-processing measurements (Required to read the area of existing blobs)
print "Calculating initial measurements..."
selectDetections() 
addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")

// 3. Calculate optimal diameters
double diametroYellow = calculateModalDiameter("Yellow", 15.0)
double diametroMagenta = calculateModalDiameter("Magenta", 40.0)

// Initialize processors
def processorYellow = new HexGridFromBlobs("Yellow", diametroYellow)
def processorMagenta = new HexGridFromBlobs("Magenta", diametroMagenta)

// 4. LOOP OVER TILES
tiles.eachWithIndex { tile, i ->
    
    print "Processing Tile ${i+1}/${tiles.size()}..."
    
    // Select the current tile
    selectObjects(tile)
    
    // Execute on tile children
    processorYellow.runOnParent(tile)
    processorMagenta.runOnParent(tile)
}

print " Processing completed. Updating hierarchy..."

// 5. FINAL HIERARCHY FIX
resetSelection() 

// This command fixes parenting (puts detections into the correct tiles)
resolveHierarchy()

print "Calculating final measurements..."
selectDetections()
addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")

print "Script completed successfully."