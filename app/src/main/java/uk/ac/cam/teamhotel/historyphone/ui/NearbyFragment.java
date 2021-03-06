package uk.ac.cam.teamhotel.historyphone.ui;

import java.util.concurrent.TimeUnit;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.support.v4.app.Fragment;

import io.reactivex.Observable;
import uk.ac.cam.teamhotel.historyphone.HistoryPhoneApplication;
import uk.ac.cam.teamhotel.historyphone.R;
import uk.ac.cam.teamhotel.historyphone.artifact.Artifact;
import uk.ac.cam.teamhotel.historyphone.artifact.ArtifactLoader;
import uk.ac.cam.teamhotel.historyphone.ble.BeaconScanner;
import uk.ac.cam.teamhotel.historyphone.ble.BluetoothNotSupportedException;
import uk.ac.cam.teamhotel.historyphone.utils.StreamTools;

import static android.app.Activity.RESULT_OK;

public class NearbyFragment extends Fragment {

    public static final String TAG = "NearbyFragment";

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;

    private static final float DISTANCE_ALPHA = 0.75f;

    private Observable<Pair<Artifact, Float>> entriesStream;

    /**
     * Construct the artifact entry pipeline from the raw beacon stream.
     */
    private void createPipeline() {
        // Stop scanning while the beacon pipeline is set up.
        stopScanning();

        // Get the artifact loader from the parent activity.
        ArtifactLoader artifactLoader =
                ((HistoryPhoneApplication) getActivity().getApplication()).getArtifactLoader();

        // Compose the pipeline.
        Log.i(TAG, "Constructing nearby beacon pipeline...");
        entriesStream = BeaconScanner.getInstance().getBeaconStream()
                // Load Artifact objects from beacon UUIDs.
                .compose(StreamTools.mapLeft(artifactLoader::load))
                // Group the pairs by their beacon UUID.
                .groupBy(pair -> pair.first.getUUID())
                // Perform group processing on artifacts with like UUIDs.
                .map(stream -> {
                    // Wrap in an array to get around Java's weird closure semantics.
                    final Artifact[] group = new Artifact[] { null };
                    final float[] distance = new float[] { 0.0f };
                    final boolean[] initialised = new boolean[] { false };
                    return stream
                            // Initialise the closure variables.
                            .doOnNext(pair -> {
                                if (!initialised[0]) {
                                    // Store the artifact UUID this stream is grouped by.
                                    group[0] = pair.first;
                                    // Store the initial distance for averaging.
                                    distance[0] = pair.second;
                                    initialised[0] = true;
                                }
                            })
                            // Exponentially average actual distances over multiple scans.
                            .map(pair -> {
                                distance[0] = (1 - DISTANCE_ALPHA) * pair.second +
                                        DISTANCE_ALPHA * distance[0];
                                return new Pair<>(pair.first, distance[0]);
                            })
                            // After a timeout without seeing this artifact, throw an error.
                            .timeout(4000, TimeUnit.MILLISECONDS)
                            // When an error is streamed, forward the group artifact at +inf metres.
                            .onErrorReturn(error -> {
                                Log.d(TAG, "Beacon " + group[0].getUUID() + " timed out.");
                                return new Pair<>(
                                        group[0],
                                        Float.POSITIVE_INFINITY
                                );
                            });
                })
                // Recombine the streams into one.
                .flatMap(stream -> stream);

        // Begin scanning for beacons.
        startScanning();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Set up the Bluetooth event receiver.
        getActivity().registerReceiver(bluetoothEventReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    public void onPause() {
        super.onPause();

        // Unregister the Bluetooth event receiver.
        getActivity().unregisterReceiver(bluetoothEventReceiver);
    }

    /**
     * Start scanning for beacons via the beacon scanner. If Bluetooth is
     * not currently enabled, prompt the user to enable it.
     */
    private void startScanning() {
        BeaconScanner scanner = BeaconScanner.getInstance();
        try {
            if (scanner.isBluetoothEnabled()) {
                // If Bluetooth is currently enabled, start scanning.
                scanner.start();
            } else {
                // Otherwise, prompt the user to enable Bluetooth.
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
            }
        } catch (BluetoothNotSupportedException e) {
            // TODO: Indicate to user that Bluetooth is not supported.
        }
    }

    /**
     * Stop scanning for beacons via the beacon scanner.
     */
    private void stopScanning() {
        BeaconScanner.getInstance().stop();
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        switch (request) {
            case REQUEST_ENABLE_BLUETOOTH:
                // This is the result of prompting the user to enable Bluetooth.
                // Treat the result as their preference on whether we should be scanning.
                if (result == RESULT_OK) {
                    startScanning();
                } else {
                    stopScanning();
                }
                break;
        }
    }

    /**
     * Broadcast receiver for Bluetooth events, in order to start/stop scanning.
     */
    private final BroadcastReceiver bluetoothEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    // The local Bluetooth adapter is on, and ready for use.
                    startScanning();
                    break;

                case BluetoothAdapter.STATE_OFF:
                case BluetoothAdapter.STATE_TURNING_OFF:
                    // The local Bluetooth adapter is turning off.
                    stopScanning();
                    break;
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate a new view with the nearby fragment layout.
        View rootView = inflater.inflate(R.layout.fragment_nearby, container, false);

        // Set up the click listener for the list view.
        final ListView listView = (ListView) rootView.findViewById(R.id.nearby_list);
        listView.setOnItemClickListener((parent, rowView, position, id) -> {
            // Pass artifact name and UUID to chat session.
            Pair<Artifact, Float> entry =
                    ((NearbyAdapter) listView.getAdapter()).getItem(position);

            // Null entries should not be possible.
            assert entry != null;
            if (entry.first == null) {
                Log.w(TAG, "Null artifact clicked.");
                return;
            }

            // Check if the item is a loading tile; if so, we do not want
            // a chat session to open on click.
            if (entry.first.isPlaceholder()) {
                Log.i(TAG, "Loading artifact clicked.");
                return;
            }

            // Open a chat screen for the artifact.
            Log.i(TAG, "Artifact clicked: " + entry.first.toString());
            Intent intent = new Intent(getActivity(), ChatActivity.class);
            intent.putExtra("ENABLE_CHAT", true);
            intent.putExtra("UUID", entry.first.getUUID());
            startActivity(intent);
        });

        // Create the beacon scanning pipeline and set the list
        // adapter to track the entries stream.
        createPipeline();

        listView.setAdapter(new NearbyAdapter(getActivity(), entriesStream));

        return rootView;
    }

}
