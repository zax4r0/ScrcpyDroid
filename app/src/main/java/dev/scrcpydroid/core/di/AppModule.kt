package dev.scrcpydroid.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.scrcpydroid.core.decoder.VideoDecoder
import dev.scrcpydroid.core.scrcpy.ScrcpyOptions
import dev.scrcpydroid.core.scrcpy.VideoCodec
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDefaultScrcpyOptions(): ScrcpyOptions {
        return ScrcpyOptions(
            maxSize = 1920,
            videoBitRate = 8_000_000,
            maxFps = 60,
            videoCodec = VideoCodec.H264,
            audioEnabled = false,
            controlEnabled = true,
            stayAwake = true,
            showTouches = false,
            powerOffOnClose = false
        )
    }

    @Provides
    @Singleton
    fun provideVideoDecoder(): VideoDecoder {
        return VideoDecoder()
    }
}
