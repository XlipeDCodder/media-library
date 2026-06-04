package com.musicplayer.plugins.media_library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse

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

    /** Sanidade: confirma que o plugin está carregado e responde. */
    class GetStatus(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return BridgeResponse.success(
                mapOf("status" to "ready", "provider" to "MediaStore")
            )
        }
    }
}
