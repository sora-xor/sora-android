package jp.co.soramitsu.common.logger

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import com.orhanobut.logger.CsvFormatStrategy
import com.orhanobut.logger.DiskLogAdapter
import com.orhanobut.logger.DiskLogStrategy
import jp.co.soramitsu.common.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.IOException

class DiskLoggerAdapter(dir: String) : DiskLogAdapter(
    CsvFormatStrategy.newBuilder().tag("SORA")
        .logStrategy(DiskLogStrategy(DiskLogHandler(dir, "SoraAndroidLog", 1024 * 1024))).build()
) {

    override fun isLoggable(priority: Int, tag: String?): Boolean {
        return BuildConfig.BUILD_TYPE == "debug"
    }
}

class DiskLogHandler(
    looper: Looper,
    private val folder: String,
    private val fileName: String,
    private val maxFileSize: Int
) : Handler(looper) {

    companion object {
        private fun getDefaultLooper(): Looper {
            val ht = HandlerThread("AndroidFileLogger")
            ht.start()
            return ht.looper
        }
    }

    constructor(folder: String, fileName: String, maxFileSize: Int) : this(
        getDefaultLooper(),
        folder,
        fileName,
        maxFileSize
    )

    override fun handleMessage(msg: Message) {
        val content = msg.obj as String
        var fileWriter: FileWriter? = null
        val logFile: File = getLogFile(folder, fileName)
        try {
            fileWriter = FileWriter(logFile, true)
            writeLog(fileWriter, content)
            fileWriter.flush()
            fileWriter.close()
        } catch (e: IOException) {
            if (fileWriter != null) {
                try {
                    fileWriter.flush()
                    fileWriter.close()
                } catch (e1: IOException) {
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun writeLog(fileWriter: FileWriter, content: String) {
        fileWriter.append(content)
    }

    private fun getLogFile(folderName: String, fileName: String): File {
        val folder = File(folderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        var newFileCount = 0
        var newFile: File
        var existingFile: File? = null
        newFile = File(folder, String.format("%s_%s.csv", fileName, newFileCount))
        while (newFile.exists()) {
            existingFile = newFile
            newFileCount++
            newFile = File(folder, String.format("%s_%s.csv", fileName, newFileCount))
        }
        return if (existingFile != null) {
            if (existingFile.length() >= maxFileSize) {
                newFile
            } else existingFile
        } else newFile
    }
}
