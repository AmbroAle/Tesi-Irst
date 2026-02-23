import qupath.lib.objects.PathObjects
import qupath.lib.roi.*
import qupath.lib.regions.ImagePlane
import qupath.lib.geom.Point2
import java.awt.geom.Area
import org.slf4j.LoggerFactory
import qupath.lib.roi.GeometryTools

/**
 * HexGridFromBlobs
 * * Splits blobs into hexagons ONLY if area >= 300 µm².
 * * Maintaining the shape of the original structure.
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

    void runOnParent(def parentObject) {

        // Find child blobs of the current Tile
        def blobs = parentObject.getChildObjects().findAll {
            it.isDetection() && it.getPathClass() == getPathClass(channelName)
        }

        if (blobs.isEmpty()) return

        def allHexes = []
        def blobsToRemove = [] 

        blobs.each { blob ->

            def areaMicrons = blob.getMeasurementList().get("Area µm^2")
            if (areaMicrons == null || Double.isNaN(areaMicrons)) return 
            if (areaMicrons < minBlobAreaToSplit) return 

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
                    
                    // Pure mathematical intersection
                    hexArea.intersect(blobArea)

                    if (hexArea.isEmpty()) continue

                    // GEOMETRY ENGINE (GEOMETRY TOOLS)
                    
                    // Convert the Java Area into a Temporary QuPath ROI
                    def tempRoi = RoiTools.getShapeROI(hexArea, plane)
                    
                    // Extract JTS Geometry directly from the ROI
                   def geom = tempRoi.getGeometry()
                    
                    // If the intersection created multiple disconnected islands, iterate through them.
            
                    for (int i = 0; i < geom.getNumGeometries(); i++) {
                        def singleGeom = geom.getGeometryN(i)
                        
                        // Re-convert the single island (with its holes) into the final ROI
                        def finalRoi = GeometryTools.geometryToROI(singleGeom, plane)
                        
                        // Create the object
                        def obj = PathObjects.createDetectionObject(finalRoi, blob.getPathClass())
                        allHexes << obj
                    }
                }
            }
        }

        // APPLY CHANGES 
        if (!blobsToRemove.isEmpty()) {
            removeObjects(blobsToRemove, true)
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

// 2. Pre-processing measurements 
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
    
    selectObjects(tile)
    
    processorYellow.runOnParent(tile)
    processorMagenta.runOnParent(tile)
}

print " Processing completed. Updating hierarchy..."

// 5. FINAL HIERARCHY FIX
resetSelection() 

resolveHierarchy()

print "Calculating final measurements..."
selectDetections()
addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")

// Safe distance calculation
try {
    detectionCentroidDistances()
} catch (Exception e) {
    print "Warning: Could not calculate distances (likely missing one of the cell classes)."
}

print "Script completed successfully."