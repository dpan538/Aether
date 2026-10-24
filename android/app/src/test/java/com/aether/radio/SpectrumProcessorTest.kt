package com.aether.radio

import androidx.media3.common.audio.AudioProcessor
import org.junit.Test
import java.nio.ByteBuffer

class SpectrumProcessorTest {
    @Test fun emptyPipelineBuffersAreSafeBeforeAndAfterDrain() {
        val processor=SpectrumProcessor({false},{})
        processor.queueInput(AudioProcessor.EMPTY_BUFFER)
        processor.queueInput(ByteBuffer.allocateDirect(0))
        processor.getOutput()
        processor.queueInput(AudioProcessor.EMPTY_BUFFER)
    }
}
