/*
 * FileHelper.kt
 * Implements the FileHelper class
 * A FileHelper provides helper methods for reading and writing files from and to device storage
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.y20k.escapepods.core.Collection
import java.io.*
import java.text.NumberFormat
import kotlin.coroutines.experimental.suspendCoroutine


/*
 * FileHelper class
 */
class FileHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(FileHelper::class.java)


    /* Return an InputStream for given Uri */
    fun getTextFileStream(context: Context, uri: Uri): InputStream {
        return context.contentResolver.openInputStream(uri)
    }


    /* Get file size for given Uri */
    fun getFileSize(context: Context, uri: Uri): Long {
        val cursor: Cursor = context.contentResolver.query(uri, null, null, null, null)
        val sizeIndex: Int = cursor.getColumnIndex(OpenableColumns.SIZE)
        cursor.moveToFirst()
        return cursor.getLong(sizeIndex)
    }


    /* Get file name for given Uri */
    fun getFileName(context: Context, uri: Uri): String {
        val cursor: Cursor = context.contentResolver.query(uri, null, null, null, null)
        val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        return cursor.getString(nameIndex)
    }


    /* Get MIME type for given file */
    fun getFileType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri)
    }


    /* Save podcast collections as JSON text file */
    fun saveCollectionAsync(context: Context, collection: Collection) {

        // convert to JSON
        var json: String = ""
        val gson: Gson = getCustomGson()
        json = gson.toJson(collection)

        // save JSON as text file
        launch(UI) {
            async(CommonPool) {
                writeTextFile(context, json, Keys.COLLECTION_FOLDER, Keys.COLLECTION_FILE)
            }.await()
        }
    }


    /* Reads collection from storage using GSON - async via coroutine */
    suspend fun readCollection(context: Context): Collection {
        return suspendCoroutine {cont ->
            // get JSON from text file
            val json: String = readTextFile(context, Keys.COLLECTION_FOLDER, Keys.COLLECTION_FILE)
            if (json.isEmpty()) {
                // return an empty collection
                cont.resume(Collection())
            } else {
                // convert JSON and return as COLLECTION
                cont.resume(getCustomGson().fromJson(json, Collection::class.java))
            }
        }
    }


    /*  Creates a Gson object */
    private fun getCustomGson(): Gson {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        gsonBuilder.excludeFieldsWithoutExposeAnnotation()
        return gsonBuilder.create()
    }



    /* Create nomedia file in given folder to prevent media scanning */
    fun createNomediaFile(folder: File) {
        val noMediaOutStream: FileOutputStream = FileOutputStream(getNoMediaFile(folder))
        noMediaOutStream.write(0)
    }


    /* Delete nomedia file in given folder */
    fun deleteNoMediaFile(folder: File) {
        getNoMediaFile(folder).delete()
    }


    /* Converts byte value into a human readable format */
    // Source: https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
    fun getReadableByteCount(bytes: Long, si: Boolean): String {

        // check if Decimal prefix symbol (SI) or Binary prefix symbol (IEC) requested
        val unit: Long = if (si) 1000L else 1024L

        // just return bytes if file size is smaller than requested unit
        if (bytes < unit) return bytes.toString() + " B"

        // calculate exp
        val exp: Int = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()

        // determine prefix symbol
        val prefix: String = ((if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i")

        // calculate result and set number format
        val result: Double = bytes / Math.pow(unit.toDouble(), exp.toDouble())
        val numberFormat = NumberFormat.getNumberInstance()
        numberFormat.maximumFractionDigits = 1

        return numberFormat.format(result) + " " + prefix + "B"
    }


    /* Reads InputStream from file uri and returns it as String */
    private fun readTextFile(context: Context, folder: String, fileName: String): String {
        // todo read https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
        // https://developer.android.com/training/secure-file-sharing/retrieve-info

        // check if file exists
        val file: File = File(context.getExternalFilesDir(folder), fileName)
        if (!file.exists()) {
            return ""
        }
        // read until last line reached
        val stream: InputStream = file.inputStream()
        val reader: BufferedReader = BufferedReader(InputStreamReader(stream))
        val builder: StringBuilder = StringBuilder()
        reader.forEachLine {
            builder.append(it)
            builder.append("\n") }
        stream.close()
        return builder.toString()
    }


    /* Writes given text to file on storage - async via coroutine */
    suspend private fun writeTextFile(context: Context, text: String, folder: String, fileName: String) {
        return suspendCoroutine {
            File(context.getExternalFilesDir(folder), fileName).writeText(text)
        }
    }


    /* Returns a nomedia file object */
    private fun getNoMediaFile(folder: File): File {
        return File(folder, ".nomedia")
    }
}