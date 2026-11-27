package com.senseix21.muminsday.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import java.time.Duration
import java.time.Instant

private const val SAMPLE_RATE = 22_050
private const val TONE_HZ = 440.0
private val FADE: Duration = Duration.ofMillis(100)

/** The wake lock outlives the audio by this much, so a slow stop cannot leave the phone asleep mid-sound. */
private val WAKE_LOCK_MARGIN: Duration = Duration.ofSeconds(10)

/**
 * Plays a [Sound] as a `mediaPlayback` foreground service, and is the only thing in the app that makes audio.
 *
 * One playback at a time (INV-S5): a second start while playing changes nothing, so a replan, a second alarm at the
 * same instant or a double delivery cannot make two Adhans. Stops when the sound ends, on the notification's Stop
 * action, or when the app is opened. Everything here runs on the main thread.
 */
class PlaybackService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                // Must come first: the platform kills a service that was started as foreground and does not say so.
                enterForeground()
                val sound = Sound.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SOUND) }
                val use = AudioUse.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_USE) } ?: AudioUse.Alarm
                when {
                    sound == null -> stopSelf()
                    playing -> Log.i(LOG_TAG, "playback: already playing, second start ignored")
                    else -> play(sound, use)
                }
            }
            ACTION_STOP -> stopSelf()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notifications.playing(this, stop)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifications.PLAYBACK_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notifications.PLAYBACK_NOTIFICATION_ID, notification)
        }
    }

    private fun play(sound: Sound, use: AudioUse) {
        playing = true
        changed?.invoke()
        Log.i(LOG_TAG, "playback: start $sound")

        wakeLock = checkNotNull(getSystemService(PowerManager::class.java))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mumins:playback")
            .also { it.acquire((sound.length + WAKE_LOCK_MARGIN).toMillis()) }

        // G26: USAGE_ALARM plays through silent mode; USAGE_NOTIFICATION obeys it.
        val attributes = AudioAttributes.Builder()
            .setUsage(if (use == AudioUse.Alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .build()
        focus = request
        checkNotNull(getSystemService(AudioManager::class.java)).requestAudioFocus(request)

        val samples = sineWave(SAMPLE_RATE, sound.length, TONE_HZ, FADE)
        val audio = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audio.write(samples, 0, samples.size)
        audio.play()
        track = audio

        handler.postDelayed({ stopSelf() }, (sound.length + Duration.ofMillis(300)).toMillis())
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        val wasPlaying = playing
        track?.release()
        track = null
        focus?.let { checkNotNull(getSystemService(AudioManager::class.java)).abandonAudioFocusRequest(it) }
        focus = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        playing = false
        changed?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (wasPlaying) {
            Log.i(LOG_TAG, "playback: stopped")
            // Firing -> Armed: a playback that ends plans again (the state machine in the architecture spec).
            replan(applicationContext, Instant.now())
        }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PLAY = "com.senseix21.muminsday.PLAY"
        private const val ACTION_STOP = "com.senseix21.muminsday.STOP"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_USE = "use"

        /** Only ever touched on the main thread, where the service runs. */
        private var playing = false

        /** True while the Adhan or tone is sounding. Main thread only. */
        fun isPlaying(): Boolean = playing

        /** The open screen's hook (G31): called on the main thread when playback starts or ends. At most one screen. */
        var changed: (() -> Unit)? = null

        fun playIntent(context: Context, sound: Sound, use: AudioUse): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY)
                .putExtra(EXTRA_SOUND, sound.name).putExtra(EXTRA_USE, use.name)

        /** Opening the app silences the Adhan (G6). A no-op when nothing is playing. */
        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
