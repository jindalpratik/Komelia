package io.github.snd_r.komelia.image

actual enum class SamplerType {
    VIPS_LANCZOS_DOWN_BICUBIC_UP,
    IMAGEIO_LANCZOS,
    SKIA_MITCHELL,
    SKIA_CATMULL_ROM,
    SKIA_NEAREST,
}