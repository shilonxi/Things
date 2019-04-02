package pr.platerecognization;

public class PlateRecognition
{
    static {
        System.loadLibrary("hyperlpr");
    }
    static public native long InitPlateRecognizer(String casacde_detection,
                                           String finemapping_prototxt,String finemapping_caffemodel,
                                           String segmentation_prototxt,String segmentation_caffemodel,
                                           String charRecognization_proto,String charRecognization_caffemodel);
    static public native void ReleasePlateRecognizer(long  object);
    static public native String SimpleRecognization(long  inputMat,long object);
}
