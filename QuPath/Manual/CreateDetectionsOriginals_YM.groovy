setPixelSizeMicrons(0.252600, 0.252600)
// Create tiles
selectObjectsByClassification("Region*");
runPlugin('qupath.lib.algorithms.TilerPlugin', '{"tileSizeMicrons":510.0,"trimToROI":true,"makeAnnotations":true,"removeParentAnnotation":false}')

tiles = getAnnotationObjects().findAll{it.getPathClass() == null}
print tiles

// Create Magenta annotations
tiles.each { tile ->
    selectObjects(tile)
    addPixelClassifierMeasurements("CD20_full_thr1", "CD20_full_thr1")
    createAnnotationsFromPixelClassifier("CD20_full_thr1", 10.0, 0.0, "SPLIT", "IGNORE_EXISTING")
    addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")
}

// Create Yellow annotations
tiles.each { tile ->
    selectObjects(tile)
    addPixelClassifierMeasurements("CD3_full_thr1", "CD3_full_thr1")
    createAnnotationsFromPixelClassifier("CD3_full_thr1", 10.0, 0.0, "SPLIT", "IGNORE_EXISTING")
    addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER", "NUCLEUS_CELL_RATIO")
}

// Convert all annotations (Magenta/Yellow) to DETECTIONS
// Trova tutte le annotazioni appena create
def annotsToConvert = getAnnotationObjects().findAll {
    it.getPathClass() == getPathClass("Magenta") || it.getPathClass() == getPathClass("Yellow")
}

if (annotsToConvert.isEmpty()) {
    print "Errore: Nessuna annotazione 'Magenta' o 'Yellow' è stata creata. Controlla i tuoi Pixel Classifiers."
    return
}

// Create a list of new detections
def newDetections = annotsToConvert.collect {
    return PathObjects.createDetectionObject(it.getROI(), it.getPathClass())
}

selectDetections();
addShapeMeasurements("AREA", "LENGTH", "CIRCULARITY", "SOLIDITY", "MAX_DIAMETER", "MIN_DIAMETER")

// // Remove old annotations and add the new detections
removeObjects(annotsToConvert, true)
addObjects(newDetections)

print "Processing Complete: Created ${newDetections.size()} total detection 'blobs'."