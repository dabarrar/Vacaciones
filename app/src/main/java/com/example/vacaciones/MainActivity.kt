package com.example.vacaciones

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.time.LocalDateTime
import org.osmdroid.views.overlay.Marker
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import androidx.camera.core.ImageCaptureException
import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog



enum class Pantalla {
    FORM,
    FOTO
}
class CameraAppViewModel : ViewModel() {
    val pantalla = mutableStateOf(Pantalla.FORM)
    // callbacks
    var onPermisoCamaraOk : () -> Unit = {}
    var onPermisoUbicacionOk: () -> Unit = {}
    // lanzador permisos
    var lanzadorPermisos: ActivityResultLauncher<Array<String>>? = null
    fun cambiarPantallaFoto(){ pantalla.value = Pantalla.FOTO }
    fun cambiarPantallaForm(){ pantalla.value = Pantalla.FORM }
}
class FormRecepcionViewModel : ViewModel() {
    val receptor = mutableStateOf("")
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    val fotosRecepcion = mutableStateOf<MutableList<Uri>>(mutableListOf())
}

// Permisos y configuracion de la camara
class MainActivity : ComponentActivity() {
    val cameraAppVm:CameraAppViewModel by viewModels()
    lateinit var cameraController: LifecycleCameraController
    val lanzadorPermisos =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?:
                false) or (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?:
                false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso ubicacion granted")
                    cameraAppVm.onPermisoUbicacionOk()
                }
                (it[android.Manifest.permission.CAMERA] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso camara granted")
                    cameraAppVm.onPermisoCamaraOk()
                }
                else -> {
                }
            }
        }
    //Configuracion de la camara permisos -- camara trasera
    private fun setupCamara() {
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector =
            CameraSelector.DEFAULT_BACK_CAMERA
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraAppVm.lanzadorPermisos = lanzadorPermisos
        setupCamara()
        setContent {
            AppUI(cameraController)
        }
    }
}

//Nombre de la Foto
fun generarNombreSegunFechaHastaSegundo():String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0, 14)

fun crearArchivoImagenPrivado(contexto: Context): File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombreSegunFechaHastaSegundo()}.jpg"
)

fun uri2imageBitmap(uri:Uri, contexto:Context) =
    BitmapFactory.decodeStream(
        contexto.contentResolver.openInputStream(uri)
    ).asImageBitmap()



// Captura de la imagen y guardado
fun tomarFotografia(cameraController: CameraController, contexto: Context, imagenGuardadaOk: (uri: Uri) -> Unit) {
    val outputFileOptions = ImageCapture.OutputFileOptions.Builder(crearArchivoImagenPrivado(contexto)).build()
    cameraController.takePicture(outputFileOptions, ContextCompat.getMainExecutor(contexto), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            outputFileResults.savedUri?.also {
                Log.v("tomarFotografia()::onImageSaved", "Foto guardada en ${it.toString()}")
                imagenGuardadaOk(it)
            }
        }

        override fun onError(exception: ImageCaptureException) {
            Log.e("tomarFotografia()", "Error: ${exception.message}")
        }
    })
}



//Ubicacion del dispositivo y permisos
class SinPermisoException(mensaje:String) : Exception(mensaje)

fun getUbicacion(contexto: Context, onUbicacionOk:(location: Location) ->
Unit):Unit {
    try {
        val servicio =
            LocationServices.getFusedLocationProviderClient(contexto)
        val tarea =
            servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOk(it)
        }
    } catch (e:SecurityException) {
        throw SinPermisoException(e.message?:"Permiso de ubicación denegado")
    }
}


//Comportamiento de la pantalla
@Composable
fun AppUI(cameraController: CameraController) {
    val contexto = LocalContext.current
    val formRecepcionVm:FormRecepcionViewModel = viewModel()
    val cameraAppViewModel:CameraAppViewModel = viewModel()
    when(cameraAppViewModel.pantalla.value) {
        Pantalla.FORM -> {
            PantallaFormUI(
                formRecepcionVm,
                tomarFotoOnClick = {
                    cameraAppViewModel.cambiarPantallaFoto()
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(Manifest.permission.CAMERA))
                },
                actualizarUbicacionOnClick = {
                    cameraAppViewModel.onPermisoUbicacionOk = {
                        getUbicacion(contexto) {
                            formRecepcionVm.latitud.value = it.latitude
                            formRecepcionVm.longitud.value = it.longitude
                        }
                    }
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            )
        }
        Pantalla.FOTO -> {
            PantallaFotoUI(formRecepcionVm, cameraAppViewModel,
                cameraController)
        }
        else -> {
            Log.v("AppUI()", "when else, no debería entrar aquí")
        }
    }
}


// Composable para la captura de imagen y ubicacion
@Composable
fun PantallaFormUI(
    formRecepcionVm: FormRecepcionViewModel,
    tomarFotoOnClick: () -> Unit = {},
    actualizarUbicacionOnClick: () -> Unit = {}
) {
    val contexto = LocalContext.current
    // Agrega un estado para el índice de la imagen seleccionada
    var imagenSeleccionadaIndex by remember { mutableStateOf(-1) }
    // Agrega un estado para controlar si se muestra la imagen en pantalla completa
    var mostrarImagen by remember { mutableStateOf(false) }
    // Agrega un estado para mantener la URI de la imagen seleccionada
    var imagenSeleccionadaUri by remember { mutableStateOf<Uri?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Lugar visitado",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            modifier = Modifier.padding(10.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .background(Color.Gray)
        ) {
            BasicTextField(
                value = formRecepcionVm.receptor.value,
                onValueChange = { newValue ->
                    formRecepcionVm.receptor.value = newValue
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }

        Text("Fotos de mis vacaciones:")
        Button(onClick = {
            tomarFotoOnClick()
        }) {
            Text("Tomar Fotografía")
        }

        val columns = 3 // Ajusta el número de columnas según tus necesidades

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            content = {
                itemsIndexed(formRecepcionVm.fotosRecepcion.value) { index, uri ->
                    Box(
                        modifier = Modifier
                            .size(200.dp, 100.dp)
                            .padding(8.dp) // Agrega un espacio alrededor de las fotos
                            .clickable {
                                // Al hacer clic en una foto, establece el índice de la imagen seleccionada
                                imagenSeleccionadaIndex = index
                                // Muestra la imagen en pantalla completa
                                imagenSeleccionadaUri = uri
                                mostrarImagen = true
                            }
                    ) {
                        Image(
                            painter = BitmapPainter(uri2imageBitmap(uri, contexto)),
                            contentDescription = "Imagen de mis vacaciones ${formRecepcionVm.receptor.value} - Foto $index"
                        )
                    }
                }
            }
        )

        // Mostrar la imagen seleccionada en pantalla completa si hay una selección
        if (mostrarImagen && imagenSeleccionadaUri != null) {
            Dialog(
                onDismissRequest = {
                    // Cierra la pantalla de diálogo al hacer clic fuera de la imagen
                    mostrarImagen = false
                    imagenSeleccionadaUri = null
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(Color.Black)
                ) {
                    Image(
                        painter = BitmapPainter(uri2imageBitmap(imagenSeleccionadaUri!!, contexto)),
                        contentDescription = "Imagen en pantalla completa",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Text("La ubicación es: lat: ${formRecepcionVm.latitud.value} y long: ${formRecepcionVm.longitud.value}")
        Button(onClick = {
            actualizarUbicacionOnClick()
        }) {
            Text("Selecciona tu Ubicación")
        }
        Spacer(Modifier.height(100.dp))
        MapaOsmUI(formRecepcionVm.latitud.value, formRecepcionVm.longitud.value)
    }
}


//1 modific pantalla camara
@Composable
fun PantallaFotoUI(formRecepcionVm: FormRecepcionViewModel, appViewModel: CameraAppViewModel, cameraController: CameraController) {
    val contexto = LocalContext.current

    AndroidView(
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    Button(onClick = {
        tomarFotografia(
            cameraController,
            contexto
        ) {
            formRecepcionVm.fotosRecepcion.value.add(it)
            appViewModel.cambiarPantallaForm()
        }
    }) {
        Text("Tomar foto")
    }
}



// Mapa y ubicacion
@Composable
fun MapaOsmUI(latitud:Double, longitud:Double) {
    val contexto = LocalContext.current
    AndroidView(
        factory = {
            MapView(it).also {
                it.setTileSource(TileSourceFactory.MAPNIK)
                Configuration.getInstance().userAgentValue = contexto.packageName
            }
        }, update = {
            it.overlays.removeIf { true }
            it.invalidate()
            it.controller.setZoom(18.0)
            val geoPoint = GeoPoint(latitud, longitud)
            it.controller.animateTo(geoPoint)
            val marcador = Marker(it)
            marcador.position = geoPoint
            marcador.setAnchor(Marker.ANCHOR_CENTER,
                Marker.ANCHOR_CENTER)
            it.overlays.add(marcador)
        }
    )
}

