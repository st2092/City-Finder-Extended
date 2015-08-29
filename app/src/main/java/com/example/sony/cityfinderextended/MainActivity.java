package com.example.sony.cityfinderextended;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;


public class MainActivity extends ActionBarActivity
        implements OnMapReadyCallback, GoogleMap.OnMapLoadedCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener {

    private GoogleMap map;
    private LatLng my_location;
    private ArrayList<String> locations;                // list of the locations that are marked on the map
    private ArrayAdapter<String> locations_adapter;     // adapter to put ArrayList to Spinner dropdown menu
    private Polyline drawn_polyline;
    private PolylineOptions drawn_polyline_options;
    private ArrayList<LatLng> coordinates_list;


    /*
     * This method gets call at the start to set up the application. Specifically,
     * this method starts the map when it is ready.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawn_polyline = null;
        drawn_polyline_options = new PolylineOptions();
        coordinates_list = new ArrayList<LatLng>();


        MapFragment map_fragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        map_fragment.getMapAsync(this);         // calls onMapReady when loaded
    }

    /*
     * This method gets call when the map fragment is loaded.
     * The map is loaded, but not laid out yet.
     */
    @Override
    public void
    onMapReady(GoogleMap map)
    {
        this.map = map;
        map.setOnMapLoadedCallback(this);       // calls onMapLoaded when layout is done
    }

    /*
     * This method gets call when the map is ready.
     * Runs the start up tasks such as reading a list
     * of cities to add markers and determine user's location
     * if possible.
     */
    @Override
    public void
    onMapLoaded()
    {
        locations = readCities();

        // set up the Spinner's layout
        Spinner spinner = (Spinner) findViewById(R.id.location_spinner);
        locations_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, locations);
        locations_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(locations_adapter);

        // set up event listener for clicks on the spinner's dropdown menu
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void
            onItemSelected(AdapterView<?> parent, View view, int index, long id) {
                // an item was selected
                handle_spinner_click(index);
            }

            @Override
            public void
            onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });

        map.setOnMarkerClickListener(this);
        map.setOnMapClickListener(this);

        // determines user's current location, if possible
        my_location = getMyLocation();
        if (my_location == null)
        {
            Toast.makeText(this, "Unable to access your location. Consider enabling Location services in your device's settings.", Toast.LENGTH_LONG).show();
        }
        else
        {
            // add my location
            map.addMarker(new MarkerOptions().position(my_location).title("Me"));
        }
    }

    /*
     * This function reads a list of cities from a text file and draws a marker on the map
     * for each city.
     *
     * Note: The structure of an entry of a city is:
     *          City, State
     *          latitude
     *          longitude
     *
     * latitude: North/South relative to the equator (north pole = +90; south pole = -90)
     * longitude: East/West relative to prime meridian (west = 0 -> -180; east = 0 -> 180)
     */
    private ArrayList<String>
    readCities()
    {
        ArrayList<String> list_of_locations = new ArrayList<String>();
        Scanner scan = new Scanner(getResources().openRawResource(R.raw.cities));
        while(scan.hasNextLine())
        {
            String city_name = scan.nextLine();
            if (city_name == null)
            {
                break;
            }
            double city_latitude = Double.parseDouble(scan.nextLine());
            double city_longitude = Double.parseDouble(scan.nextLine());
            map.addMarker(new MarkerOptions()
                            .position(new LatLng(city_latitude, city_longitude))
                            .title(city_name)
            );
            list_of_locations.add(city_name);
            coordinates_list.add(new LatLng(city_latitude, city_longitude));
            //Log.d("test", "Added marker for " + city_name );
        }
        return list_of_locations;
    }

    /*
     * Returns the user's location as a LatLng object. Returns null
     * if location cannot be found (e.g. location service is off).
     * We will try to find out the user location in 3 ways:
     *      GPS,
     *      Cell or wifi network,
     *      and "passive" mode
     *
     * Passive mode: passively receive location updates when other applications or services request
     *              them without actually requesting the location yourself
     */
    private LatLng
    getMyLocation()
    {
        // try to obtain user location in 3 ways: GPS, cell or wifi network, and "passive" mode
        LocationManager location_manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location location = location_manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null)
        {
            // gps method failed or not available; fallback to network
            location = location_manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (location == null)
        {
            // gps and network failed or not available; fallback to "passive" location
            location = location_manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }
        if (location == null)
        {
            // cannot get the user's location
            return null;
        }
        else
        {
            double my_latitude = location.getLatitude();
            double my_longitude = location.getLongitude();
            return new LatLng(my_latitude, my_longitude);
        }
    }

    /*
     * This method gets call when user clicks on any city map markers.
     * Adds a line from one city to next chosen.
     * User must have at least 2 cities chosen before a line is drawn.
     */
    @Override
    public boolean
    onMarkerClick(Marker marker)
    {
        if (my_location != null)
        {
            if (drawn_polyline != null)
            {
                drawn_polyline.remove();    // clear the current drawn path
            }

            LatLng marker_LatLng = marker.getPosition();

            // update polyline
            drawn_polyline_options = drawn_polyline_options.add(marker_LatLng);
            drawn_polyline = map.addPolyline(drawn_polyline_options);   // redraw path with new city

            Log.d("test", "onMarkerClick called ... adding " + marker_LatLng + " to list.");
            return true;
        }
        else
        {
            return false;
        }
    }

    /*
     * This method gets call when user clicks on the map.
     * Adds a marker onto the map with it's title as the
     * latitude and longitude.
     */
    @Override
    public void
    onMapClick(LatLng clicked_point)
    {
        double clicked_latitude = clicked_point.latitude;
        double clicked_longitude = clicked_point.longitude;
        //String name = clicked_latitude + ", " + clicked_longitude;
        String city_name = getCityName(clicked_point);
        map.addMarker(new MarkerOptions()
                .position(clicked_point)
                .title(city_name)
        );
        Log.d("test", "onMapClick called ... adding new marker" + clicked_point + ", " + city_name);
        locations.add(city_name);
        coordinates_list.add(clicked_point);
        locations_adapter.notifyDataSetChanged();
    }

    /*
     * This function returns the city and state corresponding to the LatLng coordinates
     * passed in.
     *
     * Example of a possible return value: "San Jose, CA"
     *
     * If the city or state cannot be found with the LatLng coordinates it will be "null."
     * Example of a possible return value in this case: "Null, CA" or "Null, Null"
     */
    private String
    getCityName(LatLng city_LatLng)
    {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        List<Address> addresses = null;
        try {
             addresses = geocoder.getFromLocation(city_LatLng.latitude, city_LatLng.longitude, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // return the name of city if found; otherwise have the LatLng be the name
        if ( addresses != null && addresses.size() > 0)
        {
            return addresses.get(0).getLocality() + ", " + addresses.get(0).getAdminArea();
        }
        else
        {
            return city_LatLng.toString();
        }
    }

    /*
     * This function gets call when the user selects an city via the spinner.
     * Zooms the map towards the chosen city with it being at the center of the map.
     */
    private void
    handle_spinner_click(int index)
    {
        LatLng chosen_city_LatLng = coordinates_list.get(index);

        // zoom into the selected city and animate camera
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(chosen_city_LatLng, 4));   // zoom = 4
        map.animateCamera(CameraUpdateFactory.zoomIn());

        // Zoom out to zoom level 3, animating with duration of 2 seconds
        map.animateCamera(CameraUpdateFactory.zoomTo(3), 2000, null);

        //Log.d("test", "handle_spinner_click got called ... index: " + index + ", List size: " + coordinates_list.size());
    }

    /*
     * This function gets call when the user clicks the "clear" button.
     * Removes all drawn paths on the map.
     */
    public void
    clear_drawn_paths(View view)
    {
        drawn_polyline.remove();
        drawn_polyline_options = new PolylineOptions();     // let garbage collector handle deallocate memory
    }
}