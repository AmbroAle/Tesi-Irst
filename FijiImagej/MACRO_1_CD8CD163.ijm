//AUTHORS: Alessandro Ambrogiani, Filippo Piccinini
//CONTACTS: ambrogiani28@gmail.com, filippo.piccinini85@gmail.com
//DATE: 20251017
//NAME: MACRO_1_CD8CD163
//DESCRIPTION: This ImageJ macro processes histological fluorescence images and allows manual cleaning of the GREEN channel (CD80) and the RED channel (CD163). 
// It enables the user to interactively select unwanted regions using the freehand tool, create a binary mask, and apply it through a Multiply operation 
// to remove selected areas from the channel. After modification, the script automatically rebuilds the RGB image from the individual red, green, 
// and blue channels, saves the composite image as a PNG file, and logs the performed operations. This workflow ensures controlled manual 
// preprocessing of fluorescence images for further quantitative analysis.
//COPYRIGHT: 
/*
 MiAI (Microscopy and Artificial Intelligence) Toolbox
 Copyright © 2025 Alessandro Ambrogiani, Filippo Piccinini.
 University of Bologna, Italy. All rights reserved.
 This program is free software; you can redistribute it and/or modify it
 under the terms of the GNU General Public License version 3 (or higher)
 as published by the Free Software Foundation. This program is
 distributed WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.
 */

//Define input file
run("Close All");
path1 = File.openDialog("Select a File");
dir1 = File.getParent(path1);
name1 = File.getName(path1);
print("Path:", path1);
print("Name:", name1);
print("Directory:", dir1);
dotIndex = lastIndexOf(name1, ".");
if (dotIndex != -1) {
    name1WithoutExtension = substring(name1, 0, dotIndex);
} else {
    name1WithoutExtension = name1; 
}

//Define output parameters
outputROIname1 = getString("Enter the name for the ROI analysed: ", "");
dir2 = getDirectory("Set the output directory (subfolders will be automatically created): ");
dir2Subfolder = dir2 + "/" + name1WithoutExtension + "_" + outputROIname1 + "/";
File.makeDirectory(dir2Subfolder); 

// Create log file
logFile = dir2Subfolder + name1WithoutExtension + outputROIname1 + "_processing_log.txt";

//Open input file
list = getFileList(dir1);
print("Directory contains "+list.length+" files");
open(path1);
selectImage(nImages);
bitDepth1 = bitDepth();
print(bitDepth1);

if(bitDepth1 == 24){
    print("Image is RGB.");
} else {
    print("Image is not RGB.");
    run("Stack to RGB");
    selectImage(nImages);
    close();
    selectImage(nImages);
    rename(name1);
}

if (bitDepth1 != 24) {
    run("RGB Color");
}

// Save ColourDeconvolution images
run("Colour Deconvolution", "vectors=[FastRed FastBlue DAB]");

selectImage(name1+" (RGB)-(Colour_1)");
Dialog.create("");
Dialog.addMessage("Select name for CH1");
Dialog.addChoice("name", newArray("CD163"), "CD163");
Dialog.show();
outputCH1name1 = Dialog.getChoice();
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + ".tif");
run("8-bit");
run("Invert");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_BN.tif");
run("Duplicate...", " ");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_BN-1.tif");

selectImage(name1+" (RGB)-(Colour_2)");
Dialog.create("");
Dialog.addMessage("Select name for CH2");
Dialog.addChoice("name", newArray("NUCLEI"), "NUCLEI");
Dialog.show();
outputCH2name1 = Dialog.getChoice();
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + ".tif");
run("8-bit");
run("Invert");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_BN.tif");
run("Duplicate...", " ");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_BN-1.tif");

selectImage(name1+" (RGB)-(Colour_3)");
Dialog.create("");
Dialog.addMessage("Select name for CH3");
Dialog.addChoice("name", newArray("CD8"), "CD8");
Dialog.show();
outputCH3name1 = Dialog.getChoice();
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + ".tif");
run("8-bit");
run("Invert");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_BN.tif");
run("Duplicate...", " ");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_BN-1.tif");

run("Close All");

// FUNCTION: PROCESS SINGLE CHANNEL WITH PREVIEW
function processChannel(channelName, shortName, defaultAlgo, defaultRadius) {
    
    originalImage = dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + channelName + "_BN-1.tif";
    
    // Variables to store final choices
    finalAlgo = defaultAlgo;
    finalMedianSelection = "NO";
    finalRadius = defaultRadius;

    // PREVIEW LOOP 
    while(true) {
        run("Close All");
        open(originalImage);
        originalTitle = getTitle();
        run("Duplicate...", "title=PreviewImage");
        selectImage("PreviewImage");

        // 1. Ask parameters
        Dialog.create("Settings for " + shortName);
        Dialog.addMessage("Adjust settings. A preview will be shown next.");
        Dialog.addChoice("Threshold Algorithm:", 
            newArray("Default", "Huang", "Otsu", "Minimum", "Triangle", "Mean", "Moments", "Percentile", "Yen", "Intermodes"), 
            finalAlgo);
        
        // Default logic for checkboxes/choices based on previous runs or defaults
        medianCheck = false;
        if (finalMedianSelection == "YES") { medianCheck = true; }
        
        Dialog.addCheckbox("Apply Median Filter", medianCheck);
        Dialog.addNumber("Median Radius:", finalRadius);
        Dialog.show();

        currentAlgo = Dialog.getChoice();
        isMedian = Dialog.getCheckbox();
        currentRadius = Dialog.getNumber();

        // 2. Apply Process on Preview Image
        selectImage("PreviewImage");
        
        if (isMedian) {
            run("Median...", "radius=" + currentRadius);
        } 

        setAutoThreshold(currentAlgo + " dark no-reset");
        setOption("BlackBackground", true);
        run("Convert to Mask");
        
        // 3. Show Result and Ask for Confirmation
        // Position windows so user can see
        selectImage("PreviewImage");
        setLocation(100, 100);
        
        medianText = "NO";
		if (isMedian) {
		    medianText = "YES";
		}
        Dialog.create("Preview Result: " + shortName);
        Dialog.addMessage("Check the 'PreviewImage'.\nAlgorithm: " + currentAlgo + "\nMedian: " + medianText + " (Radius: " + currentRadius + ")");
        Dialog.addChoice("Are you satisfied?", newArray("YES", "NO - Try Again"), "YES");
        Dialog.show();
        
        satisfaction = Dialog.getChoice();

        if (satisfaction == "YES") {
            // Save final values
            finalAlgo = currentAlgo;
            finalRadius = currentRadius;
            if (isMedian) { finalMedianSelection = "YES"; } else { finalMedianSelection = "NO"; }
            
            // Save the valid image
            selectImage("PreviewImage");
            saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + channelName + "_TH.tif");
            close(); // Close original
            break; // EXIT LOOP
        } else {
            // Update defaults for next loop iteration so user doesn't have to re-type
            finalAlgo = currentAlgo;
            finalRadius = currentRadius;
            if (isMedian) { finalMedianSelection = "YES"; } else { finalMedianSelection = "NO"; }
            // Loop repeats...
        }
    }


    result = newArray(finalAlgo, finalMedianSelection, finalRadius);
    return result;
}

// Process CH1 
resCH1 = processChannel(outputCH1name1, "CH1 (" + outputCH1name1 + ")", "Default", 2);
algoCH1 = resCH1[0];
medianAppliedCH1 = resCH1[1];
medianRadiusCH1 = resCH1[2];

// Process CH2
resCH2 = processChannel(outputCH2name1, "CH2 (" + outputCH2name1 + ")", "Moments", 0);
algoCH2 = resCH2[0];
medianAppliedCH2 = resCH2[1];
medianRadiusCH2 = resCH2[2];

// Process CH3
resCH3 = processChannel(outputCH3name1, "CH3 (" + outputCH3name1 + ")", "Yen", 12);
algoCH3 = resCH3[0];
medianAppliedCH3 = resCH3[1];
medianRadiusCH3 = resCH3[2];

print("All the binary masks are processed.");

// NORMALIZE AND SAVE THRESHOLDED MASKS
run("Close All");

open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_TH.tif");
run("Divide...", "value=255.000");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_TH.tif");
run("Close All");
    
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_TH.tif");
run("Divide...", "value=255.000");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_TH.tif");
run("Close All");
    
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_TH.tif");
run("Divide...", "value=255.000");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_TH.tif");
run("Close All");

// Overlap the binary masks

// CH1 part
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_BN.tif");
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_TH.tif");
imageCalculator("Multiply create", name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_BN.tif", name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_TH.tif");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif");
run("Close All");

// CH2 part
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_BN.tif");
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_TH.tif");
imageCalculator("Multiply create", name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_BN.tif", name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_TH.tif");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif");
run("Close All");

// CH3 part
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_BN.tif");
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_TH.tif");
imageCalculator("Multiply create", name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_BN.tif", name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_TH.tif");
saveAs("Tiff", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif");
run("Close All");

// Merge channels
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif");
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif");
open(dir2Subfolder + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif");

Dialog.create("Choose color scheme for merged channels");
Dialog.addMessage("CH1 = " + outputCH1name1 + 
                  "\nCH2 = " + outputCH2name1 + 
                  "\nCH3 = " + outputCH3name1);
Dialog.addMessage("Select a color scheme:\n" +
                  "A: CH1=green, CH2=blue, CH3=red\n" +
                  "B: CH1=gray, CH2=blue, CH3=yellow\n" +
                  "C: CH1=magenta, CH2=blue, CH3=cyan\n" +
                  "D: CH1=cyan, CH2=blue, CH3=magenta\n" +
                  "E: CH1=red, CH2=blue, CH3=green\n" +
                  "F: CH1=magenta, CH2=blue, CH3=yellow\n" +
                  "G: CH1=yellow, CH2=blue, CH3=magenta");
Dialog.addChoice("Scheme:", newArray("A","B","C","D","E","F","G"), "E");
Dialog.show();
selectionString2 = Dialog.getChoice();

// MERGE CHANNELS

if(selectionString2 == "A"){
    run("Merge Channels...", "c1=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif] c2=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif] c3=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif] create");
} else if (selectionString2 == "B"){
    run("Merge Channels...", "c3=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif] c4=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif] c7=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif] create");
} else if (selectionString2 == "C"){
    run("Merge Channels...", "c3=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif] c5=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif] c6=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif] create");
} else if (selectionString2 == "D"){
    run("Merge Channels...", "c3=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif] c5=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif] c6=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif] create");
} else if (selectionString2 == "E"){
    run("Merge Channels...", "c1=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif] c2=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif] c3=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif] create");
} else if (selectionString2 == "F"){
    run("Merge Channels...", "c3=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif] c6=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif] c7=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif] create");
} else if (selectionString2 == "G"){
    run("Merge Channels...", "c3=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif] c6=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif] c7=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif] create");
} else {
    run("Merge Channels...", "c1=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH3name1 + "_DEF.tif] c2=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH1name1 + "_DEF.tif] c3=[" + name1WithoutExtension + outputROIname1 + "_" + outputCH2name1 + "_DEF.tif] create");
}
run("Stack to RGB");
rename("Composite_to_Clean");

// Function for manual cleaning
function manualChannelCleaning(channelColor, channelLabel) {
    selectionManual = getString("Enter YES to manually remove areas from the " + channelColor + " channel (" + channelLabel + ")", "NO");
    if (selectionManual != "YES") return "NO"; 

    print("Draw all regions you want to remove from the " + channelColor + " channel.");
    print("Tip: Hold SHIFT while drawing to add multiple regions to the same selection.");

    selectImage(getTitle());
    setTool("freehand");
    waitForUser("Draw all areas to remove on the COLOR image for " + channelLabel + ".\nHold SHIFT to add multiple regions.\nClick OK when finished.");

    if (selectionType() == -1) { 
        print("No selection made. Skipping manual cleaning for " + channelColor + ".");
        return "NO";
    }
    run("Create Mask");
    rename("ManualMask_" + channelColor);
    print("Mask created for " + channelColor);
    run("Select None"); 
    return "YES"; 
}

// MANUAL CLEANING ON GREEN (CD8) AND RED (CD163) CHANNELS
manualAppliedRed  = manualChannelCleaning("red", outputCH1name1);
manualAppliedGreen = manualChannelCleaning("green", outputCH3name1);

selectImage("Composite_to_Clean"); 
bitDepth1 = bitDepth();
if (bitDepth1 == 24) { run("Split Channels"); }

// Rebuild final RGB image
titles = getList("image.titles");
redUpdated = ""; greenUpdated = ""; blueTitle = "";
for (i = 0; i < titles.length; i++) {
    if (endsWith(titles[i], "(red)"))   redUpdated = titles[i];
    if (endsWith(titles[i], "(green)")) greenUpdated = titles[i];
    if (endsWith(titles[i], "(blue)"))  blueTitle = titles[i];
}

if (manualAppliedRed == "YES") {
    selectImage("ManualMask_red");
    run("Invert");
    run("Divide...", "value=255.000");
    imageCalculator("Multiply create", redUpdated, "ManualMask_red");
    close(redUpdated); 
    close("ManualMask_red");
    selectImage("Result of " + redUpdated);
    rename("red_Updated"); 
    redUpdated = "red_Updated";
}

if (manualAppliedGreen == "YES") {
    selectImage("ManualMask_green");
    run("Invert");
    run("Divide...", "value=255.000");
    imageCalculator("Multiply create", greenUpdated, "ManualMask_green");
    close(greenUpdated);
    close("ManualMask_green");
    selectImage("Result of " + greenUpdated);
    rename("green_Updated"); 
    greenUpdated = "green_Updated";
}

run("Merge Channels...", "c1=[" + redUpdated + "] c2=[" + greenUpdated + "] c3=[" + blueTitle + "] create");
run("Stack to RGB");
close(redUpdated); close(greenUpdated); close(blueTitle);

// FINAL DIALOG AND LOG
Dialog.create("Processing!");
Dialog.addMessage("Please, click here OK but then wait 2 minutes for automatic saving!");
Dialog.show();
saveAs("PNG", dir2Subfolder + name1WithoutExtension + outputROIname1 + "_Composite.png");

logText = "";
logText += "Image: " + name1 + "\n\n";
logText += "ROI Name: " + outputROIname1 + "\n\n";
logText += "Output Directory: " + dir2Subfolder + "\n\n";
logText += "CH1 (" + outputCH1name1 + " - Red):\n Threshold: " + algoCH1 + "\n Median: " + medianAppliedCH1 + "\n Radius: " + medianRadiusCH1 + "\n\n";
logText += "CH2 (" + outputCH2name1 + " - Blue):\n Threshold: " + algoCH2 + "\n Median: " + medianAppliedCH2 + "\n Radius: " + medianRadiusCH2 + "\n\n";
logText += "CH3 (" + outputCH3name1 + " - Green):\n Threshold: " + algoCH3 + "\n Median: " + medianAppliedCH3 + "\n Radius: " + medianRadiusCH3 + "\n\n";

File.saveString(logText, logFile);
print("Processing log saved.");

// CLEANUP INTERMEDIATE FILES
// Deletes everything except _DEF.tif, _Composite.png, and log files.
print("Cleaning up intermediate files...");
fileList = getFileList(dir2Subfolder);
for (i = 0; i < fileList.length; i++) {
    filename = fileList[i];
    
    // Check if the file is one of the ones we want to KEEP
    isDef = endsWith(filename, "_DEF.tif");
    isComposite = endsWith(filename, "_Composite.png");
    isLog = endsWith(filename, "_processing_log.txt");

    // If it's NOT one of the keepers, delete it
    if (!isDef && !isComposite && !isLog) {
        ok = File.delete(dir2Subfolder + filename);
    }
}

print("Intermediate files deleted.");
print("Now you can close!");
run("Close All");