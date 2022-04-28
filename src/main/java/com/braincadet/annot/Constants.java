package com.braincadet.annot;

import java.awt.*;

public class Constants {

    /** The name of the directory used to store the annotations. */
    public static final String ANNOTATION_OUTPUT_DIR_NAME = "annot";

    /** Inside there will be three subdirs: train, validate, test where the optional sampling masks are saved */
    public static final String ML_REGION_OUTPUT_DIR_NAME = "ml_region";

    /** */
    public static final String ANNOTATION_REGION_POS = "annot_pos";

    /** */
    public static final String ANNOTATION_REGION_NEG = "annot_neg";

    public static final String TRAIN_DIR_NAME = "train";

    public static final String TEST_DIR_NAME = "test";

    public static final String VALIDATE_DIR_NAME = "validate";

    /** Keyboard command to export the existing annotation/ml_region */
    public static final char EXPORT_COMMAND = 'e';

    /** Keyboard command to delete underlying element */
    public static final char DELETE_COMMAND = 'd';

    /** Keyboard command to start with periodic clicking while moving the mouse (drawing mode) */
    public static final char PERIODIC_CLICK = 'p';

    /** Keyboard command to increase the size of the circular element */
    public static final char INCREASE_CIRCLE_RADIUS = 'u';

    /** Keyboard command to decrease the size of the circular element */
    public static final char DECREASE_CIRCLE_RADIUS = 'j';

    /** Switch currently annotated region type */
    public static final char CHANGE_ML_REGION = 'r';

    /** Value used to increase/decrease current annotation circle radius */
    public static final int  R_STEP = 2;

    /** */
    public static final int R_MIN = 2;

    /**  */
    public static final Color COLOR_IGNORE = Color.CYAN;

    /**  */
    public static final Color COLOR_ANNOT = new Color(1f, 1f, 0f, .4f); // semi-transparent yellow

    /**  */
    public static final Color COLOR_ML_REGION_TRAIN = new Color(1f, 0f, 0f, .5f); // region used to sample for train set

    /**  */
    public static final Color COLOR_ML_REGION_VALID = new Color(0f, 1f, 0f, .5f); // region used to sample for validation set

    /**  */
    public static final Color COLOR_ML_REGION_TEST = new Color(0f, 0f, 1f, .5f); // region used to sample for test set

    /** */
    public static final Color COLOR_POSITIVE = new Color(1f, 0f, 0f, .5f);

    /** */
    public static final Color COLOR_NEGATIVE = new Color(0f, 0f, 1f, .5f);

    /**  */
    public static final float ANNOTATOR_OUTLINE_WIDTH = 1f;


    public static float modifyCircleRadius(char command, float radius, float radiusMax, float radiusMin) {

        if (command == Constants.INCREASE_CIRCLE_RADIUS) {
            return radius + ((radius < radiusMax)? Constants.R_STEP : 0);
        }

        if (command == Constants.DECREASE_CIRCLE_RADIUS) {
            return radius - ((radius >= radiusMin)? Constants.R_STEP : 0);
        }

        return radius; // stays the same for non-existent command


    }

    public static boolean isValidImageExtension(String imageTitle) {
        return Tools.getFileExtension(imageTitle).equalsIgnoreCase("TIF") ||
                Tools.getFileExtension(imageTitle).equalsIgnoreCase("JPG");
    }
}