import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvLoadImage;

public class Main {
    public static void main(String args[]){
        try {
            ReconoFac reconocer = ReconoFac.getInstance();

            //Entrenamiento
            /*IplImage[] trainImages = new IplImage[10];
            for(int i=1; i<=10; i++){
                trainImages[i-1]=cvLoadImage("terry"+i+".jpg");
                CvSeq faces = reconocer.detectFace(trainImages[i-1]);
                CvRect r = new CvRect(cvGetSeqElem(faces,0));
                trainImages[i-1]=reconocer.preprocessImage(trainImages[i-1], r);
            }
            reconocer.learnNewFace("John Terry", trainImages);*/

            //Reconocimiento
            IplImage target = new IplImage();
            target = cvLoadImage("cr7_target.jpg");
            CvSeq faces2 = reconocer.detectFace(target);
            CvRect r2 = new CvRect(cvGetSeqElem(faces2,0));
            target=reconocer.preprocessImage(target, r2);
            System.out.println("Persona Identificada: "+reconocer.identifyFace(target));
        } catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}