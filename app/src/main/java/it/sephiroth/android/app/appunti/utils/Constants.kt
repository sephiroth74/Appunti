package it.sephiroth.android.app.appunti.utils

object Constants {

    const val KEY_ID = "key_id"
    const val KEY_AUTOPLAY = "key_autoplay"


    /**
     * Contains all the REQUEST CODES used in any Activity
     */
    object ActivityRequestCodes {
        // speech recognition intent request code
        const val AUDIO_CAPTURE_REQUEST_CODE = 101

        // pick new category
        const val CATEGORY_PICK_REQUEST_CODE = 103

        // pick file
        const val OPEN_FILE_REQUEST_CODE = 104

        // take picture
        const val IMAGE_CAPTURE_REQUEST_CODE = 105

        // Request Code for Voice Recognition Permissions
        const val REQUEST_RECORD_AUDIO_PERMISSION_CODE = 1002

        // permission request - codes
        const val REQUEST_LOCATION_PERMISSION_CODE = 1001
    }
}