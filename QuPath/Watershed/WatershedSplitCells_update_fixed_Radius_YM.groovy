/**
 * Automated script for differential segmentation and spatial analysis of 
 * CD3+ (Yellow) and CD20+ (Magenta) lymphocyte populations within 'Tile' annotations.
 * * Workflow:
 * 1. Identify ROI tiles.
 * 2. Clear previous specific detections.
 * 3. Perform two-pass Watershed segmentation (CD3 then CD20) with population-specific parameters.
 * 4. Merge results and promote objects to the root hierarchy.
 * 5. Apply Quality Control (QC) filtering based on nuclear area.
 * 6. Compute spatial metrics (distances).
 */

// 1. INITIALIZATION AND ROI IDENTIFICATION
// Find all annotation objects named "Tile" without an existing class
def tiles = getAnnotationObjects().findAll { it.getPathClass() == null && it.getName().startsWith("Tile") }

if (tiles.isEmpty()) {
    print "Error: No 'Tile' annotations found."
    return
}
print "Ready to process detections in ${tiles.size()} tiles..."

// 2. CLEANUP PREVIOUS DATA
// Identify and remove existing 'Magenta' (CD20) or 'Yellow' (CD3) detections 
// to ensure a clean analysis run.
def allOriginalBlobs = getDetectionObjects().findAll { 
        it.getPathClass() == getPathClass("Magenta") || it.getPathClass() == getPathClass("Yellow")
}
    
// Remove identified objects
removeObjects(allOriginalBlobs, true) 

// 3. BATCH PROCESSING
// Iterate through each identified tile
tiles.each { tile ->
    // Select the current tile as the parent object for detection
    selectObjects(tile) 

    // PHASE A: CD3 DETECTION (YELLOW
    // Run Watershed Cell Detection on the CD3 channel
    // Parameters optimized for T-cells (smaller radius)
    runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', """
        {
          "detectionImage": "CD3", 
          "requestedPixelSizeMicrons": 0.5,
          "backgroundRadiusMicrons": 8.0,
          "medianRadiusMicrons": 4.0,
          "cellExpansionMicrons": 0.01,
          "sigmaMicrons": 1.5,
          "minAreaMicrons": 20.0,
          "maxAreaMicrons": 200.0,
          "threshold": 0.1,
          "includeNuclei": true,
          "smoothBoundaries": true,
          "makeMeasurements": false
        }
    """)
    
    // Retrieve the newly created detections (children of the tile)
    def newYellowDetections = new ArrayList(tile.getChildObjects().findAll { it.isDetection() })
    // Assign the 'Yellow' class to CD3+ cells
    newYellowDetections.each { it.setPathClass(getPathClass("Yellow")) }

    
    // PHASE B: CD20 DETECTION (MAGENTA
    // Run Watershed Cell Detection on the CD20 channel
    // Note: Running this plugin overwrites the current children of the tile in the viewer,
    // which is why we stored the Yellow detections in a list above.
    // Parameters optimized for B-cells (larger median radius and min area)
    runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', """
        {
          "detectionImage": "CD20", 
          "requestedPixelSizeMicrons": 0.5,
          "backgroundRadiusMicrons": 8.0,
          "medianRadiusMicrons": 5.0,
          "cellExpansionMicrons": 0.01,
          "sigmaMicrons": 1.5,
          "minAreaMicrons": 40.0,
          "maxAreaMicrons": 200.0,
          "threshold": 0.1,
          "includeNuclei": true,
          "smoothBoundaries": true,
          "makeMeasurements": false
        }
    """)
    
    // Retrieve the new detections (currently the only children visible on the tile)
    def newMagentaDetections = new ArrayList(tile.getChildObjects().findAll { it.isDetection() })
    // Assign the 'Magenta' class to CD20+ cells
    newMagentaDetections.each { it.setPathClass(getPathClass("Magenta")) }


    // PHASE C: MERGE RESULTS
    // The tile currently contains only Magenta detections.
    // Add the stored Yellow detections back to the tile to combine populations.
    addObjects(newYellowDetections) 
}

// 4. HIERARCHY REORGANIZATION
// Promote detections to the Root object to facilitate global distance calculations 
// across tile boundaries.

// Collect all final detections (both Magenta and Yellow)
def allFinalDetections = new ArrayList(getDetectionObjects())

// (Optional: List tiles if needed for removal, though not removed in this logic)
def allTiles = getAnnotationObjects().findAll { it.getName() != null && it.getName().startsWith("Tile") }

// Add all detections to the image hierarchy root
addObjects(allFinalDetections)


// 5. MORPHOMETRY AND QUALITY CONTROL (QC)
selectDetections()
// Compute morphological features
addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")

// QC FILTER: NUCLEUS AREA
// Define strict threshold for minimum nucleus size
def minNucleusArea = 10.0

// Identify artifacts or fragments with nuclei smaller than the threshold
def detectionsToRemove = getDetectionObjects().findAll { 
    // Retrieve the specific measurement
    def nucleusArea = it.getMeasurementList().get("Nucleus: Area µm^2")
    // Flag for removal if measurement is missing or below threshold
    return nucleusArea == null || nucleusArea < minNucleusArea 
}

// Execute filtering
if (!detectionsToRemove.isEmpty()) {
    print "QC FILTER: Detected ${detectionsToRemove.size()} objects with nucleus < ${minNucleusArea} µm²."
    removeObjects(detectionsToRemove, true)
    print "QC FILTER: Small artifacts removed."
} else {
    print "QC FILTER: All detections passed the area threshold."
}


// 6. SPATIAL ANALYSIS
// Select valid detections
selectDetections()
// Compute Euclidean distances between centroids of all detections
detectionCentroidDistances()
// Finalize and resolve object hierarchy
resolveHierarchy()

print "Script completed: Detections filtered and distances calculated.”
