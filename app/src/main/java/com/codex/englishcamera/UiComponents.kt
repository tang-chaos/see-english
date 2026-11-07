package com.codex.englishcamera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Muted=Color(0xFF53695E)
internal val Hairline=Color(0xFFDDE6DE)
internal val SoftGold=Color(0xFFFFF1D1)
internal enum class Glyph { CAMERA, HISTORY, BOOK, SPARK, SPEAKER, PHOTO, EXPAND }

/** Small native vectors: no downloaded icon/font package or bitmap assets. */
@Composable
internal fun AppIcon(glyph: Glyph,modifier: Modifier=Modifier,tint: Color=Green) {
    Canvas(modifier.size(22.dp)) {
        val u=size.minDimension/24f
        val stroke=Stroke(1.7f*u,cap=StrokeCap.Round,join=StrokeJoin.Round)
        fun line(x: Float,y: Float,x2: Float,y2: Float)=drawLine(tint,Offset(x*u,y*u),Offset(x2*u,y2*u),1.7f*u,StrokeCap.Round)
        when(glyph) {
            Glyph.CAMERA->{
                drawRoundRect(tint,Offset(2*u,6*u),Size(20*u,15*u),CornerRadius(3*u),style=stroke)
                drawCircle(tint,4*u,Offset(12*u,13.5f*u),style=stroke)
                line(7f,6f,9f,3f);line(9f,3f,15f,3f);line(15f,3f,17f,6f)
            }
            Glyph.PHOTO->{
                drawRoundRect(tint,Offset(3*u,3*u),Size(18*u,18*u),CornerRadius(3*u),style=stroke)
                drawCircle(tint,1.4f*u,Offset(8*u,8*u))
                val p=Path().apply { moveTo(4*u,18*u);lineTo(10*u,12*u);lineTo(14*u,16*u);lineTo(17*u,13*u);lineTo(21*u,17*u) }
                drawPath(p,tint,style=stroke)
            }
            Glyph.HISTORY->{
                drawArc(tint,-130f,300f,false,Offset(3*u,3*u),Size(18*u,18*u),style=stroke)
                line(3.5f,3f,3.5f,8f);line(3.5f,8f,8.5f,8f)
                line(12f,7f,12f,12f);line(12f,12f,16f,14f)
            }
            Glyph.BOOK->{
                val p=Path().apply {
                    moveTo(12*u,5*u);cubicTo(8*u,3*u,5*u,3*u,2*u,5*u);lineTo(2*u,20*u)
                    cubicTo(5*u,18*u,8*u,18*u,12*u,20*u);cubicTo(16*u,18*u,19*u,18*u,22*u,20*u)
                    lineTo(22*u,5*u);cubicTo(19*u,3*u,16*u,3*u,12*u,5*u);lineTo(12*u,20*u)
                };drawPath(p,tint,style=stroke)
            }
            Glyph.SPARK->{
                val p=Path().apply {
                    moveTo(12*u,2*u);lineTo(14.6f*u,9.4f*u);lineTo(22*u,12*u);lineTo(14.6f*u,14.6f*u)
                    lineTo(12*u,22*u);lineTo(9.4f*u,14.6f*u);lineTo(2*u,12*u);lineTo(9.4f*u,9.4f*u);close()
                };drawPath(p,tint,style=stroke)
            }
            Glyph.SPEAKER->{
                val p=Path().apply { moveTo(3*u,9*u);lineTo(7*u,9*u);lineTo(12*u,5*u);lineTo(12*u,19*u);lineTo(7*u,15*u);lineTo(3*u,15*u);close() }
                drawPath(p,tint,style=stroke)
                drawArc(tint,-55f,110f,false,Offset(10*u,6*u),Size(11*u,12*u),style=stroke)
            }
            Glyph.EXPAND->{
                line(3f,9f,3f,3f);line(3f,3f,9f,3f);line(15f,3f,21f,3f);line(21f,3f,21f,9f)
                line(21f,15f,21f,21f);line(21f,21f,15f,21f);line(9f,21f,3f,21f);line(3f,21f,3f,15f)
            }
        }
    }
}

@Composable
internal fun IconBadge(glyph: Glyph,background: Color=Mint,tint: Color=Green,size: Int=44) {
    Surface(color=background,shape=RoundedCornerShape((size*.34f).dp),modifier=Modifier.size(size.dp)) {
        Box(contentAlignment=Alignment.Center) { AppIcon(glyph,Modifier.size((size*.48f).dp),tint) }
    }
}

@Composable
internal fun StudyBanner(title: String,subtitle: String,glyph: Glyph,action: (@Composable ()->Unit)?=null) {
    Surface(color=Green,shape=RoundedCornerShape(26.dp),modifier=Modifier.fillMaxWidth().padding(vertical=14.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconBadge(glyph,Color.White.copy(alpha=.13f),Gold,42)
                Spacer(Modifier.width(12.dp))
                Text(title,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(subtitle,color=Color(0xFFD4E6DB),fontSize=13.sp,modifier=Modifier.weight(1f))
                action?.invoke()
            }
        }
    }
}

@Composable
internal fun WordCardShell(onClick: (() ->Unit)?=null,content: @Composable ColumnScope.()->Unit) {
    val inner: @Composable ()->Unit={ Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(5.dp),content=content) }
    if(onClick!=null)Surface(onClick=onClick,color=Color.White,shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Hairline),
        shadowElevation=1.dp,modifier=Modifier.fillMaxWidth(),content=inner)
    else Surface(color=Color.White,shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Hairline),
        shadowElevation=1.dp,modifier=Modifier.fillMaxWidth(),content=inner)
}

@Composable
internal fun SpeakButton(text: String="朗读",onClick: ()->Unit) {
    TextButton(onClick=onClick,shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.textButtonColors(containerColor=Mint,contentColor=Green),
        contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),modifier=Modifier.heightIn(min=48.dp)) {
        AppIcon(Glyph.SPEAKER,modifier=Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(text,fontSize=13.sp)
    }
}
