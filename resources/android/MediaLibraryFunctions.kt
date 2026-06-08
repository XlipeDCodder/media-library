package com.musicplayer.plugins.media_library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import com.nativephp.mobile.utils.NativeActionCoordinator
import org.json.JSONObject

/**
 * Fragment invisível que abre o seletor de pastas do Android (SAF, ACTION_OPEN_DOCUMENT_TREE)
 * e devolve a URI da árvore como evento `native-event` ("folder:chosen") para o webview.
 * O launcher é registrado no inicializador do fragment (timing correto do ActivityResult).
 */
class MediaLibraryFolderPicker : Fragment() {
    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val act = activity as? FragmentActivity ?: return@registerForActivityResult
            if (uri == null) {
                NativeActionCoordinator.dispatchEvent(act, "folder:cancelled", "{}")
                return@registerForActivityResult
            }
            // Mantém o acesso à pasta entre reinícios do app.
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val name = DocumentFile.fromTreeUri(requireContext(), uri)?.name
                ?: uri.lastPathSegment ?: "Pasta"
            val payload = JSONObject().apply {
                put("uri", uri.toString())
                put("name", name)
            }
            NativeActionCoordinator.dispatchEvent(act, "folder:chosen", payload.toString())
        }

    fun launch() = picker.launch(null)

    companion object {
        fun install(activity: FragmentActivity): MediaLibraryFolderPicker =
            activity.supportFragmentManager.findFragmentByTag("MediaLibraryFolderPicker")
                as? MediaLibraryFolderPicker
                ?: MediaLibraryFolderPicker().also {
                    activity.supportFragmentManager.beginTransaction()
                        .add(it, "MediaLibraryFolderPicker")
                        .commitNow()
                }
    }
}

/** Permissão de leitura de áudio adequada à versão do Android. */
private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

object MediaLibraryFunctions {

    /** Verifica (síncrono) se a permissão de leitura de áudio está concedida. */
    class CheckPermission(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val granted = ContextCompat.checkSelfPermission(context, audioPermission()) ==
                PackageManager.PERMISSION_GRANTED
            return BridgeResponse.success(mapOf("granted" to granted))
        }
    }

    /** Dispara o diálogo de permissão (assíncrono); o resultado volta como evento de lifecycle. */
    class RequestPermission(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val perm = audioPermission()
            val granted = ContextCompat.checkSelfPermission(activity, perm) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                activity.runOnUiThread {
                    ActivityCompat.requestPermissions(activity, arrayOf(perm), 2001)
                }
            }
            return BridgeResponse.success(mapOf("granted" to granted, "requested" to !granted))
        }
    }

    /**
     * Consulta todas as faixas de áudio indexadas pelo MediaStore do Android.
     * Retorna metadados prontos (título, artista, álbum, gênero quando disponível,
     * duração, pasta), o URI de conteúdo (para tocar) e o caminho do arquivo.
     */
    class QueryAudio(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val tracks = ArrayList<Map<String, Any>>()

            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val hasBuckets = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

            val projection = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATA,
            )
            if (hasBuckets) projection.add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(
                collection, projection.toTypedArray(), selection, null, sortOrder
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val bucketCol = if (hasBuckets)
                    c.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME) else -1

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val data = c.getString(dataCol) ?: ""
                    val folder = when {
                        bucketCol >= 0 -> c.getString(bucketCol) ?: ""
                        data.isNotEmpty() -> data.substringBeforeLast('/', "").substringAfterLast('/')
                        else -> ""
                    }
                    val albumId = c.getLong(albumIdCol)
                    val albumArtUri = ContentUris.withAppendedId(
                        android.net.Uri.parse("content://media/external/audio/albumart"), albumId
                    ).toString()

                    tracks.add(
                        mapOf(
                            "id" to id.toString(),
                            "uri" to ContentUris.withAppendedId(collection, id).toString(),
                            "path" to data,
                            "title" to (c.getString(titleCol) ?: ""),
                            "artist" to (c.getString(artistCol) ?: ""),
                            "album" to (c.getString(albumCol) ?: ""),
                            "album_id" to albumId.toString(),
                            "artwork_uri" to albumArtUri,
                            "duration" to (c.getLong(durationCol) / 1000), // ms -> s
                            "year" to c.getInt(yearCol),
                            "track" to c.getInt(trackCol),
                            "size" to c.getLong(sizeCol),
                            "mime" to (c.getString(mimeCol) ?: ""),
                            "folder" to folder,
                        )
                    )
                }
            }

            return BridgeResponse.success(mapOf("tracks" to tracks, "count" to tracks.size))
        }
    }

    /** Abre o seletor de pastas (SAF). O resultado volta como evento `folder:chosen`. */
    class PickFolder(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            activity.runOnUiThread {
                MediaLibraryFolderPicker.install(activity).launch()
            }
            return BridgeResponse.success(mapOf("started" to true))
        }
    }

    /** Enumera o áudio dentro de uma pasta SAF (tree URI) e lê os metadados. */
    class ScanTree(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val uriStr = parameters["uri"] as? String
                ?: return BridgeResponse.success(mapOf("tracks" to emptyList<Any>(), "count" to 0))

            val tracks = ArrayList<Map<String, Any>>()
            val root = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            if (root != null) collect(root, tracks)

            return BridgeResponse.success(mapOf("tracks" to tracks, "count" to tracks.size))
        }

        private fun collect(dir: DocumentFile, out: ArrayList<Map<String, Any>>) {
            for (f in dir.listFiles()) {
                if (f.isDirectory) {
                    collect(f, out)
                    continue
                }
                val name = f.name ?: continue
                val mime = f.type ?: ""
                val isAudio = mime.startsWith("audio") ||
                    name.endsWith(".mp3", true) || name.endsWith(".flac", true) || name.endsWith(".m4a", true)
                if (!isAudio) continue

                out.add(readTrack(f, name, mime))
            }
        }

        private fun readTrack(f: DocumentFile, name: String, mime: String): Map<String, Any> {
            val r = MediaMetadataRetriever()
            var title = ""; var artist = ""; var album = ""; var duration = 0L
            try {
                r.setDataSource(context, f.uri)
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                duration = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000
            } catch (_: Exception) {
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
            return mapOf(
                "uri" to f.uri.toString(),
                "path" to f.uri.toString(),
                "title" to (if (title.isNotBlank()) title else name.substringBeforeLast('.')),
                "artist" to artist,
                "album" to album,
                "duration" to duration,
                "size" to f.length(),
                "mime" to mime,
            )
        }
    }

    /**
     * Extrai a capa EMBUTIDA de um arquivo de áudio (tag ID3/MP4) e devolve em base64.
     * Funciona tanto para content:// (SAF/MediaStore) quanto para caminhos de arquivo.
     * O MediaStore albumart nem sempre tem a capa, mas a tag embutida sim.
     */
    class GetArtwork(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val src = parameters["uri"] as? String
                ?: return BridgeResponse.success(mapOf("art" to ""))

            val r = MediaMetadataRetriever()
            return try {
                if (src.startsWith("content://")) {
                    r.setDataSource(context, Uri.parse(src))
                } else {
                    r.setDataSource(src)
                }
                val pic = r.embeddedPicture
                val b64 = if (pic != null)
                    android.util.Base64.encodeToString(pic, android.util.Base64.NO_WRAP)
                else ""
                BridgeResponse.success(mapOf("art" to b64))
            } catch (e: Exception) {
                BridgeResponse.success(mapOf("art" to ""))
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        }
    }

    /** Sanidade: confirma que o plugin está carregado e responde. */
    class GetStatus(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return BridgeResponse.success(
                mapOf("status" to "ready", "provider" to "MediaStore")
            )
        }
    }
}
