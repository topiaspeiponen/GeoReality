package com.example.georeality

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.fragment_cache_creation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.lang.Exception

/**
 * @author Topias Peiponen, Roope Vaarama
 * @since 24.09.2020
 */
class CacheCreationFragment : Fragment() {

    lateinit var recFile: File
    private var Recording = false
    private var switchIsOn = false
    private var location : String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        askPerm()
        val view = inflater.inflate(R.layout.fragment_cache_creation, container, false)
        setupLayout(view)

        return view
    }

    private fun setupLayout(view : View) {
        val spinner: Spinner = view.findViewById(R.id.spinner)
        val spinnerModels : Spinner = view.findViewById(R.id.spinnerModels)
        val typeSwitch: SwitchCompat = view.findViewById(R.id.typeSwitch)
        val saveButton: Button = view.findViewById(R.id.saveButton)
        val recordButton: Button = view.findViewById(R.id.recordButton)
        var recording: Boolean = false
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
        location = sharedPref?.getString("locationData", "defaultLocation")

        typeSwitch.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                typeSwitch.text = getString(R.string.audio)
                typeTitle.text = getString(R.string.record)
                spinner.visibility = View.GONE
                spinnerModels.visibility = View.GONE
                arTextInput.visibility = View.GONE
                recordButton.visibility = View.VISIBLE
                timerView.visibility = View.VISIBLE
                switchIsOn = true
            } else {
                typeSwitch.text = getString(R.string.ar)
                typeTitle.text = getString(R.string.type)
                spinner.visibility = View.VISIBLE
                recordButton.visibility = View.GONE
                timerView.visibility = View.GONE
                switchIsOn = false
                if (spinner.selectedItem.toString() == getString(R.string.ar_type_2d)) {
                    arTextInput.visibility = View.VISIBLE
                } else if (spinner.selectedItem.toString() == getString(R.string.ar_type_3d)) {
                    spinnerModels.visibility = View.VISIBLE
                }
            }
        }


        saveButton.setOnClickListener {

            val cacheType = typeSwitch.text.toString()
            val title = titleTextInput.text
            val spinnerType = spinner.selectedItem.toString()
            Log.d("save", "Save button was clicked cache type: ${cacheType}, title: ${title}, spinnertype: ${spinnerType}, location: ${location}")
            submitCache()
        }

        recordButton.setOnClickListener {
            if(!recording){
                audioRecorder()

                typeTitle.text = getString(R.string.recording)
                recordButton.text = getString(R.string.stop)
                recording = true
            } else {
                Recording = false
                typeTitle.text = getString(R.string.record)
                recordButton.text = getString(R.string.record)
                recording = false
            }

        }

        //ArrayAdapter for type spinner
        ArrayAdapter.createFromResource(
            requireActivity().applicationContext,
            R.array.type_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            //Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_layout)
            //Apply the adapter to the spinner
            spinner.adapter = adapter
        }

        //ArrayAdapter for models spinner
        ArrayAdapter.createFromResource(
            requireActivity().applicationContext,
            R.array.model_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            //Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_layout)
            //Apply the adapter to the spinner
            spinnerModels.adapter = adapter
        }

        val spinnerItems = resources.getStringArray(R.array.type_array)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                //2D text is selected
                if (pos == 0){
                    Toast.makeText(requireActivity(), "Selected item" + " " + spinnerItems[pos], Toast.LENGTH_SHORT).show()
                    arTextInput.visibility = View.VISIBLE
                    spinnerModels.visibility = View.GONE
                }
                //3D models is selected
                if (pos == 1){
                    Toast.makeText(requireActivity(), "Selected item" + " " + spinnerItems[pos], Toast.LENGTH_SHORT).show()
                    arTextInput.visibility = View.GONE
                    spinnerModels.visibility = View.VISIBLE
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }

        spinnerModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                //Model1 is selected
                if (p2 == 0) {

                }

                //Model2 is selected
                if (p2 == 1) {

                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {

            }

        }
    }

    /**
     * Checks if fields in CacheCreationFragment layout are filled correctly
     * @return Boolean, true if filled correctly, false if not
     */
    private fun formIsValid() : Boolean {
        //Check if title and spinner selection is empty
        if (titleTextInput.text.toString() == "") {
            Toast.makeText(requireActivity(), "Title cannot be empty!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (spinner.selectedItem == null) {
            Toast.makeText(requireActivity(), "AR mode must be selected!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (location == null) {
            Toast.makeText(requireActivity(), "Could not get current location!", Toast.LENGTH_SHORT).show()
            return false
        }

        //Check if type specific fields are empty
        if (switchIsOn) {
            /*if (recFile == null) {
                Toast.makeText(requireActivity(), "Audio must be recorded!", Toast.LENGTH_SHORT).show()
                return false
            } else {
                return true
            }*/
        } else if (!switchIsOn) {
            return if (arTextInput.text.toString() == "" && spinner.selectedItem.toString() == getString(R.string.ar_type_2d)) {
                Toast.makeText(requireActivity(), "AR text cannot be empty!", Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }
        return true
    }
    private fun submitCache() {
        Log.d("SubmitCache", "Clicked! $switchIsOn")
        if (formIsValid()) {
            val title = titleTextInput.text.toString()
            val user = FirebaseAuth.getInstance().currentUser!!.email
            val locationArray = location!!.split(",")
            val latitude = locationArray[0]
            val longitude = locationArray[1]
            val displayText = titleTextInput.text.toString()

            //AR is selected
            if (!switchIsOn) {
                val type = spinner.selectedItem.toString()

                //2D text is selected
                if (type == getString(R.string.ar_type_2d)) {
                    val newARMarker = ARMarker(
                        user,
                        latitude.toDouble(),
                        longitude.toDouble(),
                        title,
                        type,
                        displayText,
                        null
                    )
                    Log.d("2D ARMarker", newARMarker.toString())
                    Database.dbViewModel!!.addNewARMarker(newARMarker)
                }

                //3D model is selected
                if (type == getString(R.string.ar_type_3d)) {
                    val model = spinnerModels.selectedItem.toString()
                    val newARMarker = ARMarker(
                        user,
                        latitude.toDouble(),
                        longitude.toDouble(),
                        title,
                        type,
                        null,
                        model
                    )
                    Log.d("3D ARMarker", newARMarker.toString())
                    Database.dbViewModel!!.addNewARMarker(newARMarker)
                }

            }
            //Audio is selected
            else if (switchIsOn) {
                val newAudioMarker = AudioMarker(user, latitude.toDouble(), longitude.toDouble(), title, "placeholderID")
                Database.dbViewModel!!.addNewAudioMarker(newAudioMarker)
            }
        }
    }

    private fun audioRecorder() {
        val file = "record.raw"
        /*val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)*/
        try {
            //recFile = File("$storageDir/$file")
        } catch (e: Exception) {
            Log.d("error", "error creating file: ${e.message}")
        }

        try {
            val outputStream = FileOutputStream(recFile)
            val bufferedOutputStream = BufferedOutputStream(outputStream)
            val dataOutputStream = DataOutputStream(bufferedOutputStream)

            val minBufferSize = AudioRecord.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val aFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()

            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(aFormat)
                .setBufferSizeInBytes(minBufferSize)
                .build()

            val audioData = ByteArray(minBufferSize)

            GlobalScope.launch(Dispatchers.IO) {
                Recording = true
                recorder.startRecording()
                while (Recording) {
                    val numofBytes = recorder.read(audioData, 0, minBufferSize)
                    if (numofBytes > 0) {
                        dataOutputStream.write(audioData)

                    }
                }
                Log.d("audioplay", "Recording stopped")
                recorder.stop()
                dataOutputStream.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun askPerm() {
        if (ContextCompat.checkSelfPermission(
                requireActivity().application.applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1
            )
        }
    }
}

class SpinnerActivity: Activity(), AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
        val selection = parent?.getItemAtPosition(pos)
    }

    override fun onNothingSelected(p0: AdapterView<*>?) {
    }
}

