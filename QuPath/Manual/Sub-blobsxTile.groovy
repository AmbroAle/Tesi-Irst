// SplitLargeBlobsIrregular.groovy
// Splits large detection blobs into smaller sub-blobs while preserving the original shape

import qupath.lib.objects.PathObjects
import qupath.lib.roi.*
import qupath.lib.roi.RoiTools
import qupath.lib.regions.ImagePlane
import java.awt.geom.Area
import java.awt.geom.Rectangle2D

// PARAMETERS
def channelName = "Magenta"      // Detection class to process
def maxWidth = 200.0             // Maximum width of a sub-blob (µm)
def maxHeight = 200.0            // Maximum height of a sub-blob (µm)
def plane = ImagePlane.getDefaultPlane()  // Image plane for new ROIs

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()

// GET ALL DETECTIONS 
def blobs = getDetectionObjects().findAll { it.getPathClass() == getPathClass(channelName) }

if (blobs.isEmpty()) {
    print "No blobs found for channel '${channelName}'."
    return
}

print "INFO: Found ${blobs.size()} blobs for channel '${channelName}'."

// PROCESS EACH BLOB 
blobs.each { blob ->

    def roi = blob.getROI()
    if (!roi) return  // Skip if no ROI

    def xMin = roi.getBoundsX()
    def yMin = roi.getBoundsY()
    def w = roi.getBoundsWidth()
    def h = roi.getBoundsHeight()
    def blobClass = blob.getPathClass()

    // Skip blobs that are already within the maximum size
    if (w <= maxWidth && h <= maxHeight)
        return

    // Determine the number of rows and columns for splitting
    int nCols = Math.ceil(w / maxWidth) as int
    int nRows = Math.ceil(h / maxHeight) as int
    double stepX = w / nCols
    double stepY = h / nRows

    def subBlobs = []

    // Loop over each row and column to create sub-blobs
    for (int r = 0; r < nRows; r++) {
        for (int c = 0; c < nCols; c++) {

            // Compute sub-rectangle boundaries
            double sx = xMin + c * stepX
            double sy = yMin + r * stepY
            double sw = (c == nCols - 1) ? (xMin + w - sx) : stepX
            double sh = (r == nRows - 1) ? (yMin + h - sy) : stepY

            // Create a clipping rectangle
            Rectangle2D clipRect = new Rectangle2D.Double(sx, sy, sw, sh)
            Area clipArea = new Area(clipRect)

            // Intersect with the original blob shape to preserve the exact contour
            Area blobArea = new Area(roi.getShape())
            blobArea.intersect(clipArea)

            if (blobArea.isEmpty()) continue  // Skip empty areas

            // Create new ROI for the sub-blob
            def subROI = RoiTools.getShapeROI(blobArea, plane)
            subBlobs << PathObjects.createDetectionObject(subROI, blobClass)
        }
    }

    // Replace the original blob with its sub-blobs
    if (!subBlobs.isEmpty()) {
        hierarchy.removeObject(blob, true)   // Remove original blob
        hierarchy.addObjects(subBlobs)       // Add all sub-blobs
        print "Blob at (${xMin.round(1)}, ${yMin.round(1)}) split into ${subBlobs.size()} irregular sub-blobs."
    }
}

print "INFO: All blobs processed."