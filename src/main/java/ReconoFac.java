
import static org.bytedeco.javacpp.helper.opencv_objdetect.cvHaarDetectObjects;
import static org.bytedeco.javacpp.opencv_core.CV_32SC1;
import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvCreateImage;
import static org.bytedeco.javacpp.opencv_core.cvGetSize;
import static org.bytedeco.javacpp.opencv_core.cvLoad;
import static org.bytedeco.javacpp.opencv_core.cvSetImageROI;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvLoadImage;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvSaveImage;
import static org.bytedeco.javacpp.opencv_imgproc.CV_BGR2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.CV_INTER_LINEAR;
import static org.bytedeco.javacpp.opencv_imgproc.cvCvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.cvEqualizeHist;
import static org.bytedeco.javacpp.opencv_imgproc.cvResize;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.CvMat;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_face.FaceRecognizer;
import org.bytedeco.javacpp.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.javacpp.opencv_objdetect;
import org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade;

//LIBRERIAS JAVA
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;


/**
 *
 * @author jonathan
 */
public class ReconoFac {

    private static String faceDataFolder = "C:\\facerecognizer\\data\\";
    public static String imageDataFolder = faceDataFolder + "images\\";
    private static final String CASCADE_FILE = "C:\\opencv\\data\\haarcascades\\haarcascade_frontalface_alt.xml";
    private static final String BinaryFile = faceDataFolder + "frBinary.dat";
    public static final String personNameMappingFileName = faceDataFolder + "personNumberMap.properties";

    final CvHaarClassifierCascade cascade = new CvHaarClassifierCascade(cvLoad(CASCADE_FILE));
    private Properties dataMap = new Properties();
    private static ReconoFac instance = new ReconoFac();

    public static final int NUM_IMAGES_PER_PERSON =10;
    double binaryTreshold = 100;
    int highConfidenceLevel = 70;

    FaceRecognizer ptr_binary = null;
    private FaceRecognizer fr_binary = null;

    private ReconoFac() {
        createModels();
        loadTrainingData();
    }

    public static ReconoFac getInstance() {
        return instance;
    }

    private void createModels() {
        ptr_binary = LBPHFaceRecognizer.create(1, 8, 8, 8, binaryTreshold);
        fr_binary = ptr_binary;
    }

    protected CvSeq detectFace(IplImage originalImage) {
        CvSeq faces = null;
        Loader.load(opencv_objdetect.class);
        try {
            IplImage grayImage = IplImage.create(originalImage.width(), originalImage.height(), IPL_DEPTH_8U, 1);
            cvCvtColor(originalImage, grayImage, CV_BGR2GRAY);
            CvMemStorage storage = CvMemStorage.create();
            faces = cvHaarDetectObjects(grayImage, cascade, storage, 1.1, 1, 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return faces;
    }

    public String identifyFace(IplImage image) {
        String personName = "";
        Set keys = dataMap.keySet();

        if (keys.size() > 0) {
            int[] ids = new int[1];
            double[] distance = new double[1];
            int result = -1;

            fr_binary.predict(image, ids, distance);
            result = ids[0];

            if (result > -1 && distance[0]<highConfidenceLevel) {
                personName = (String) dataMap.get("" + result);
            }
        }

        return personName;
    }

    public boolean learnNewFace(String personName, IplImage[] images) throws Exception {
        int memberCounter = dataMap.size();
        if(dataMap.containsValue(personName)){
            Set keys = dataMap.keySet();
            Iterator ite = keys.iterator();
            while (ite.hasNext()) {
                String personKeyForTraining = (String) ite.next();
                String personNameForTraining = (String) dataMap.getProperty(personKeyForTraining);
                if(personNameForTraining.equals(personName)){
                    memberCounter = Integer.parseInt(personKeyForTraining);
                }
            }
        }
        dataMap.put("" + memberCounter, personName);
        storeTrainingImages(personName, images);
        retrainAll();

        return true;
    }


    public IplImage preprocessImage(IplImage image, CvRect r){
        IplImage gray = cvCreateImage(cvGetSize(image), IPL_DEPTH_8U, 1);
        IplImage roi = cvCreateImage(cvGetSize(image), IPL_DEPTH_8U, 1);
        CvRect r1 = new CvRect(r.x()-10, r.y()-10, r.width()+10, r.height()+10);
        cvCvtColor(image, gray, CV_BGR2GRAY);
        cvSetImageROI(gray, r1);
        cvResize(gray, roi, CV_INTER_LINEAR);
        cvEqualizeHist(roi, roi);
        return roi;
    }

    private void retrainAll() throws Exception {
        Set keys = dataMap.keySet();
        if (keys.size() > 0) {
            MatVector trainImages = new MatVector(keys.size() * NUM_IMAGES_PER_PERSON);
            CvMat trainLabels = CvMat.create(keys.size() * NUM_IMAGES_PER_PERSON, 1, CV_32SC1);
            Iterator ite = keys.iterator();
            int count = 0;

            System.err.print("Cargando imagenes para entrenamiento ...");
            while (ite.hasNext()) {
                String personKeyForTraining = (String) ite.next();
                String personNameForTraining = (String) dataMap.getProperty(personKeyForTraining);
                IplImage[] imagesForTraining = readImages(personNameForTraining);

                for (int i = 0; i < imagesForTraining.length; i++) {
                    trainLabels.put(count, 0, Integer.parseInt(personKeyForTraining));
                    IplImage grayImage = IplImage.create(imagesForTraining[i].width(), imagesForTraining[i].height(), IPL_DEPTH_8U, 1);
                    cvCvtColor(imagesForTraining[i], grayImage, CV_BGR2GRAY);
                    trainImages.put(count,grayImage);
                    count++;
                }
            }

            System.err.println("hecho.");

            System.err.print("Realizando entrenamiento ...");
            fr_binary.train(trainImages, trainLabels);
            System.err.println("hecho.");
            storeTrainingData();
        }

    }

    private void loadTrainingData() {
        try {
            File personNameMapFile = new File(personNameMappingFileName);
            if (personNameMapFile.exists()) {
                FileInputStream fis = new FileInputStream(personNameMappingFileName);
                dataMap.load(fis);
                fis.close();
            }

            File binaryDataFile = new File(BinaryFile);
            binaryDataFile.createNewFile();
            fr_binary.load(BinaryFile);
            System.err.println("hecho");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void storeTrainingData() throws Exception {
        System.err.print("Almacenando modelos ...");

        File binaryDataFile = new File(BinaryFile);
        if (binaryDataFile.exists()) {
            binaryDataFile.delete();
        }
        fr_binary.save(BinaryFile);

        File personNameMapFile = new File(personNameMappingFileName);
        if (personNameMapFile.exists()) {
            personNameMapFile.delete();
        }
        FileOutputStream fos = new FileOutputStream(personNameMapFile, false);
        dataMap.store(fos, "");
        fos.close();

        System.err.println("hecho.");
    }


    public void storeTrainingImages(String personName, IplImage[] images) {
        for (int i = 0; i < images.length; i++) {
            String imageFileName = imageDataFolder + "training\\" + personName + "_" + i + ".bmp";
            File imgFile = new File(imageFileName);
            if (imgFile.exists()) {
                imgFile.delete();
            }
            cvSaveImage(imageFileName, images[i]);
        }
    }

    private IplImage[] readImages(String personName) {
        File imgFolder = new File(imageDataFolder);
        IplImage[] images = null;
        if (imgFolder.isDirectory() && imgFolder.exists()) {
            images = new IplImage[NUM_IMAGES_PER_PERSON];
            for (int i = 0; i < NUM_IMAGES_PER_PERSON; i++) {
                String imageFileName = imageDataFolder + "training\\" + personName + "_" + i + ".bmp";
                IplImage img = cvLoadImage(imageFileName);
                images[i] = img;
            }

        }
        return images;
    }
}
