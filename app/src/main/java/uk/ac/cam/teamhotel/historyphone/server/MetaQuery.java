package uk.ac.cam.teamhotel.historyphone.server;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.cam.teamhotel.historyphone.artifact.Artifact;

/**
 * This class has the ability to request images and metadata from the server based on the object uuid
 * getImage() returns the correct bitmap
 * getArtifact() actually builds and returns an artifact object
 */
public class MetaQuery {
    private static final String TAG = "MetaQuery";

    public static Bitmap getImage(long uuid) {
        Bitmap result = null;
        Log.d(TAG, "getImage: Try to get image!");
        try {
            //create url string
            String urlString = "http://10.0.2.2:12345/img/" + String.valueOf(uuid) + ".png";

            //create connection
            URL url = new URL(urlString);//TODO: Insert the appropriate server url
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            //create the input stream from http connection
            BufferedInputStream is = new BufferedInputStream(connection.getInputStream(), 8192);
            //create byte output stream and populate it from the input stream
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte bytes[] = new byte[8192];
            int count;
            while ((count = is.read(bytes)) != -1) {
                out.write(bytes, 0, count);
            }
            //use the byte output stream to create a bitmap
            result = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
            Log.d(TAG, "getImage: Got the image!");

        } catch (MalformedURLException m) {
            System.err.print("There was a problem with generating the URL");
        } catch (IOException e) {
            System.err.print("There has been an I/O error");
            e.printStackTrace();
        }
        return result;
    }

    public static Artifact getArtifact(long uuid) {
        Artifact result = null;
        //TODO: Change URL for when the server is not running on localhost
        String url = "http://10.0.2.2:12345/api/info?id=" + String.valueOf(uuid);
        try {
            //read JSON from server
            JSONObject jsonObject = readJsonFromUrl(url);

            //extract data from JSON
            String name = jsonObject.getString("name");
            String description = jsonObject.getString("description");
            Bitmap image = getImage(uuid);

            //create new artifact
            result = new Artifact(name, description, image, uuid);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (JSONException j) {
            j.printStackTrace();
            return null;
        }
        return result;
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        URLConnection connection = new URL(url).openConnection();
        //set timeout so the app doesn't stall
        connection.setConnectTimeout(500);
        InputStream is = connection.getInputStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

            //Build JSON string from input stream
            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            String jsonText = sb.toString();

            //Build JSON Object from string
            JSONObject json = new JSONObject(jsonText);
            return json;
        } finally {
            is.close();
        }
    }
}
