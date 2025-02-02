package com.blockstream.common.lightning

import breez_sdk.LogEntry
import breez_sdk.LogStream
import breez_sdk.setLogStream
import com.blockstream.common.data.AppConfig
import com.blockstream.common.data.AppInfo
import com.blockstream.common.di.ApplicationScope
import com.blockstream.common.fcm.FcmCommon
import com.blockstream.common.gdk.Gdk
import com.blockstream.common.gdk.data.LoginData
import com.blockstream.common.platformFileSystem
import com.blockstream.common.utils.Loggable
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesIgnore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okio.Path
import okio.Path.Companion.toPath

class LightningManager constructor(
    private val greenlightKeys: GreenlightKeys,
    private val appInfo: AppInfo,
    private val appConfig: AppConfig,
    private val scope: ApplicationScope,
    private val gdk: Gdk,
    val firebase: FcmCommon,
) {
    private val bridges = mutableMapOf<String, LightningBridge>()
    private val references = mutableMapOf<LightningBridge, Int>()

    private val mutex = Mutex()

    val logs = StringBuilder()

    init {
        if(appConfig.lightningFeatureEnabled) {
            setLogStream(object : LogStream {
                override fun log(l: LogEntry) {
                    if (l.level == "DEBUG") {
                        logs.append("${Clock.System.now()} - ${l.line}\n")
                        if (logs.length > 2_000_000) {
                            logger.d { "Clear Lightning Logs" }
                            logs.deleteRange(0, 1_000_000)
                        }
                    }
                }
            })
        }
    }

    @NativeCoroutinesIgnore
    suspend fun getLightningBridge(loginData: LoginData): LightningBridge {
        val file = "${gdk.dataDir}/breezSdk/${loginData.xpubHashId}/0"

        return mutex.withLock {
            (bridges.getOrPut(file) {
                logger.i { "Creating a new LightningBridge $file" }

                LightningBridge(
                    appInfo = appInfo,
                    workingDir = file,
                    greenlightKeys = greenlightKeys,
                    firebase = firebase,
                    lightningManager = this
                )
            }).also { bridge ->
                references[bridge] = (references[bridge] ?: 0) + 1
            }
        }
    }

    suspend fun createLogs(): Path {
        val fileSystem = platformFileSystem()
        val logDir = "${appConfig.cacheDir}/logs/".toPath()

        // Delete old logs
        fileSystem.deleteRecursively(logDir)

        if (!platformFileSystem().exists(logDir)) {
            fileSystem.createDirectories(logDir, mustCreate = true)
        }

        return "${logDir}/greenlight_logs_${Clock.System.now()}.txt".toPath().also {
            withContext(context = Dispatchers.IO) {
                fileSystem.write(it) {
                    this.writeUtf8(logs.toString())
                }
            }
        }
    }

    fun release(lightningBridge: LightningBridge) {
        scope.launch {
            mutex.withLock {
                logger.i { "Release LightningBridge" }

                val reference = ((references[lightningBridge] ?: 1) - 1).also {
                    references[lightningBridge] = it
                }

                if (reference < 1) {
                    logger.i { "Stopping LightningBridge" }
                    // Remove
                    bridges.remove(lightningBridge.workingDir)
                    // Stop
                    lightningBridge.stop()
                }
            }
        }
    }

    companion object : Loggable()
}