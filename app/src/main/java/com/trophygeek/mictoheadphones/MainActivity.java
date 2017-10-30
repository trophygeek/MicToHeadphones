/**
 * Quick demo of recording sound off the mic and echoing it back out the headphone.
 *
 * You really don't want to use the external speaker for this or you'll get a feedback squelch
 *
 */
package com.trophygeek.mictoheadphones;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.BassBoost;
import android.media.audiofx.NoiseSuppressor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "mictoheadphones";
    Context m_context;
    private RecordTask recordTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        m_context = this;

        ToggleButton toggle = (ToggleButton) findViewById(R.id.toggleButton);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // is the noise_reduction checkbox set
                CheckBox noise_checkbox = (CheckBox) findViewById(R.id.noiseReductionCheckbox);
                CheckBox bass_checkbox = (CheckBox) findViewById(R.id.bassBoostCheckbox);

                if (isChecked) {
                    // The toggle is enabled
                    // start the passthrough.
                    recordTask = new RecordTask();
                    recordTask.EnableNoiseSupprssor(noise_checkbox.isChecked());
                    recordTask.EnableBassBoost(bass_checkbox.isChecked());

                    // in theory you could set sample rate, mono vs stereo, etc here.

                    // we don't dynamically allow the noise and bass sound filters to be
                    // toggle on the fly (for this demo), but we need to communicate that via
                    // the UI.
                    noise_checkbox.setEnabled(false);
                    bass_checkbox.setEnabled(false);

                    boolean success = recordTask.Start();
                    if (!success) {
                        // failed, just do something lame for now.
                        Toast toast = Toast.makeText(m_context,
                                "Must grant permissions to mic from App Info", Toast.LENGTH_LONG);
                        toast.show();

                        ToggleButton toggleBtn = (ToggleButton) findViewById(R.id.toggleButton);
                        toggleBtn.setChecked(false);
                        toggleBtn.setEnabled(false);
                    }
                } else {
                    // The toggle is disabled
                    // stop the passthrough
                    if (recordTask != null) {
                        recordTask.Stop();
                        recordTask = null;

                        // disabled when we started playing, reset.
                        noise_checkbox.setEnabled(true);
                        bass_checkbox.setEnabled(true);
                    }
                }
            }
        });
    }


    private static class RecordTask extends AsyncTask<String, Integer, String> {
        AudioRecord arec;
        AudioTrack atrack;

        int buffersize;
        byte[] buffer;

        boolean is_recording = false;

        boolean enable_suppressor = true;
        NoiseSuppressor noise_suppressor;

        boolean enable_bass = true;
        BassBoost bass_boost;

        // todo: make these options
        private static final int RECORDER_SAMPLERATE = 8000;
        private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
        private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

        private static final int PLAYBACK_CHANNELS = AudioFormat.CHANNEL_OUT_MONO;

        boolean Start() {
            if (this.InitAudioPassthrough()) {   // on main thread
                execute();
                return true;
            }
            return false;
        }

        /** will cause DoAudioPassthrough() to stop looping and return **/
        void Stop() {
            is_recording = false;
        }

        void EnableNoiseSupprssor(boolean newstate) {
            enable_suppressor = newstate;
        }

        void EnableBassBoost(boolean newstate) {
            enable_bass = newstate;
        }

        @Override
        protected String doInBackground(String... params) {
            this.DoAudioPassthrough();
            return "this string is passed to postExecute";
        }


        /** happens on main thread **/
        boolean InitAudioPassthrough() {
            is_recording = true;

            buffersize = AudioRecord.getMinBufferSize(
                    RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING);


            arec = new AudioRecord( MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, buffersize);

            int state = arec.getState();
            if (state < 1) {
                is_recording = false;
                // todo: dynamically request permissions. For now we just fail reasonably.
                return false;
            }

            atrack = new AudioTrack( AudioManager.STREAM_MUSIC,
                    RECORDER_SAMPLERATE, PLAYBACK_CHANNELS, RECORDER_AUDIO_ENCODING,
                    buffersize, AudioTrack.MODE_STREAM);


            if (enable_suppressor) {
                noise_suppressor = NoiseSuppressor.create(arec.getAudioSessionId());
            }

            if (enable_bass) {
                bass_boost = new BassBoost(1, atrack.getAudioSessionId());
            }

            buffer = new byte[buffersize*8];
            return true;
        }

        /** this happens on a thread **/
        void DoAudioPassthrough() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // start the recording and playback
            arec.startRecording();
            atrack.play();

            // tight loop play recorded buffer directly
            while (is_recording) {
                int count = arec.read(buffer, 0, buffersize);
                atrack.write(buffer, 0, count);
            }

            arec.stop();
            atrack.stop();


            if (noise_suppressor != null) {
                noise_suppressor.release();
                noise_suppressor = null;
            }

            atrack.release();
            arec.release();

            atrack = null;
            arec = null;
            buffer = null;
        }

    }

}
