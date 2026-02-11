import qupath.lib.objects.PathObjects
import qupath.lib.roi.RoiTools
import java.awt.geom.Area
import java.awt.BasicStroke
import org.slf4j.LoggerFactory

/**
 * HexFusionByAdjacency
 * Merges small hexagonal detections into adjacent larger ones.
 * Adapted to run Tile-by-Tile for better performance and hierarchy management.
 */
class HexFusionByAdjacency {

    String channelName
    double minArea = 10.0
    double gridSize = 50.0
    double buffer = 1.0
    def logger = LoggerFactory.getLogger(HexFusionByAdjacency)

    HexFusionByAdjacency(String channelName) {
        this.channelName = channelName
    }

    Area bufferedArea(def roi, double buffer) {
        def shape = roi.getShape()
        def area = new Area(shape)
        def stroke = new BasicStroke((float)(2 * buffer))
        area.add(new Area(stroke.createStrokedShape(shape)))
        return area
    }

    void runOnParent(def parentObject) {

        // 1. LOAD DETECTIONS FROM TILE
        def allHexes = parentObject.getChildObjects().findAll {
            it.isDetection() && it.getPathClass() == getPathClass(channelName)
        }

        if (allHexes.isEmpty()) return

        // 2. SPLIT BIG / SMALL
        // Note: We assume the area is already calculated. If missing, we calculate it 
        // on the fly geometrically to avoid errors if measurements are not updated.
        
        def bigHexes = []
        def smallHexes = []

        allHexes.each { det ->
            // Try to get the measurement; if missing, use ROI geometry
            def area = det.measurements.get("Area µm^2")
            if (area == null || Double.isNaN(area)) {
                 // Geometric fallback (pixel * calibration)
                 // For simplicity, we assume measurements exist.
                 // If missing, treat as 0.0 (small).
                 area = 0.0 
            }
            
            if (area >= minArea) bigHexes << det
            else smallHexes << det
        }

        if (bigHexes.isEmpty() || smallHexes.isEmpty()) return 

        // 3. BUILD SPATIAL GRID
        def grid = [:].withDefault { [] }
        allHexes.each { obj ->
            def roi = obj.getROI()
            def key = [
                Math.floor(roi.getCentroidX() / gridSize),
                Math.floor(roi.getCentroidY() / gridSize)
            ]
            grid[key] << obj
        }

        // 4. FUSION PROCESS
        def newObjects = []
        def absorbedSmalls = [] as Set
        def fusedBigs = [] as Set 

        bigHexes.each { big ->
            def roiBig = big.getROI()
            def gx = Math.floor(roiBig.getCentroidX() / gridSize)
            def gy = Math.floor(roiBig.getCentroidY() / gridSize)

            def candidates = []
            for (dx in -1..1)
                for (dy in -1..1)
                    candidates += grid[[gx + dx, gy + dy]]

            candidates = candidates.findAll { c ->
                def cArea = c.measurements.get("Area µm^2")
                // Fallback area check
                if (cArea == null) cArea = 0.0
                return cArea < minArea && !absorbedSmalls.contains(c)
            }

            def mergedArea = new Area(roiBig.getShape())
            def bufferedBig = bufferedArea(roiBig, buffer)
            def absorbedCount = 0

            candidates.each { small ->
                def a = new Area(bufferedBig)
                a.intersect(new Area(small.getROI().getShape()))
                if (!a.isEmpty()) {
                    mergedArea.add(new Area(small.getROI().getShape()))
                    absorbedSmalls << small
                    absorbedCount++
                }
            }

            if (absorbedCount > 0) {
                def newROI = RoiTools.getShapeROI(mergedArea, roiBig.getImagePlane())
                def newObj = PathObjects.createDetectionObject(newROI, big.getPathClass())
                newObjects << newObj
                fusedBigs << big
            }
        }

        // 5. APPLY CHANGES
        def objectsToRemove = []
        objectsToRemove.addAll(absorbedSmalls)
        objectsToRemove.addAll(fusedBigs)

        if (!objectsToRemove.isEmpty()) {
            removeObjects(objectsToRemove, true)
            addObjects(newObjects)
        }
    }
}

// SCRIPT EXECUTION

def tiles = getAnnotationObjects().findAll { it.getPathClass() == null && it.getName() != null && it.getName().startsWith("Tile") }

if (tiles.isEmpty()) {
    print "Error: No 'Tile' found."
    return
}

print "Found ${tiles.size()} Tiles. Starting fusion..."

// Initialize Processors
def fuserYellow = new HexFusionByAdjacency("Yellow")
def fuserMagenta = new HexFusionByAdjacency("Magenta")

// LOOP OVER TILES
tiles.eachWithIndex { tile, i ->
    print "--> Processing Tile ${i+1} / ${tiles.size()} (${tile.getName()})"
    
    // Execute Fusion directly on the tile (without selecting it to avoid log spam)
    fuserYellow.runOnParent(tile)
    fuserMagenta.runOnParent(tile)
}

print " Tile processing completed."

// HIERARCHY UPDATE
resetSelection()
resolveHierarchy()

print "Calculating final measurements..."
selectDetections()
detectionCentroidDistances()
// This recalculates measurements for the newly fused objects
addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")

print "Fusion Script completed successfully."