package com.f1sound.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.f1sound.app.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Simulateur de son F1 — une seule Activity.
 *
 * Chaîne :
 *   gravité + accélération linéaire (capteurs fusionnés d'Android)
 *     -> accélération dans l'axe de la voiture (quelle que soit l'inclinaison du support)
 *       -> régime virtuel (0..12 000 tr/min)
 *         -> 3 boucles tirées de vrais enregistrements de F1, mélangées par volume
 *            et calées sur une même hauteur via la vitesse de lecture
 *
 * Téléphone attendu : en portrait sur un support, écran vers le conducteur
 * (ou posé à plat, haut du téléphone vers l'avant).
 */
class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityMainBinding

    // ---- Capteurs ---------------------------------------------------------
    private lateinit var sensorManager: SensorManager
    private var linearSensor: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var accelSensor: Sensor? = null
    /** true si le téléphone fournit gravité + accélération linéaire (gyroscope). */
    private var useFused: Boolean = false
    private var locationManager: LocationManager? = null

    // ---- Audio ------------------------------------------------------------
    private lateinit var soundPool: SoundPool
    private var idleSoundId = 0
    private var midSoundId = 0
    private var highSoundId = 0
    private var blipSoundId = 0
    private var loadedCount = 0
    private var idleStream = 0
    private var midStream = 0
    private var highStream = 0
    private var blipStream = 0
    private var loopsStarted = false

    // ---- État -------------------------------------------------------------
    private var running = false
    private var volume = 0.8f
    private val gravityVec = FloatArray(3)
    private var gravityReady = false
    private val linearVec = FloatArray(3)
    /** Accélération longitudinale lissée, en G (positive = accélère). */
    private var longG = 0f
    /** Petit décalage du capteur, estimé lentement puis retiré. */
    private var bias = 0f
    private var rpm = 0f
    private var gpsSpeedKmh = -1f
    private var gpsEnabled = false
    private var orientationOk = true
    private var lastBlipAtMs = 0L
    private var blipCount = 0
    private var lastUiMs = 0L

    // ---- Réglages (valeurs réalistes pour une voiture) --------------------
    private val rpmMax = 12_000f
    private val rpmIdle = 4_000f
    /** Au-dessus : on considère qu'on accélère. */
    private val throttleG = 0.04f
    /** Accélération qui donne le régime maximum (voiture normale ≈ 0,3 G à fond). */
    private val fullThrottleG = 0.30f
    /** En dessous : pied levé, le régime retombe. */
    private val coastG = -0.08f
    /** En dessous : freinage appuyé, son de freinage. */
    private val brakeG = -0.30f
    /** Intervalle entre deux rétrogradages simulés pendant un freinage. */
    private val blipIntervalMs = 450L

    /**
     * Hauteur naturelle (Hz) de chaque boucle, mesurée sur les enregistrements.
     * Le régime virtuel est converti en hauteur cible, puis chaque boucle est
     * accélérée/ralentie pour l'atteindre.
     */
    private val idleHz = 133f
    private val midHz = 290f
    private val highHz = 609f

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted && gpsEnabled) startGpsUpdates()
        if (!granted) {
            binding.gpsSwitch.isChecked = false
            binding.statusText.text = getString(R.string.permission_rationale)
        }
    }

    // ======================================================================
    // Cycle de vie
    // ======================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupSensors()
        setupAudio()
        setupUi()
    }

    override fun onResume() {
        super.onResume()
        if (running) {
            startListening()
            if (::soundPool.isInitialized) soundPool.autoResume()
            if (gpsEnabled) startGpsUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        // Sans ça, le son restait bloqué au dernier régime quand l'app passait en arrière-plan.
        stopListening()
        stopGpsUpdates()
        if (::soundPool.isInitialized) soundPool.autoPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGpsUpdates()
        if (::soundPool.isInitialized) soundPool.release()
    }

    // ======================================================================
    // Initialisation
    // ======================================================================

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        useFused = linearSensor != null && gravitySensor != null
        if (!useFused && accelSensor == null) {
            binding.statusText.text = getString(R.string.sensor_unavailable)
            binding.startButton.isEnabled = false
        }
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun setupAudio() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(attrs)
            .build()
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) loadedCount++ else Log.w(TAG, "Échec de chargement d'un son, status=$status")
            if (loadedCount == 4 && running) {
                startLoops()
                binding.statusText.text = getString(R.string.status_listening)
            }
        }
        idleSoundId = soundPool.load(this, R.raw.idle, 1)
        midSoundId = soundPool.load(this, R.raw.mid, 1)
        highSoundId = soundPool.load(this, R.raw.high, 1)
        blipSoundId = soundPool.load(this, R.raw.blip, 1)
    }

    private fun setupUi() {
        binding.startButton.setOnClickListener {
            if (running) stopEverything() else startEverything()
        }
        binding.gpsSwitch.setOnCheckedChangeListener { _, isChecked ->
            gpsEnabled = isChecked
            if (isChecked) {
                requestLocationPermissionIfNeeded()
            } else {
                stopGpsUpdates()
                gpsSpeedKmh = -1f
                binding.speedText.text = getString(R.string.speed_label, getString(R.string.speed_unknown))
            }
        }
        binding.volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volume = progress / 100f
                applyAudio()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    // ======================================================================
    // Marche / arrêt
    // ======================================================================

    private fun startEverything() {
        running = true
        rpm = rpmIdle
        binding.startButton.text = getString(R.string.stop)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (gpsEnabled && hasLocationPermission()) startGpsUpdates()
        startListening()
        if (loadedCount == 4) {
            startLoops()
            binding.statusText.text = getString(R.string.status_listening)
        } else {
            binding.statusText.text = getString(R.string.status_loading)
        }
    }

    private fun stopEverything() {
        running = false
        binding.startButton.text = getString(R.string.start)
        binding.statusText.text = getString(R.string.status_idle)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopListening()
        stopGpsUpdates()
        stopLoops()
        rpm = 0f
        longG = 0f
        binding.rpmText.text = getString(R.string.rpm_label, 0)
        binding.rpmBar.progress = 0
        binding.gforceText.text = getString(R.string.gforce_label, 0f)
        binding.brakingText.visibility = View.GONE
    }

    private fun startListening() {
        if (useFused) {
            gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            linearSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        } else {
            accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    private fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    // ======================================================================
    // Capteurs
    // ======================================================================

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                System.arraycopy(event.values, 0, gravityVec, 0, 3)
                gravityReady = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                System.arraycopy(event.values, 0, linearVec, 0, 3)
                if (gravityReady) processMotion()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (useFused) return
                // Téléphone sans gyroscope : on estime la gravité par une moyenne lente (~5 s).
                if (!gravityReady) {
                    System.arraycopy(event.values, 0, gravityVec, 0, 3)
                    gravityReady = true
                }
                for (i in 0..2) {
                    gravityVec[i] += (event.values[i] - gravityVec[i]) * 0.004f
                    linearVec[i] = event.values[i] - gravityVec[i]
                }
                processMotion()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Trouve "l'avant" de la voiture : c'est l'horizontale vers laquelle pointent
     * le dos du téléphone (support vertical) et/ou son haut (téléphone à plat).
     */
    private fun processMotion() {
        val gx = gravityVec[0]
        val gy = gravityVec[1]
        val gz = gravityVec[2]
        val gNorm = sqrt(gx * gx + gy * gy + gz * gz)
        if (gNorm < 1f) return
        val ux = gx / gNorm
        val uy = gy / gNorm
        val uz = gz / gNorm

        // En paysage, le haut du téléphone pointe sur le côté : on ne garde que le dos.
        val yWeight = if (abs(ux) > 0.7f) 0f else 1f
        var vx = 0f
        var vy = yWeight
        var vz = -1f
        val d = vx * ux + vy * uy + vz * uz
        vx -= d * ux
        vy -= d * uy
        vz -= d * uz
        val hNorm = sqrt(vx * vx + vy * vy + vz * vz)
        orientationOk = hNorm >= 0.3f
        if (!orientationOk) return

        val forward = (linearVec[0] * vx + linearVec[1] * vy + linearVec[2] * vz) /
            hNorm / SensorManager.GRAVITY_EARTH
        bias += (forward - bias) * 0.001f
        longG = longG * 0.85f + (forward - bias) * 0.15f

        updateRpm(longG)
        applyAudio()

        val now = SystemClock.uptimeMillis()
        if (now - lastUiMs > 66) {
            lastUiMs = now
            updateUi()
        }
    }

    // ======================================================================
    // Régime + audio
    // ======================================================================

    private fun updateRpm(g: Float) {
        // Avec le GPS, le régime "de croisière" monte avec la vitesse.
        val baseRpm = if (gpsSpeedKmh >= 0f) {
            rpmIdle + (gpsSpeedKmh / 130f).coerceIn(0f, 1f) * 3_000f
        } else {
            rpmIdle
        }
        val target = when {
            g > throttleG -> {
                val t = ((g - throttleG) / (fullThrottleG - throttleG)).coerceIn(0f, 1f)
                baseRpm + t * (rpmMax - baseRpm)
            }
            g < coastG -> baseRpm * 0.6f
            else -> baseRpm
        }
        if (g < brakeG) maybeFireBrake(g)
        val k = if (target > rpm) 0.08f else 0.05f
        rpm = (rpm + (target - rpm) * k).coerceIn(0f, rpmMax)
    }

    /** Lance les 3 boucles une seule fois ; ensuite on ne fait que régler volume et hauteur. */
    private fun startLoops() {
        if (loopsStarted || loadedCount < 4) return
        idleStream = soundPool.play(idleSoundId, volume, volume, 1, -1, 1f)
        midStream = soundPool.play(midSoundId, 0f, 0f, 1, -1, 1f)
        highStream = soundPool.play(highSoundId, 0f, 0f, 1, -1, 1f)
        loopsStarted = true
        applyAudio()
    }

    private fun stopLoops() {
        for (s in intArrayOf(idleStream, midStream, highStream, blipStream)) {
            if (s != 0) soundPool.stop(s)
        }
        idleStream = 0
        midStream = 0
        highStream = 0
        blipStream = 0
        loopsStarted = false
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Régime -> hauteur cible (Hz), sur une échelle "musicale" (exponentielle). */
    private fun targetHz(r: Float): Float {
        val x = (r - 3_200f) / 8_800f
        return idleHz * Math.pow(1_000.0 / idleHz, x.toDouble()).toFloat()
    }

    /** Fondu entre ralenti / milieu / haut régime, chaque boucle calée sur la même hauteur. */
    private fun applyAudio() {
        if (!running || !loopsStarted) return
        val f = targetHz(rpm)
        val wIdle = 1f - smoothstep(200f, 250f, f)
        val wHigh = smoothstep(420f, 520f, f)
        val wMid = (1f - wIdle - wHigh).coerceAtLeast(0f)
        // Plus fort dans les tours, comme un vrai moteur.
        val gain = volume * (0.5f + 0.5f * (rpm / rpmMax).coerceIn(0f, 1f))
        soundPool.setVolume(idleStream, wIdle * gain, wIdle * gain)
        soundPool.setVolume(midStream, wMid * gain, wMid * gain)
        soundPool.setVolume(highStream, wHigh * gain, wHigh * gain)
        soundPool.setRate(idleStream, (f / idleHz).coerceIn(0.5f, 2f))
        soundPool.setRate(midStream, (f / midHz).coerceIn(0.5f, 2f))
        soundPool.setRate(highStream, (f / highHz).coerceIn(0.5f, 2f))
    }

    /** Freinage appuyé : série de rétrogradages ("blips"), de plus en plus graves. */
    private fun maybeFireBrake(g: Float) {
        if (!loopsStarted) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBlipAtMs < blipIntervalMs) return
        if (now - lastBlipAtMs > 1_000L) blipCount = 0
        lastBlipAtMs = now
        val rate = (1.5f - blipCount * 0.18f).coerceIn(0.75f, 2f)
        blipCount++
        if (blipStream != 0) soundPool.stop(blipStream)
        val v = volume * (0.6f + (abs(g) - 0.3f).coerceIn(0f, 0.4f))
        blipStream = soundPool.play(blipSoundId, v, v, 2, 0, rate)
    }

    private fun updateUi() {
        val rpmInt = rpm.toInt().coerceIn(0, rpmMax.toInt())
        binding.rpmText.text = getString(R.string.rpm_label, rpmInt)
        binding.rpmBar.progress = rpmInt
        binding.gforceText.text = getString(R.string.gforce_label, longG)
        binding.brakingText.visibility = if (longG < brakeG) View.VISIBLE else View.GONE
        if (running && loopsStarted) {
            binding.statusText.text = getString(
                if (orientationOk) R.string.status_listening else R.string.status_orientation
            )
        }
    }

    // ======================================================================
    // GPS (facultatif)
    // ======================================================================

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            startGpsUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @Suppress("MissingPermission")
    private fun startGpsUpdates() {
        val lm = locationManager ?: return
        if (!hasLocationPermission()) return
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                binding.statusText.text = getString(R.string.gps_off)
                return
            }
        }
        try {
            lm.requestLocationUpdates(provider, 1_000L, 0f, this)
        } catch (e: SecurityException) {
            Log.w(TAG, "GPS refusé", e)
        }
    }

    private fun stopGpsUpdates() {
        locationManager?.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        if (!location.hasSpeed()) return
        gpsSpeedKmh = location.speed * 3.6f
        binding.speedText.text = getString(R.string.speed_label, "%.0f".format(gpsSpeedKmh))
    }

    @Deprecated("Deprecated in API 29+")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    companion object {
        private const val TAG = "F1SoundApp"
    }
}
