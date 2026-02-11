import qupath.lib.objects.PathObjects

// Set pixel size (if not already set from metadata)
setPixelSizeMicrons(0.252600, 0.252600)

// Create tiles
selectObjectsByClassification("Region*");
runPlugin('qupath.lib.algorithms.TilerPlugin', '{"tileSizeMicrons":510.0,"trimToROI":true,"makeAnnotations":true,"removeParentAnnotation":false}')

// Find the newly created tiles (unclassified annotations)
tiles = getAnnotationObjects().findAll{it.getPathClass() == null}
print tiles

// Create Magenta ANNOTATIONS
tiles.each { tile ->
    selectObjects(tile)
    // Note: addPixelClassifierMeasurements is technically optional if createAnnotations is run immediately, 
    // but useful if you need measurements on the tile itself.
    addPixelClassifierMeasurements("CD20_full_thr1", "CD20_full_thr1")
    createAnnotationsFromPixelClassifier("CD20_full_thr1", 10.0, 0.0, "SPLIT", "IGNORE_EXISTING")
    addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")
}

// Create Yellow ANNOTATIONS
tiles.each { tile ->
    selectObjects(tile)
    addPixelClassifierMeasurements("CD3_full_thr1", "CD3_full_thr1")
    createAnnotationsFromPixelClassifier("CD3_full_thr1", 10.0, 0.0, "SPLIT", "IGNORE_EXISTING")
    addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")
}

// Convert ALL annotations (Magenta/Yellow) to DETECTIONS

// Find all newly created annotations
def annotsToConvert = getAnnotationObjects().findAll {
    it.getPathClass() == getPathClass("Magenta") || it.getPathClass() == getPathClass("Yellow")
}

if (annotsToConvert.isEmpty()) {
    print "Error: No 'Magenta' or 'Yellow' annotations created. Check your Pixel Classifiers."
    return
}

// Create a list of new detections based on the annotations
def newDetections = annotsToConvert.collect {
    return PathObjects.createDetectionObject(it.getROI(), it.getPathClass())
}

// Calculate measurements for the new detections
selectDetections();
addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER")

// Remove old annotations and add the new detections
removeObjects(annotsToConvert, true)
addObjects(newDetections)

// Final hierarchy resolution and distance calculations
selectDetections();
detectionCentroidDistances()
resolveHierarchy()

print "Step 1 completed: Created ${newDetections.size()} total 'blob' detections."