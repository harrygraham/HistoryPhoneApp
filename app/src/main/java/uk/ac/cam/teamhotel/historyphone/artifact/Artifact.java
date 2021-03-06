package uk.ac.cam.teamhotel.historyphone.artifact;

import android.graphics.Bitmap;
import android.util.Log;

public class Artifact {

    private static final String TAG = "Artifact";

    /**
     * Instantiate a new placeholder artifact with a particular uuid. Placeholder
     * artifacts the uuid of an actual artifact, and therefore time out with them,
     * but may be used to display loading tiles where the artifact data has not
     * been retrieved from the server.
     *
     * @return a newly instantiated placeholder artifact.
     */
    public static Artifact newPlaceholder(long uuid) {
        return new Artifact(-uuid - 1, null, null, null);
    }

    private long uuid;
    private String name;
    private String description;
    private Bitmap picture;

    public Artifact(long uuid, String name, String description, Bitmap picture) {
        this.uuid = uuid;
        this.name = name;
        this.description = description;
        this.picture = picture;
    }

    public long getUUID() {
        return (isPlaceholder()) ? -uuid - 1 : uuid;
    }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Bitmap getPicture() { return picture; }
    public boolean isPlaceholder() { return uuid < 0; }

    @Override
    public String toString() {
        return "Artifact(" + uuid + ", " + name + ")";
    }
}
