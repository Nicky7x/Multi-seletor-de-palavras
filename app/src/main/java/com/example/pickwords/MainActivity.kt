package com.example.pickwords

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        col.addView(TextView(this).apply {
            textSize = 16f
            text = "PickWords 2.0 — acessório do teclado\n\n" +
                "O teclado Xiaomi/Gboard continua sendo seu teclado normal. " +
                "Quando ele aparece em um campo editável, o PickWords " +
                "tenta mostrar uma barra compacta logo acima dele.\n\n" +
                "1. Abra uma conversa, nota ou outro campo de escrita.\n" +
                "2. Toque na palavra desejada para posicionar o cursor.\n" +
                "3. Pressione '+ Palavra' na barra acima do teclado.\n" +
                "4. Repita com outras palavras (sem arrastar alças).\n" +
                "5. Pressione 'Copiar' para copiar só as selecionadas.\n\n" +
                "Não há OCR, captura de tela nem contornos desenhados.\n\n" +
                "Limitação do Android: não é possível manter vários destaques " +
                "nativos independentes em um campo de outro aplicativo. " +
                "O PickWords acumula as seleções em sua própria lista.\n\n" +
                "O serviço lê apenas o campo de escrita quando você usa '+ Palavra'."
        })
        col.addView(Button(this).apply {
            text = "Permitir configurações restritas"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        })
        col.addView(Button(this).apply {
            text = "Abrir acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        setContentView(col)
    }
}
