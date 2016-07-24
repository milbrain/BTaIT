package de.tu_darmstadt.dik.btait;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.content.Intent;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Genera info:
 *
 * When communicating with the server, the server expects an id. The id is something like an opcode to
 * the server. Currently the server knows two id. id=42 means a GET-Request is coming. id=1337 however
 * suggests a POST-request containing a query to be run on the server.
 *
 * The QR-Code must not contain semicolons except for the purpose of separation of values!
 */
public class MainActivity extends AppCompatActivity {


    // ---- Variable input ----
    // Url of the server to connect to. RPi has static ip of 192.168.1.100
    private String url = "http://192.168.2.113/index.php";
    // ---- Variable input over ----

    // Permission request codes used for the permission system in Android 6.0+
    private final int permissionInternetCode = 1;

    // Global design elements
    private TextView outputLog;
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY);

    // Global variables
    /**
     * Represents the parts of the insert query values.
     * Index    Meaning
     *
     *  0       Bezeichnung
     *  1       Lieferant
     *  2       Werkstoff
     *  3       Variante
     *  4       Kosten_pro_Meter
     *  5       Menge
     *  6       Gewicht
     *  7       Lieferdatum
     *  8       Position
     *  9       Auftragsnummer
     */
    String[] queryContent;

    /**
     * Decides what to do with a material if we scan it. If insertMode is true we will add the material
     * to the queryContent (and if queryContent is full do the query). In the other case
     * we will look for a material with the same Auftragsnummer as the scanned object and delete it from
     * the database.
     */
    boolean insertMode = true;


    // Global variable test area (this should be empty if this is a release -------
    // Test area over -------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        outputLog = (TextView) findViewById(R.id.outputLog);
        log("Application started");

        queryContent = new String[10];
        for (String c: queryContent) {
            c = "";
        }
    }

    /**
     * Called when a QR-Code was scanned and recognized. Saves the contained information in a global array. If the
     * information needed is all set, it starts a POST request to transmit the query to the server.
     *
     * The QR-Code contains the values for the query as Strings, separated by Semicolons. Typecast and parsing
     * will be done by the server later.
     *
     * QR-Code must not contain semicolons except for the purpose of separation of values! Possible status-codes
     * (which have to be put as first value of the QR-Code):
     *
     * DIK_INS_Mat:         Scan all values for insertQuery (Material and Position). Mainly for test purposes.
     *
     * Amount of parameters:                    11
     * Valid example of query values:           'QR-Stange', 1, 'QR-Stoff', 'DEF', 14.88, 17.00, 200, '2016-07-03', 1, 654321
     * Types of the query values:                    Str    Num     Str      Str    dec    dec   num      date     num  num
     * Valid example of a QR-Code:              strings/qr_mat_and_pos_code;QR-Stange;1;QR-Stoff;DEF;14.88;17.00;200;2016-07-03;1;654321
     *
     *
     * DIK_INS_Mat_Mat:     Scan Material
     *
     * Amount of parameters:                    10
     * Valid example of a QR-Code:              strings/qr_material_code;QR-Stange;1;QR-Stoff;DEF;14.88;17.00;200;2016-07-03;654321
     *
     *
     * DIK_INS_Mat_Pos:      Scan Position
     *
     * Amount of parameters:                    2
     * Valid example of a QR-Code:              strings/qr_position_code;2
     *
     * @param content Textual representation of the QR-Code
     */
    private void handleQRCodeInput(String content) {
        log("QR-Code wurde gescannt");

        String[] queryContents = content.split(";");

        if (queryContents.length == 10 && queryContents[0].equals(getString(R.string.qr_material_code))) {

            // We recognized a material, check if we should save or delete the material
            if (insertMode) {
                log("Material erkannt, speichere Materialinfos.");

                // Save parameters
                for (int i = 0; i < 8; ++i)
                    queryContent[i] = queryContents[i + 1];
                queryContent[9] = queryContents[9];
            } else {
                log("Material erkannt, lösche Material aus Datenbank");
                queryContent[9] = queryContents[9];
            }

        } else if (insertMode && queryContents.length == 2 && queryContents[0].equals(getString(R.string.qr_position_code))) {

            // We recognized a position
            log("Position erkannt, speichere Positionsinfos.");
            queryContent[8] = queryContents[1];

        } else if (insertMode && queryContents.length == 11 && queryContents[0].equals(getString(R.string.qr_mat_and_pos_code))) {

            // We recognized all data at once (probably this is a test Case)
            log("Test QR-Code erkannt, speichere alle nötigen Infos.");
            for (int i = 0; i < 10; ++i)
                queryContent[i] = queryContents[i+1];
        } else {
            log("Es wurde nichts unternommen. Ungültiger QR-Code?");
        }

        String query = "";

        // Check if insert query is complete
        boolean doInsertQuery = true;
        for (String c: queryContent)
            if (c == null || c.equals(""))
                doInsertQuery = false;

        if (doInsertQuery) {
            String insertQuery = "INSERT INTO Material (Bezeichnung, Lieferant, Werkstoff, Variante, Kosten_pro_Meter, Menge, Gewicht, Lieferdatum, Position, Auftragsnummer) VALUES (";
            insertQuery += "'" + queryContent[0] + "', " + queryContent[1] + ", '" + queryContent[2] + "', '" + queryContent[3] + "', " + queryContent[4] + ", " + queryContent[5] + ", " + queryContent[6] + ", '" + queryContent[7] + "', " + queryContent[8] + ", " + queryContent[9] + ");";

            log("Alle nötigen Infos gesammelt. Führe insert query aus und lösche Zwischenspeicher.");
            for (int i = 0; i < queryContent.length; ++i)
                queryContent[i] = "";
            query = insertQuery;
        }

        // Check if delete query is complete
        boolean doDeleteQuery = true;
        if (insertMode || queryContent[9] == null || queryContent[9].equals("")) {
            doDeleteQuery = false;
        } else {
            doDeleteQuery = true;
            query = "DELETE FROM Material WHERE Auftragsnummer = '" + queryContent[9] + "';";
            log("Lösche Material aus Datenbank");
            queryContent[9] = "";
        }

        // If either of the query flags is true
        if (doDeleteQuery || doInsertQuery) {

            // Encode query as URL
            String encodedQuery = "";
            try {
                encodedQuery = URLEncoder.encode(query, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                log("Encoding der URL für POST request gescheitert");
            }
            encodedQuery = "query=" + encodedQuery;

            // Do POST request
            doPostRequest("id=1337&" + encodedQuery);
        }
    }

    /**
     * Triggered by UI Button. Initiates the barcode scanning for insertion.
     */
    public void scanBarcodeInsert(View view) {
        insertMode = true;
        IntentIntegrator scanIntegrator = new IntentIntegrator(this);
        scanIntegrator.initiateScan();
    }

    /**
     * Triggered by UI Button. Initiates the barcode scanning for removal (of material).
     */
    public void scanBarcodeRemove(View view) {
        insertMode = false;
        IntentIntegrator scanIntegrator = new IntentIntegrator(this);
        scanIntegrator.initiateScan();
    }

    /**
     * Triggered by an UI button. Does a get request.
     */
    public void testGetRequest(View view) {
        doGetRequest("id=42");
    }

    /**
     * This method creates a DownloadWebpageTask to do a GET request.
     * @param getParams The parameters of the GET request as a single String. params has to be encoded
     *                  as an URL.
     */
    private void doGetRequest(String getParams) {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            try {
                new DownloadWebpageTask().execute(this.url, getParams);
            } catch (Exception e) {
                log("Downloading content went wrong. Please try again.");
            }
        } else {
            log("No network connection available");
        }
    }

    /**
     * Triggered by an UI button. Does a post request.
     */
    public void testPostRequest(View view) {
        String insertQuery = "INSERT INTO Material (Bezeichnung, Lieferant, Werkstoff, Variante, Kosten_pro_Meter, Menge, Gewicht, Lieferdatum, Position, Auftragsnummer) VALUES ('Teststange', 1, 'Teststoff', 'ABC', 13.77, 16.00, 100, '2016-06-17', 2, 123456);";
        String encodedQuery = "";
        try {
            encodedQuery = URLEncoder.encode(insertQuery, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log("Encoding of URL for POST request failed");
        }
        encodedQuery = "query=" + encodedQuery;
        doPostRequest("id=1337&" + encodedQuery);
    }

    /**
     * This method creates a PostToWebpageTask to do a POST request.
     * @param params The parameters of the POST request as a single String. params has to be encoded
     *               as an URL.
     */
    private void doPostRequest(String params) {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            try {
                new PostToWebpageTask().execute(this.url, params);
            } catch (Exception e) {
                log("Updating content went wrong. Please try again.");
            }
        } else {
            log("No network connection available");
        }
    }

    /**
     * Output msg into the app log console together with a time.
     * @param msg The message to be printed.
     */
    private void log(String msg) {
        outputLog.append(sdf.format(new Date()) + " " + msg + "\n");
    }

    /**
     * Uses AsyncTask to create a task away from the main UI thread. This task takes
     * two arguments. The first is the URL to connect to. The second is a String
     * containing the POST arguments in encoded URL format.
     */
    private class PostToWebpageTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {
            // params comes from the execute() call: params[0] is the url.
            try {
                return postRequest(params[0], params[1]);
            } catch (IOException e) {
                return "Unable to do POST Request. URL or Parameters may be invalid.";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            log("POST Request successful. Answer was: \n\n" + result + "\n");
        }

        @Override
        protected void onProgressUpdate(String... msg) {
            if (msg.length > 0)
                outputLog.append(msg[0]);
        }

        private String postRequest(String url, String urlParameters) throws IOException {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // Add request header
            con.setRequestMethod("POST");

            String updateMsg = sdf.format(new Date()) + " Trying POST Request:\n";

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();

            updateMsg += sdf.format(new Date()) + " The response code is: " + responseCode + "\n";
            publishProgress(updateMsg);

            InputStream is = con.getInputStream();
            int len = 1000;
            String contentAsString;

            // Convert the InputStream into a string
            try {
                contentAsString = readIt(is, len);
            } finally {
                if (is != null) {
                    is.close();
                }
            }
            return contentAsString;
        }

        // Reads an InputStream and converts it to a String.
        public String readIt(InputStream stream, int len) throws IOException {
            Reader reader;
            reader = new InputStreamReader(stream, "UTF-8");
            char[] buffer = new char[len];
            reader.read(buffer);
            return new String(buffer);
        }
    }


    /**
     * Uses AsyncTask to create a task away from the main UI thread. This task takes a
     * URL string and uses it to create an HttpUrlConnection. Once the connection
     * has been established, the AsyncTask downloads the contents of the webpage as
     * an InputStream. Finally, the InputStream is converted into a string, which is
     * displayed in the UI by the AsyncTask's onPostExecute method.
     *
     * When creating DownloadWebpageTask needs two arguments. The first has to be the
     * url to connect to. The second has to contain the arguments for the get request.
     */
    private class DownloadWebpageTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... urls) {

            // params comes from the execute() call: params[0] is the url.
            try {
                return downloadUrl(urls[0], urls[1]);
            } catch (IOException e) {
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            log("The message is: \n\n" + result + "\n");
        }

        @Override
        protected void onProgressUpdate(String... msg) {
            if (msg.length > 0)
                outputLog.append(msg[0]);
        }

        // Given a URL, establishes an HttpUrlConnection and retrieves
        // the web page content as a InputStream, which it returns as
        // a string.
        private String downloadUrl(String myurl, String getParams) throws IOException {
            InputStream is = null;
            // Only display the first 500 characters of the retrieved
            // web page content.
            int len = 1000;

            try {
                // Add get parameters here, separated by semicolon
                String fullUrl = myurl + "?" + getParams;
                String updateMsg = sdf.format(new Date()) + " Trying to reach " + fullUrl + "\n";

                URL url = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                // Starts the query
                conn.connect();
                int response = conn.getResponseCode();
                updateMsg += sdf.format(new Date()) + " The response code is: " + response + "\n";
                publishProgress(updateMsg);
                is = conn.getInputStream();

                // Convert the InputStream into a string
                return readIt(is, len);

                // Makes sure that the InputStream is closed after the app is
                // finished using it.
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        // Reads an InputStream and converts it to a String.
        public String readIt(InputStream stream, int len) throws IOException {
            Reader reader;
            reader = new InputStreamReader(stream, "UTF-8");
            char[] buffer = new char[len];
            reader.read(buffer);
            return new String(buffer);
        }
    }

    /**
     * Requests the internet permission. When first called, call with called = false.
     * This parameter is for inner use.
     *
     * @param called Set to false, when calling method
     */
    private void requestInternet(boolean called) {
        outputLog.append(sdf.format(new Date()) + " requestInternet called" + "\n");
        if (!called) {
            String internetPermission = android.Manifest.permission.INTERNET;
            if (ContextCompat.checkSelfPermission(this, internetPermission) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{internetPermission}, permissionInternetCode);
            }
        }
    }


    /**
     * Method needed for requesting permissions starting from Android 6.0. The structure of the method is
     * basically predefined by Android.
     *
     * @param requestCode  When asking for a permission you have to use a requestCode. The same code is then
     *                     given to onRequestPermissionsResult to be able to distinguish the permissions that
     *                     were asked for.
     * @param permissions  Contains the permissions that were asked for
     * @param grantResults Contains the result of the permission application process
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case permissionInternetCode: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    log("Internet permission got granted");
                    requestInternet(true);
                } else {
                    log("Internet permission is beeing applied for again");
                    requestInternet(false);
                }
            }
            default:
                log("This shouldn't have happened. The developer is speechless.");
        }
    }

    /**
     * Method that the data from barcode scanning is beeing returned to.
     * @param requestCode contains the request code used when the activity was triggered
     * @param resultCode parsed by ZXing's IntentIntegrator to get the QR-Code content
     * @param intent Intent used to transfer the data.
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // Check for barcodes with ZXing
        IntentResult scanningResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanningResult != null && scanningResult.getContents() != null && scanningResult.getFormatName() != null) {
            // We have a result
            String scanContent = scanningResult.getContents();
            String scanFormat = scanningResult.getFormatName();

            if (scanFormat.equals("QR_CODE")) {
                handleQRCodeInput(scanContent);
            } else {
                log("Nicht unterstütztes Format: " + scanFormat);
            }

        } else{
            log("Es konnte nichts gescannt werden.");
        }
    }
}

