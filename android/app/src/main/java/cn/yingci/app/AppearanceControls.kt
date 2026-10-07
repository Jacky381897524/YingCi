package cn.yingci.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable internal fun ThemeCards(selected:Int,change:(Int)->Unit){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
        listOf("跟随系统","浅色","深色").forEachIndexed{i,label->
            Column(Modifier.weight(1f).liquidGlass(0,14f).then(if(i==selected)Modifier.border(2.dp,Color(0xFF2179FF),RoundedCornerShape(14.dp))else Modifier).clip(RoundedCornerShape(14.dp)).clickable{change(i)}.padding(vertical=13.dp,horizontal=3.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Box(Modifier.size(42.dp,64.dp).clip(RoundedCornerShape(6.dp)).background(if(i==0)Brush.horizontalGradient(0f to Color(0xFFF2F7FF),.499f to Color(0xFFF2F7FF),.5f to Color(0xFF222A36),1f to Color(0xFF222A36))else Brush.verticalGradient(listOf(if(i==1)Color.White else Color(0xFF171C24),if(i==1)Color(0xFFEDF2F9)else Color(0xFF303845)))).border(1.dp,Color(0xFF9CA9BC),RoundedCornerShape(6.dp)),contentAlignment=Alignment.Center){
                    if(i==0)Row{Icon(Icons.Rounded.LightMode,null,Modifier.size(18.dp),tint=Color(0xFF263345));Icon(Icons.Rounded.DarkMode,null,Modifier.size(18.dp),tint=Color.White)}
                    else Column(Modifier.padding(5.dp)){Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0x55889AB2),RoundedCornerShape(2.dp)));Spacer(Modifier.height(4.dp));Box(Modifier.fillMaxWidth().height(18.dp).background(Color(0x33889AB2),RoundedCornerShape(2.dp)))}
                }
                Spacer(Modifier.height(8.dp));AppText(label,fontSize=12.sp,maxLines=2,fontWeight=FontWeight.Medium)
                if(i==selected)Icon(Icons.Rounded.CheckCircle,"已选择",Modifier.padding(top=4.dp).size(16.dp),tint=Color(0xFF2179FF))else Spacer(Modifier.height(20.dp))
            }
        }
    }
}
