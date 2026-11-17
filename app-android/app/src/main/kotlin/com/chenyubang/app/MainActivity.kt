package com.chenyubang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.ChenYuBangTheme
import com.chenyubang.core.ui.tokens.Tokens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChenYuBangTheme {
                // 骨架占位：P02 推荐首页在下一阶段接入，路由表见规范 §5.1
                Placeholder()
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Tokens.Space.pageX.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        Text(text = "成语帮", fontSize = Tokens.Font.n20.sp)
        Text(text = "工程骨架已初始化，页面待接入。", fontSize = Tokens.Font.n13.sp)
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember") // IDE 预览入口，运行时不引用
private fun PlaceholderPreview() {
    ChenYuBangTheme { Placeholder() }
}
