package ec.edu.ups.prueba2;



import static android.content.ContentValues.TAG;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.longdo.mjpegviewer.MjpegView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ImageView processedImageView;
    private MjpegView mjpegView;
    private Handler handler;
    private String STREAM_URL = "http://192.168.1.100:81/stream"; // URL del stream
    private boolean isProcessing = true;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed");
        } else {
            Log.d("OpenCV", "OpenCV initialization succeeded");
        }
        System.loadLibrary("prueba2"); // Cargar la biblioteca nativa
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar vistas
        webView = findViewById(R.id.webview);

        // Configurar WebView para mostrar el stream
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        processedImageView = findViewById(R.id.processedImageView);
        new Thread(this::captureAndProcessFrames).start();

    }

    // Declarar el método nativo
    public native void proccesMethod(long matAddi, long matArddo);

    private void captureAndProcessFrames() {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try {
            URL url = new URL(STREAM_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setReadTimeout(5000);
            connection.setConnectTimeout(5000);
            connection.connect();

            inputStream = connection.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            boolean capturing = false;

            while (isProcessing && (bytesRead = inputStream.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    if (!capturing) {
                        // Verificar si hay suficientes bytes para comparar
                        if (i < bytesRead - 1 && buffer[i] == (byte) 0xFF && buffer[i + 1] == (byte) 0xD8) {
                            capturing = true;
                            byteArrayOutputStream.write(buffer[i]);
                        }
                    } else {
                        byteArrayOutputStream.write(buffer[i]);
                        // Verificar si hay suficientes bytes para comparar
                        if (i > 0 && buffer[i - 1] == (byte) 0xFF && buffer[i] == (byte) 0xD9) {
                            byte[] jpegData = byteArrayOutputStream.toByteArray();
                            byteArrayOutputStream.reset();
                            capturing = false;
                            processFrame(jpegData);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al capturar y procesar frames: " + e.getMessage());
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error al cerrar la conexión: " + e.getMessage());
            }
        }
    }

    private void processFrame(byte[] jpegData) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
        if (bitmap == null) {
            Log.e(TAG, "No se pudo decodificar el frame.");
            return;
        }

        Mat inputMat = new Mat();
        Utils.bitmapToMat(bitmap, inputMat);

        Mat outputMat = new Mat(inputMat.size(), inputMat.type());

        // Llamar al método nativo para procesar la imagen
        proccesMethod(inputMat.getNativeObjAddr(), outputMat.getNativeObjAddr());

        // Convertir el resultado a Bitmap
        Bitmap resultBitmap = Bitmap.createBitmap(outputMat.cols(), outputMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outputMat, resultBitmap);

        runOnUiThread(() -> processedImageView.setImageBitmap(resultBitmap));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isProcessing = false;
    }

}
