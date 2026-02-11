# HNSCC Immune Cell Analysis Pipeline


##  Project Overview

This repository contains the source code and scripts developed for the Bachelor's Thesis: **"Analysis of macrophages and lymphocytes in Head and Neck Squamous Cell Carcinoma histological images using open-source software QuPath"**.

The project aims to automate and standardize the quantitative analysis of immune cells (specifically **CD3+ T-cells**, **CD20+ B-cells**, and **CD163+ Macrophages**) in Whole Slide Images (WSI). The pipeline integrates **Fiji (ImageJ)** for preprocessing and artifact removal, and **QuPath** for advanced tiling, segmentation, and spatial analysis.

###  Academic Context
* **Author:** Alessandro Ambrogiani
* **Institution:** University of Bologna (DISI) - Campus of Cesena
* **Supervisors:** Prof. Antonella Carbonaro, Prof. Filippo Piccinini
* **Co-Supervisors:** Dr. Maria Maddalena Tumedei, Dr. Rebecca Polidori, Dr. Marcella Tazzari

---

##  Workflow & Methodology

The analysis pipeline is divided into three main computational stages:

### 1. Preprocessing & Artifact Cleaning (Fiji/ImageJ)
**File:** `MACRO_2_CD20CD3.ijm`

Before quantitative analysis, images undergo a supervised cleaning process to remove tissue folding, debris, and staining artifacts.
* **Deconvolution:** Splits the RGB image into separate fluorescence channels.
* **Iterative Thresholding:** Allows the user to interactively test and select the best threshold algorithm (Otsu, Yen, Moments) and median filters for each channel.
* **Manual Masking:** Enables the user to draw exclusion masks on specific channels to remove artifacts mathematically (Image Algebra).
* **Logging:** Automatically generates a text log recording all parameters for reproducibility.

### 2. Tiling & Pixel Classification (QuPath)
**File:** `01_Tiling_and_Classification.groovy`

To handle large WSI data and standardizing the analysis:
* **Pixel Size Locking:** Enforces a calibration of 0.2526 µm/px.
* **Tiling:** Subdivides ROIs into discrete units of **510 x 510 µm**.
* **Pixel Classification:** Applies trained classifiers to identify positive areas for CD3 and CD20.

### 3. Adaptive Hexagonal Segmentation (QuPath)
**File:** `02_HexGridFromBlobs.groovy`

Solves the issue of under-segmentation in high-density clusters using a custom algorithm:
* **Modal Diameter Calculation:** statistically estimates the typical cell size for each marker.
* **Conditional Splitting:** Leaves single cells (< 300 µm²) intact.
* **Hexagonal Tiling:** Replaces large confluent clusters with a hexagonal grid optimized for biological packing.
* **Boolean Clipping:** Adapts the grid to the specific morphology of the biological object.

### 4. Morphological Fusion (QuPath)
**File:** `03_HexFusionByAdjacency.groovy`

A post-processing step to fix fragmentation artifacts caused by the tiling process:
* **Spatial Hashing:** Uses a grid index to optimize performance ($O(N)$ complexity).
* **Adjacency Logic:** Identifies small fragments (< 10 µm²) adjacent to large cells.
* **CSG Union:** Merges fragments into the main cell body using Constructive Solid Geometry operations.

---

##  Usage

### Prerequisites
* **Fiji (ImageJ):** [Download here](https://fiji.sc/)
* **QuPath (v0.5.0 or later):** [Download here](https://qupath.github.io/)

### How to Run

#### Step 1: Preprocessing in Fiji
1.  Open Fiji.
2.  Drag & Drop `MACRO_2_CD20CD3.ijm`.
3.  Click `Run`.
4.  Follow the dialog prompts to select the input image, define the ROI name, and perform manual cleaning.

#### Step 2: Analysis in QuPath
1.  Create a new QuPath Project and import the processed images (Composite PNGs).
2.  Open the **Script Editor** (`Automate > Show Script Editor`).
3.  Run the scripts in the following order:
    * `01_Tiling_and_Classification.groovy` (Generates tiles and initial annotations).
    * `02_HexGridFromBlobs.groovy` (Performs the adaptive segmentation).
    * `03_HexFusionByAdjacency.groovy` (Cleans up fragments).
