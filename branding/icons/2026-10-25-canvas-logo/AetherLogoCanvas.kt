import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// User-supplied design experiment. Kept as supplied, with imports added.
@Composable
fun AetherLogoCanvas(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color(0xFF0B0D12)) // 深色底色
    ) {
        val centerOffset = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.width * 0.4f

        // 1. 绘制中间的“彩色球体”
        val coreGradient = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFFFDE8E0), // 中心淡粉
                0.3f to Color(0xFFF3C785), // 中间金黄
                0.6f to Color(0xFF91B879), // 淡绿
                1.0f to Color.Transparent  // 边缘融合进背景
            ),
            center = centerOffset,
            radius = maxRadius * 0.5f
        )

        drawCircle(
            brush = coreGradient,
            radius = maxRadius * 0.5f,
            center = centerOffset
        )

        // 2. 绘制外围“黑白/纯白”密集线圈 (用数学公式生成路径)
        val lineCount = 120 // 线条密度
        val strokeWidth = 1.dp.toPx() // 细线条

        for (i in 0 until lineCount) {
            val progress = i / lineCount.toFloat()

            // 计算双曲环形的数学形变
            val path = Path().apply {
                val r = maxRadius * (0.3f + 0.7f * progress)
                // 用椭圆或双贝塞尔曲线勾勒类似磁场的形状
                addOval(
                    Rect(
                        center = centerOffset.copy(y = centerOffset.y - (10 * progress)),
                        radius = r
                    )
                )
            }

            // 外围仅使用纯白线，靠 Alpha (透明度) 表现虚实
            drawPath(
                path = path,
                color = Color.White.copy(alpha = (1f - progress) * 0.4f),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}
