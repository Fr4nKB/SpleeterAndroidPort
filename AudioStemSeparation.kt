package com.frank.glyphify.glyph

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class AudioStemSeparation(private val context: Context, private val modelName: String,
                          private val filePath: String) {
    private lateinit var interpreter: Interpreter

    @Throws(IOException::class)
    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("$modelName.tflite")
        val fileChannel = FileInputStream(fileDescriptor.fileDescriptor).channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength)
    }

    private fun loadFromFile(context: Context, filePath: String):
            Pair<IntArray, Array<FloatArray>>? {
        // create a temporary file to store the decoded audio data
        val tempFile = File.createTempFile("decoded", ".pcm", context.cacheDir)

        val session = FFmpegKit.execute("-i $filePath -f f32le -acodec pcm_f32le " +
                "-ac 2 -ar 44100 ${tempFile.absolutePath}")

        if (ReturnCode.isSuccess(session.returnCode)) {
            // load the audio data from the temporary file
            val dataList = ArrayList<FloatArray>()
            tempFile.inputStream().buffered().use { inputStream ->
                val buffer = ByteArray(4 * 2) // read two samples at a time
                while (inputStream.read(buffer) != -1) {
                    val leftChannel = ByteBuffer.wrap(buffer, 0, 4).order(ByteOrder.LITTLE_ENDIAN).float
                    val rightChannel = ByteBuffer.wrap(buffer, 4, 4).order(ByteOrder.LITTLE_ENDIAN).float
                    dataList.add(floatArrayOf(leftChannel, rightChannel))
                }
            }

            // delete the temporary file
            tempFile.delete()

            val nbFrames = dataList.size
            val nbChannels = 2

            val dims = intArrayOf(nbFrames, nbChannels)
            val data = dataList.toTypedArray()
            return Pair(dims, data)
        }
        else {
            // the command failed, handle the error
            Log.e("loadFromFile", "FFmpeg command failed with return code ${session.returnCode}")
            return null
        }
    }

    private fun saveToFile(context: Context, data: Array<FloatArray>, filePath: String) {
        // create a temporary file to store the audio data
        val tempFile = File.createTempFile("encoded", ".pcm", context.cacheDir)

        tempFile.outputStream().buffered().use { outputStream ->
            val buffer = ByteBuffer.allocate(4 * 2) // write two samples at a time
            for (frame in data) {
                val leftChannel = frame[0]
                val rightChannel = frame[1]
                buffer.order(ByteOrder.LITTLE_ENDIAN).putFloat(leftChannel)
                buffer.order(ByteOrder.LITTLE_ENDIAN).putFloat(rightChannel)
                outputStream.write(buffer.array())
                buffer.clear()
            }
        }

        val session = FFmpegKit.execute("-f f32le -acodec pcm_f32le -ac 2 -ar 44100 -i ${tempFile.absolutePath} $filePath")
        if (ReturnCode.isSuccess(session.returnCode)) {
            Log.d("saveToFile", "FFmpeg command success")
        }
        else {
            Log.e("saveToFile", "FFmpeg command failed with return code ${session.returnCode}")
        }

        tempFile.delete()
    }

    fun init() {
        try {
            val model = loadModelFile(modelName)
            interpreter = Interpreter(model)
            interpreter.allocateTensors()
        }
        catch (e: IOException) {
            Log.e("DEBUG", "Error: couldn't load tflite model.", e)
        }
    }

    fun separate() {
        if(interpreter.inputTensorCount != 1) {
            Log.d("DEBUG", "Too many input tensors")
            return
        }

        val outputTensorCount = interpreter.outputTensorCount
        if(outputTensorCount == 0) {
            Log.d("DEBUG", "No output tensors")
            return
        }

        val waveform = loadFromFile(context, filePath)

        if(waveform == null) {
            Log.d("DEBUG", "Empty waveform")
            return
        }

        val dims = waveform.first
        val data = waveform.second

        // resize input tensor
        interpreter.resizeInput(0, waveform.first)
        interpreter.allocateTensors()

        // prepare a map to hold the output data
        val outputMap = HashMap<Int, Any>()
        for (i in 0 until interpreter.outputTensorCount) {
            outputMap[i] = Array(dims[0]) { FloatArray(dims[1]) }
        }

        // Run the model
        interpreter.runForMultipleInputsOutputs(arrayOf(data), outputMap)

        // transform output data to waveform and save it a wav file
        for((index, data) in outputMap) {
            // 0 -> bass, 1 -> drums, 2 -> accompaniment, 3 -> vocals
            saveToFile(
                context,
                data as Array<FloatArray>,
                "${context.filesDir.path}/$index.wav")
        }
        outputMap.clear()

    }

    fun unInit() {
        interpreter.close()
        System.gc()
    }
}