package com.example.weather

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

enum class WeatherState {
    Sunny, ClearNight, PartlyCloudy, Cloudy, Rain, HeavyRain, Thunderstorm, Snow, Fog, Unknown
}

data class WeatherVisualConfig(
    val bgTop: Color,
    val bgBottom: Color,
    val hasClouds: Boolean,
    val hasSun: Boolean,
    val hasMoon: Boolean,
    val particleType: ParticleType = ParticleType.None,
    val hasLightning: Boolean = false
)

enum class ParticleType {
    None, Rain, Snow, HeavyRain, Fog
}

fun getWeatherState(code: Int, isDay: Boolean = true): WeatherState = when (code) {
    0 -> if (isDay) WeatherState.Sunny else WeatherState.ClearNight
    1, 2 -> if (isDay) WeatherState.PartlyCloudy else WeatherState.ClearNight
    3 -> WeatherState.Cloudy
    45, 48 -> WeatherState.Fog
    51, 53, 55, 56, 57 -> WeatherState.Rain
    61, 63, 65, 66, 67 -> WeatherState.Rain
    71, 73, 75, 77, 85, 86 -> WeatherState.Snow
    80, 81, 82 -> WeatherState.HeavyRain
    95, 96, 99 -> WeatherState.Thunderstorm
    else -> WeatherState.Unknown
}

fun getWeatherConfig(state: WeatherState): WeatherVisualConfig = when (state) {
    WeatherState.Sunny -> WeatherVisualConfig(Color(0xFF29B6F6), Color(0xFF81D4FA), false, true, false)
    WeatherState.ClearNight -> WeatherVisualConfig(Color(0xFF1A237E), Color(0xFF3949AB), false, false, true)
    WeatherState.PartlyCloudy -> WeatherVisualConfig(Color(0xFF4FC3F7), Color(0xFFB3E5FC), true, true, false)
    WeatherState.Cloudy -> WeatherVisualConfig(Color(0xFF78909C), Color(0xFFB0BEC5), true, false, false)
    WeatherState.Rain -> WeatherVisualConfig(Color(0xFF455A64), Color(0xFF78909C), true, false, false, ParticleType.Rain)
    WeatherState.HeavyRain -> WeatherVisualConfig(Color(0xFF263238), Color(0xFF546E7A), true, false, false, ParticleType.HeavyRain)
    WeatherState.Thunderstorm -> WeatherVisualConfig(Color(0xFF1E1E1E), Color(0xFF37474F), true, false, false, ParticleType.HeavyRain, true)
    WeatherState.Snow -> WeatherVisualConfig(Color(0xFF90A4AE), Color(0xFFCFD8DC), true, false, false, ParticleType.Snow)
    WeatherState.Fog -> WeatherVisualConfig(Color(0xFF9E9E9E), Color(0xFFE0E0E0), false, false, false, ParticleType.Fog)
    WeatherState.Unknown -> WeatherVisualConfig(Color(0xFF607D8B), Color(0xFF90A4AE), false, false, false)
}

class Particle(
    var x: Float,
    var y: Float,
    var speed: Float,
    var size: Float,
    var angle: Float = 0f
)

@Composable
fun WeatherIconRenderer(state: WeatherState, modifier: Modifier = Modifier) {
    val config = getWeatherConfig(state)
    val transition = rememberInfiniteTransition()
    
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val particles = remember(config.particleType) {
        val count = when (config.particleType) {
            ParticleType.Rain -> 50
            ParticleType.HeavyRain -> 120
            ParticleType.Snow -> 80
            ParticleType.Fog -> 10
            else -> 0
        }
        Array(count) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = Random.nextFloat() * 0.5f + 0.5f,
                size = Random.nextFloat() * 2f + 1f,
                angle = if (config.particleType == ParticleType.Fog) Random.nextFloat() * 360f else 0f
            )
        }
    }

    val lightningAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val bgBrush = remember(config.bgTop, config.bgBottom) {
        Brush.verticalGradient(colors = listOf(config.bgTop, config.bgBottom))
    }

    val lightningPath = remember { Path() }

    Canvas(modifier = modifier) {
        // Draw background
        drawRect(
            brush = bgBrush,
            size = size
        )

        // Draw Sun
        if (config.hasSun) {
            val sunRadius = size.minDimension * 0.25f
            val sunCenter = Offset(size.width * 0.5f, size.height * 0.4f)
            drawCircle(
                color = Color(0xFFFFCA28),
                radius = sunRadius,
                center = sunCenter,
                alpha = 0.9f
            )
            // Rays
            val numRays = 8
            for (i in 0 until numRays) {
                val angle = (i * 360f / numRays) + (time * 2f)
                val rad = Math.toRadians(angle.toDouble())
                val endX = sunCenter.x + (sunRadius * 1.5f) * cos(rad).toFloat()
                val endY = sunCenter.y + (sunRadius * 1.5f) * sin(rad).toFloat()
                drawLine(
                    color = Color(0xFFFFCA28).copy(alpha = 0.5f),
                    start = sunCenter,
                    end = Offset(endX, endY),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Draw Moon
        if (config.hasMoon) {
            val moonRadius = size.minDimension * 0.2f
            val moonCenter = Offset(size.width * 0.5f, size.height * 0.4f)
            drawCircle(
                color = Color(0xFFFFF59D),
                radius = moonRadius,
                center = moonCenter
            )
            // Moon crater
            drawCircle(
                color = Color(0xFF1A237E),
                radius = moonRadius * 0.8f,
                center = Offset(moonCenter.x + moonRadius * 0.4f, moonCenter.y - moonRadius * 0.2f)
            )
        }

        // Draw Clouds
        if (config.hasClouds) {
            val cloudOffset = (time * 5f) % size.width
            val cloudY = size.height * 0.3f
            drawCloud(Offset(cloudOffset, cloudY), size.minDimension * 0.3f, Color.White.copy(alpha = 0.8f))
            drawCloud(Offset(cloudOffset - size.width, cloudY), size.minDimension * 0.3f, Color.White.copy(alpha = 0.8f))
            
            val cloud2Offset = size.width - ((time * 3f) % size.width)
            val cloud2Y = size.height * 0.5f
            drawCloud(Offset(cloud2Offset, cloud2Y), size.minDimension * 0.2f, Color.White.copy(alpha = 0.6f))
            drawCloud(Offset(cloud2Offset + size.width, cloud2Y), size.minDimension * 0.2f, Color.White.copy(alpha = 0.6f))
        }

        // Draw Lightning
        if (config.hasLightning && lightningAlpha > 0.95f) {
            drawRect(Color.White.copy(alpha = 0.4f))
            lightningPath.reset()
            lightningPath.moveTo(size.width * 0.5f, size.height * 0.2f)
            lightningPath.lineTo(size.width * 0.4f, size.height * 0.6f)
            lightningPath.lineTo(size.width * 0.6f, size.height * 0.6f)
            lightningPath.lineTo(size.width * 0.45f, size.height * 0.9f)
            drawPath(lightningPath, Color.Yellow, style = Stroke(width = 8f))
        }

        // Draw Particles
        if (config.particleType != ParticleType.None) {
            particles.forEach { p ->
                val px = (p.x * size.width + (if(config.particleType == ParticleType.Snow) sin(time * 0.05f + p.y * 10f) * 20f else 0f)) % size.width
                val py = (p.y * size.height + (time * p.speed * 20f)) % size.height
                
                when (config.particleType) {
                    ParticleType.Rain, ParticleType.HeavyRain -> {
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(px, py),
                            end = Offset(px - 10f, py + 20f),
                            strokeWidth = p.size * 2f
                        )
                    }
                    ParticleType.Snow -> {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = p.size * 3f,
                            center = Offset(px, py)
                        )
                    }
                    ParticleType.Fog -> {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.1f),
                            radius = p.size * 40f,
                            center = Offset(px, py)
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

fun DrawScope.drawCloud(center: Offset, size: Float, color: Color) {
    drawCircle(color, size * 0.5f, center)
    drawCircle(color, size * 0.4f, Offset(center.x - size * 0.4f, center.y + size * 0.1f))
    drawCircle(color, size * 0.4f, Offset(center.x + size * 0.4f, center.y + size * 0.1f))
    drawRect(color, topLeft = Offset(center.x - size * 0.4f, center.y + size * 0.1f), size = Size(size * 0.8f, size * 0.4f))
}
