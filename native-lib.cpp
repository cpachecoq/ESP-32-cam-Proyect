#include <jni.h>
#include <opencv2/opencv.hpp>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/features2d.hpp>

extern "C"
JNIEXPORT void JNICALL
Java_ec_edu_ups_prueba2_MainActivity_proccesMethod(
        JNIEnv *env, jobject /* this */, jlong addrInput, jlong addrOutput) {
    cv::Mat &input = *(cv::Mat *) addrInput;
    cv::Mat &output = *(cv::Mat *) addrOutput;

    // Reducción de colores mediante cuantización
    cv::Mat samples = input.reshape(1, input.rows * input.cols);
    samples.convertTo(samples, CV_32F);
    int clusterCount = 8;
    cv::Mat labels;
    cv::Mat centers;
    cv::kmeans(samples, clusterCount, labels,
               cv::TermCriteria(cv::TermCriteria::EPS + cv::TermCriteria::MAX_ITER, 10, 1.0),
               3, cv::KMEANS_PP_CENTERS, centers);
    centers.convertTo(centers, CV_8U);
    samples.convertTo(samples, CV_8U);
    cv::Mat reduced(input.size(), input.type());
    for (int y = 0; y < input.rows; y++) {
        for (int x = 0; x < input.cols; x++) {
            int cluster_idx = labels.at<int>(y * input.cols + x);
            reduced.at<cv::Vec3b>(y, x) = centers.at<cv::Vec3b>(cluster_idx);
        }
    }

    // Convertir a escala de grises
    cv::Mat gray;
    cv::cvtColor(samples, gray, cv::COLOR_BGR2GRAY);

    // Equializacion de histograma
    cv:: Mat equal;
    cv::equalizeHist(gray, equal);

    // Detección de bordes 
    cv::Mat edges;
    cv::Canny(equal, edges, 100, 200);

    // Inviertir la imagen
    cv::Mat inverted;
    cv::bitwise_not(edges, inverted);

    // Aplicar desenfoque gaussiano
    cv::Mat blurred;
    cv::GaussianBlur(inverted, blurred, cv::Size(21, 21), 0);

    // Inviertir la imagen desenfocada
    cv::Mat invertedBlurred;
    cv::bitwise_not(blurred, invertedBlurred);

    // Combina las imágenes para obtener el efecto de dibujo a lápiz con sombreado
    cv::divide(gray, invertedBlurred, output, 256.0);


}
